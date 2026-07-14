package com.finhub.fundflow.application;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.service.AnomalyDetector;
import com.finhub.fundflow.domain.service.DeduplicationService;
import com.finhub.fundflow.domain.service.FingerprintGenerator;
import com.finhub.fundflow.domain.service.TransactionClassifier;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.CategorySuggestion;
import com.finhub.fundflow.domain.vo.Fingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IngestionAppService#importFile} 编排测试契约（纯单测，Mock 6 依赖 + 注入 salt/key）。
 *
 * <p>验证 9 步编排：adapt -> 逐条构建 Transaction（容错）-> deduplicate -> classify（markClassified）
 * -> detect（markAnomaly）-> saveBatch -> 发布事件 -> 返回 ImportResult。</p>
 */
@ExtendWith(MockitoExtension.class)
class IngestionAppServiceTest {

    private static final String SALT = "test-salt";
    private static final String ENC_KEY = "0123456789abcdef0123456789abcdef"; // 32 字节

    @Mock
    private DataSourceAdapter dataSourceAdapter;
    @Mock
    private FingerprintGenerator fingerprintGenerator;
    @Mock
    private DeduplicationService deduplicationService;
    @Mock
    private TransactionClassifier transactionClassifier;
    @Mock
    private AnomalyDetector anomalyDetector;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IngestionAppService service;

    @BeforeEach
    void setUp() {
        service = new IngestionAppService(
                dataSourceAdapter, fingerprintGenerator, deduplicationService,
                transactionClassifier, anomalyDetector, transactionRepository,
                eventPublisher, SALT, ENC_KEY);

        // 指纹生成：返回固定指纹（dedup 被 mock，不依赖 hash 唯一性）
        lenient().when(fingerprintGenerator.generate(
                anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(new Fingerprint("fp-hash", SALT));

        // 去重：默认透传（按需在各用例覆盖）
        lenient().when(deduplicationService.deduplicate(any())).thenAnswer(inv -> inv.getArgument(0));

        // 分类：默认不可采纳（按需在各用例覆盖）
        lenient().when(transactionClassifier.classify(any()))
                .thenReturn(new CategorySuggestion(Category.UNCLASSIFIED, new BigDecimal("0.5"), "RULE"));

        // 异常检测：默认无异常（按需覆盖）
        lenient().when(anomalyDetector.detect(any())).thenReturn(Map.of());

        // saveBatch / publishEvent 为 void，Mock 默认空操作，无需显式 stub
    }

    @Test
    @DisplayName("2 条有效记录应全部导入：imported=2，saveBatch 调用 1 次（size=2），事件已发布并清空")
    void shouldImportAllValidRecords() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e1", "美团外卖", "100.00", "支出"),
                raw("e2", "饿了么", "50.00", "支出")));
        // 采纳分类 -> 每条产生 1 个 TransactionClassifiedEvent
        when(transactionClassifier.classify(any()))
                .thenReturn(new CategorySuggestion(Category.FOOD, new BigDecimal("0.9"), "RULE"));

        IngestionAppService.ImportResult result = service.importFile(mockStream(), "alipay.csv");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();

        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        // 每条产生 1 个分类事件 -> publishEvent 调 2 次（事件是 record，走 publishEvent(Object) 重载）
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        // 事件已清空
        assertThat(captor.getValue()).allMatch(tx -> tx.getDomainEvents().isEmpty());
    }

    @Test
    @DisplayName("非法方向行应跳过：skipped+1，有效行不受影响")
    void shouldSkipInvalidDirectionRow() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e-bad", "商户", "100.00", "不明方向"),
                raw("e-ok", "美团外卖", "50.00", "支出")));

        IngestionAppService.ImportResult result = service.importFile(mockStream(), "alipay.csv");

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("金额为 null 的行应跳过：skipped+1")
    void shouldSkipNullAmountRow() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                new RawRecord("e-null", null, "CNY", "支出",
                        "商户", "备注", LocalDateTime.of(2024, 1, 15, 10, 30), "ALIPAY"),
                raw("e-ok", "美团外卖", "50.00", "支出")));

        IngestionAppService.ImportResult result = service.importFile(mockStream(), "alipay.csv");

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("相同 externalId 应去重后只导入 1 条")
    void shouldDeduplicateBeforeSave() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e-dup", "美团外卖", "100.00", "支出"),
                raw("e-dup", "美团外卖", "100.00", "支出")));
        // 去重过滤掉 1 条
        when(deduplicationService.deduplicate(any()))
                .thenAnswer(inv -> {
                    List<Transaction> in = inv.getArgument(0);
                    return in.subList(0, 1);
                });

        IngestionAppService.ImportResult result = service.importFile(mockStream(), "alipay.csv");

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("分类建议可采纳时应调用 markClassified：tx.category 变更为 FOOD")
    void shouldClassifyWhenSuggestionAdoptable() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e1", "美团外卖", "100.00", "支出")));
        when(transactionClassifier.classify(any()))
                .thenReturn(new CategorySuggestion(Category.FOOD, new BigDecimal("0.9"), "RULE"));

        service.importFile(mockStream(), "alipay.csv");

        Transaction tx = captureSavedTransaction();
        assertThat(tx.getCategory()).isEqualTo(Category.FOOD);
    }

    @Test
    @DisplayName("分类建议不可采纳时不调用 markClassified：tx.category 保持 UNCLASSIFIED")
    void shouldNotClassifyWhenSuggestionNotAdoptable() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e1", "未知商户", "100.00", "支出")));
        when(transactionClassifier.classify(any()))
                .thenReturn(new CategorySuggestion(Category.UNCLASSIFIED, new BigDecimal("0.5"), "RULE"));

        service.importFile(mockStream(), "alipay.csv");

        Transaction tx = captureSavedTransaction();
        assertThat(tx.getCategory()).isEqualTo(Category.UNCLASSIFIED);
    }

    @Test
    @DisplayName("异常检测返回告警分值时应调用 markAnomaly：tx.anomalyFlag=true")
    void shouldMarkAnomalyWhenDetectorReturnsScore() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e1", "美团外卖", "100.00", "支出")));
        when(anomalyDetector.detect(any()))
                .thenReturn(Map.of("e1", new AnomalyScore(new BigDecimal("0.9"), "AMOUNT_SPIKE")));

        service.importFile(mockStream(), "alipay.csv");

        Transaction tx = captureSavedTransaction();
        assertThat(tx.isAnomalyFlag()).isTrue();
    }

    @Test
    @DisplayName("应发布全部领域事件并清空：分类事件 + 异常事件 = 2 次 publishEvent")
    void shouldPublishDomainEvents() {
        when(dataSourceAdapter.adapt(any(), anyString())).thenReturn(List.of(
                raw("e1", "美团外卖", "100.00", "支出")));
        when(transactionClassifier.classify(any()))
                .thenReturn(new CategorySuggestion(Category.FOOD, new BigDecimal("0.9"), "RULE"));
        when(anomalyDetector.detect(any()))
                .thenReturn(Map.of("e1", new AnomalyScore(new BigDecimal("0.9"), "AMOUNT_SPIKE")));

        service.importFile(mockStream(), "alipay.csv");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        assertThat(captureSavedTransaction().getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("adapt 抛 IllegalArgumentException 应透传")
    void shouldThrowWhenAdaptFails() {
        when(dataSourceAdapter.adapt(any(), anyString()))
                .thenThrow(new IllegalArgumentException("无法识别数据源"));

        assertThatThrownBy(() -> service.importFile(mockStream(), "bank.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别数据源");
    }

    @Test
    @DisplayName("inputStream 为 null 应抛 IllegalArgumentException")
    void shouldRejectNullInputStream() {
        assertThatThrownBy(() -> service.importFile(null, "alipay.csv"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("filename 为 null 应抛 IllegalArgumentException")
    void shouldRejectNullFilename() {
        assertThatThrownBy(() -> service.importFile(mockStream(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // 辅助构造
    // =========================================================================

    private RawRecord raw(String externalId, String counterparty, String amount, String direction) {
        return new RawRecord(externalId, new BigDecimal(amount), "CNY", direction,
                counterparty, "备注", LocalDateTime.of(2024, 1, 15, 10, 30, 0), "ALIPAY");
    }

    private InputStream mockStream() {
        return org.mockito.Mockito.mock(InputStream.class);
    }

    /** 捕获 saveBatch 的单条 Transaction（用于断言聚合根状态变更） */
    private Transaction captureSavedTransaction() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        return captor.getValue().get(0);
    }
}
