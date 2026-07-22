package com.finhub.fundflow.application;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.event.*;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.service.*;
import com.finhub.fundflow.domain.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导入编排服务：应用层用例编排。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>调用防腐层解析 CSV → RawRecord</li>
 *   <li>调用 FingerprintGenerator 生成指纹</li>
 *   <li>构造 Transaction 聚合根（工厂方法校验不变量）</li>
 *   <li>调用 DeduplicationService 排重</li>
 *   <li>调用 TransactionClassifier 分类建议</li>
 *   <li>聚合根决策是否采纳分类（markClassified）</li>
 *   <li>调用 TransactionRepository 持久化</li>
 *   <li>发布领域事件</li>
 * </ol>
 *
 * <p>注意：此层只编排，不决策。所有业务规则在领域层。</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class IngestionAppService {

    private final DataSourceAdapter dataSourceAdapter;
    private final FingerprintGenerator fingerprintGenerator;
    private final DeduplicationService deduplicationService;
    private final TransactionClassifier transactionClassifier;
    private final AnomalyDetector anomalyDetector;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 盐值从配置注入（基础设施配置，lombok.config 使 @Value 透传到构造器参数） */
    @Value("${finhub.fingerprint.salt}")
    private final String fingerprintSalt;

    /** AES 加密密钥，从配置注入（对方户名/备注等敏感字段加密用，必须 32 字节） */
    @Value("${finhub.encryption.key}")
    private final String encryptionKey;

    /**
     * 导入文件主流程（9 步编排）。
     *
     * <p>adapt -> 逐条构建 Transaction（容错）-> deduplicate -> classify（markClassified）
     * -> detect（markAnomaly）-> saveBatch -> 发布领域事件 -> 返回 ImportResult。</p>
     *
     * @param inputStream 文件流（由 Controller 提供，应用层不管理资源生命周期）
     * @param filename    原始文件名
     * @return 导入结果（成功数、跳过数、失败数）
     * @throws IllegalArgumentException inputStream/filename 为 null，或 adapt 严重失败
     */
    public ImportResult importFile(InputStream inputStream, String filename) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 1. 防腐层解析 CSV -> RawRecord（adapt 整体失败则透传 IllegalArgumentException）
        List<RawRecord> rawRecords = dataSourceAdapter.adapt(inputStream, filename);

        // 2. 加密密钥已由构造器注入（encryptionKey）

        // 3. 逐条构建 Transaction（单条失败不阻断整批，计入 skipped）
        List<Transaction> candidates = new ArrayList<>(rawRecords.size());
        int invalidRows = 0;
        for (RawRecord raw : rawRecords) {
            try {
                candidates.add(buildTransaction(raw));
            } catch (Exception e) {
                invalidRows++;
                log.warn("跳过无效记录: externalId={}, reason={}", raw.externalId(), e.getMessage());
            }
        }

        // 4. 三重防重（external_id -> fingerprint -> 缓存 + DB 查重）
        List<Transaction> deduped = deduplicationService.deduplicate(candidates);

        // 5. 分类建议：可采纳则 markClassified
        for (Transaction tx : deduped) {
            try {
                CategorySuggestion suggestion = transactionClassifier.classify(tx);
                if (suggestion != null && suggestion.isAdoptable()) {
                    tx.markClassified(suggestion.category(), suggestion.source());
                }
            } catch (Exception e) {
                // 分类失败不阻断，保持 UNCLASSIFIED
                log.warn("分类失败，保持 UNCLASSIFIED: externalId={}, reason={}",
                        tx.getExternalId(), e.getMessage());
            }
        }

        // 6. 异常检测：命中则 markAnomaly
        Map<String, AnomalyScore> anomalies = anomalyDetector.detect(deduped);
        for (Transaction tx : deduped) {
            AnomalyScore score = anomalies.get(resolveKey(tx));
            if (score != null) {
                tx.markAnomaly(score);
            }
        }

        // 7. 持久化（@Transactional 类级回滚保护）
        transactionRepository.saveBatch(deduped);

        // 8. 发布领域事件并清空（saveBatch 已回填 id 并丰富事件，transactionId 为真实主键）
        for (Transaction tx : deduped) {
            for (Object event : tx.getDomainEvents()) {
                eventPublisher.publishEvent(event);
            }
            tx.clearDomainEvents();
        }

        // 9. 汇总结果：去重跳过数 + 无效行数
        int dedupSkipped = candidates.size() - deduped.size();
        int imported = deduped.size();
        log.info("导入完成: sourceSystem={}, imported={}, skipped={}, failed=0, fpHash前8位={}",
                firstSourceSystem(deduped), imported, invalidRows + dedupSkipped,
                deduped.isEmpty() ? "N/A" : fpPrefix(deduped.get(0).getFingerprint()));
        return new ImportResult(imported, invalidRows + dedupSkipped, 0);
    }

    // =========================================================================
    // 私有辅助
    // =========================================================================

    /** 备注空值占位符，与 FingerprintGeneratorImpl.EMPTY_REMARK_PLACEHOLDER 对齐 */
    private static final String EMPTY_REMARK_PLACEHOLDER = "__EMPTY__";

    /**
     * 由原始记录构建聚合根。任一不变量违例（方向/金额/户名/时间非法）即抛异常，
     * 由调用方 try/catch 计入 skipped。
     */
    private Transaction buildTransaction(RawRecord raw) {
        Direction direction = parseDirection(raw.direction());
        String currency = (raw.currency() == null || raw.currency().isBlank()) ? "CNY" : raw.currency();
        Money money = new Money(raw.amount(), currency);

        String counterpartyPlain = raw.counterparty();
        EncryptedString encCounterparty = EncryptedString.fromPlain(counterpartyPlain, encryptionKey);

        String remarkPlain = (raw.remark() == null || raw.remark().isBlank())
                ? EMPTY_REMARK_PLACEHOLDER : raw.remark();
        EncryptedString encRemark = EncryptedString.fromPlain(remarkPlain, encryptionKey);

        Fingerprint fingerprint = fingerprintGenerator.generate(
                counterpartyPlain, money, raw.transTime(), remarkPlain, fingerprintSalt);

        return Transaction.createFrom(
                raw.externalId(), money, direction, Category.UNCLASSIFIED,
                raw.transTime(), encCounterparty, encRemark, fingerprint, raw.sourceSystem());
    }

    /** 方向解析：支持 IN/OUT 与 收入/支出，其余抛 IllegalArgumentException */
    private Direction parseDirection(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("方向不能为空");
        }
        return switch (raw.trim()) {
            case "IN", "收入" -> Direction.IN;
            case "OUT", "支出" -> Direction.OUT;
            default -> throw new IllegalArgumentException("无法识别的方向: " + raw);
        };
    }

    /** 异常检测结果的 key 解析：优先 externalId，否则 fingerprint.hashValue（与 AnomalyDetectorImpl 一致） */
    private String resolveKey(Transaction tx) {
        String extId = tx.getExternalId();
        if (extId != null && !extId.isBlank()) {
            return extId;
        }
        return tx.getFingerprint().hashValue();
    }

    private static String firstSourceSystem(List<Transaction> deduped) {
        return deduped.isEmpty() ? "N/A" : deduped.get(0).getSourceSystem();
    }

    /** 指纹 hash 前 8 位，用于日志脱敏 */
    private static String fpPrefix(Fingerprint fp) {
        if (fp == null) {
            return "N/A";
        }
        String hash = fp.hashValue();
        return hash.length() > 8 ? hash.substring(0, 8) : hash;
    }

    /**
     * 导入结果 DTO（应用层技术对象，非领域对象）。
     */
    public record ImportResult(int imported, int skipped, int failed) {
    }
}