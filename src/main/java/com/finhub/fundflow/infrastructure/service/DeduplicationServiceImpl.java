package com.finhub.fundflow.infrastructure.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.service.DeduplicationService;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.github.benmanes.caffeine.cache.Cache;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DeduplicationService 实现：三重防重（external_id → fingerprint → 缓存预检）。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>第一重 external_id——外部系统业务主键，最强约束，命中即跳过。</li>
 *   <li>第二重 fingerprint——external_id 缺失时靠结构化哈希兜底。</li>
 *   <li>第三重缓存仅加速，非权威——DB 唯一约束是最后防线。</li>
 *   <li>批次内先去重再查库：{@code seenExternalIds} / {@code seenFingerprints}
 *       避免同批次内重复记录多次查库。</li>
 *   <li>日志仅打印脱敏字段：external_id、fpHash 前 8 位、sourceSystem，
 *       禁止打印金额/户名/备注。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Server
public class DeduplicationServiceImpl implements DeduplicationService {

    private final TransactionRepository repository;
    private final Cache<String, Boolean> cache;

    @Override
    public List<Transaction> deduplicate(List<Transaction> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates 不能为 null");
        }

        // 批次内去重集合，避免同批次重复记录多次查库
        Set<String> seenExternalIds = new HashSet<>();
        Set<String> seenFingerprints = new HashSet<>();
        List<Transaction> result = new ArrayList<>();

        for (Transaction tx : candidates) {
            if (tx == null) {
                throw new IllegalArgumentException("candidates 不能包含 null 元素");
            }

            // 三重防重链：external_id → fingerprint → 缓存，任一命中即跳过
            if (isDuplicateByExternalId(tx, seenExternalIds)) {
                continue;
            }
            if (isDuplicateByFingerprint(tx, seenFingerprints)) {
                continue;
            }
            if (isDuplicateByCache(tx)) {
                continue;
            }

            // 通过全部防重检查，保留并标记缓存
            result.add(tx);
            markCache(tx);
        }

        return result;
    }

    /**
     * 第一重：external_id 防重。
     * 先查批次内（O(1) 内存去重），再查数据库（已完成持久化的记录）。
     */
    private boolean isDuplicateByExternalId(Transaction tx, Set<String> seen) {
        String extId = tx.getExternalId();
        if (extId == null || extId.isBlank()) {
            return false;
        }
        // 批次内去重
        if (!seen.add(extId)) {
            log.info("批次内 external_id 重复，跳过: externalId={}, sourceSystem={}",
                    extId, tx.getSourceSystem());
            return true;
        }
        // 数据库去重——异常自然上抛，外层统一处理
        if (repository.findByExternalId(extId).isPresent()) {
            log.info("数据库中 external_id 已存在，跳过: externalId={}, sourceSystem={}",
                    extId, tx.getSourceSystem());
            return true;
        }
        return false;
    }

    /**
     * 第二重：fingerprint 防重。
     * 结构化哈希（金额+时间+对方+备注+盐值）——external_id 缺失时的兜底机制。
     * 日志仅输出 hash 前 8 位，满足安全合规。
     */
    private boolean isDuplicateByFingerprint(Transaction tx, Set<String> seen) {
        Fingerprint fp = tx.getFingerprint();
        if (fp == null) {
            return false;
        }
        String fpHash = fp.hashValue();
        // 批次内去重
        if (!seen.add(fpHash)) {
            log.info("批次内 fingerprint 重复，跳过: fpHash前8位={}, sourceSystem={}",
                    fpPrefix(fpHash), tx.getSourceSystem());
            return true;
        }
        // 数据库去重——异常自然上抛，外层统一处理
        if (repository.findByFingerprint(fp).isPresent()) {
            log.info("数据库中 fingerprint 已存在，跳过: fpHash前8位={}, sourceSystem={}",
                    fpPrefix(fpHash), tx.getSourceSystem());
            return true;
        }
        return false;
    }

    /**
     * 第三重：Caffeine 缓存预检。
     * 非权威，仅用于降低数据库查询压力。缓存键优先 external_id，其次 fingerprint hash。
     * 真正的唯一性保障依赖 DB 唯一约束。
     */
    private boolean isDuplicateByCache(Transaction tx) {
        String cacheKey = buildCacheKey(tx);
        if (cacheKey == null) {
            return false;
        }
        if (cache.getIfPresent(cacheKey) != null) {
            log.info("缓存中已存在，跳过: externalId={}, fpHash前8位={}, sourceSystem={}",
                    tx.getExternalId(), fpHashPrefix(tx), tx.getSourceSystem());
            return true;
        }
        return false;
    }

    /** 通过防重后标记缓存，后续同批次/跨批次请求可直接跳过 */
    private void markCache(Transaction tx) {
        String cacheKey = buildCacheKey(tx);
        if (cacheKey != null) {
            cache.put(cacheKey, Boolean.TRUE);
        }
    }

    /** 构建缓存键：优先 external_id，其次 fingerprint。null 表示无可用于缓存的标识。 */
    private String buildCacheKey(Transaction tx) {
        String extId = tx.getExternalId();
        if (extId != null && !extId.isBlank()) {
            return "ext:" + extId;
        }
        Fingerprint fp = tx.getFingerprint();
        if (fp != null) {
            return "fp:" + fp.hashValue();
        }
        return null;
    }

    /** 指纹 hash 前 8 位，用于日志脱敏输出 */
    private static String fpPrefix(String fpHash) {
        return fpHash.length() > 8 ? fpHash.substring(0, 8) : fpHash;
    }

    /** 从交易中提取脱敏后的 fingerprint 前缀，null 安全返回 "N/A" */
    private static String fpHashPrefix(Transaction tx) {
        Fingerprint fp = tx.getFingerprint();
        if (fp == null) {
            return "N/A";
        }
        return fpPrefix(fp.hashValue());
    }
}
