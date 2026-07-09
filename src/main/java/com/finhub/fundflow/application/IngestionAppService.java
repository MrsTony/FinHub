package com.finhub.fundflow.application;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.event.*;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.service.*;
import com.finhub.fundflow.domain.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
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
public class IngestionAppService {

    private final DataSourceAdapter dataSourceAdapter;
    private final FingerprintGenerator fingerprintGenerator;
    private final DeduplicationService deduplicationService;
    private final TransactionClassifier transactionClassifier;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 盐值从环境变量注入（基础设施配置） */
    private final String fingerprintSalt;

    /**
     * 导入文件主流程。
     *
     * @param inputStream 文件流（由 Controller 提供，应用层不管理资源生命周期）
     * @param filename    原始文件名
     * @return 导入结果（成功数、跳过数、失败数）
     */
    public ImportResult importFile(InputStream inputStream, String filename) {
        // TODO: 实现编排逻辑
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * 导入结果 DTO（应用层技术对象，非领域对象）。
     */
    public record ImportResult(int imported, int skipped, int failed) {
    }
}