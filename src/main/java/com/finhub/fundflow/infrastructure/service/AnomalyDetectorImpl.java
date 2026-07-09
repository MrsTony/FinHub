package com.finhub.fundflow.infrastructure.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.service.AnomalyDetector;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.Category;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AnomalyDetector 实现：基于统计规则的异常检测。
 *
 * <h3>已实现规则（MVP）</h3>
 * <ul>
 *   <li>金额异常：按 Category 分组，计算平均金额，单笔 &gt; 3x → AMOUNT_SPIKE(0.9)，&gt; 1.5x → AMOUNT_HIGH(0.6)</li>
 *   <li>方向区分：收入/支出按各自 Category 独立计算</li>
 * </ul>
 *
 * <h3>预留规则（后期扩展）</h3>
 * <ul>
 *   <li>重复扣款：7 天内相同金额 + 相同对方户名 &gt; 2 次（需查询数据库，MVP 不实现）</li>
 *   <li>订阅陷阱：同一商户小额按月规律扣款（需跨批次数据，MVP 不实现）</li>
 * </ul>
 */
@Slf4j
public class AnomalyDetectorImpl implements AnomalyDetector {

    /** 金额异常-尖峰阈值 */
    private static final BigDecimal SPIKE_THRESHOLD = new BigDecimal("3");

    /** 金额异常-偏高阈值 */
    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("1.5");

    /** 金额异常-尖峰评分 */
    private static final BigDecimal SPIKE_SCORE = new BigDecimal("0.9");

    /** 金额异常-偏高评分 */
    private static final BigDecimal HIGH_SCORE = new BigDecimal("0.6");

    /** 金额异常-尖峰原因代码 */
    private static final String REASON_AMOUNT_SPIKE = "AMOUNT_SPIKE";

    /** 金额异常-偏高原因代码 */
    private static final String REASON_AMOUNT_HIGH = "AMOUNT_HIGH";

    @Override
    public Map<String, AnomalyScore> detect(List<Transaction> transactions) {
        // 1. 空值校验
        validateInput(transactions);

        // 2. 空列表直接返回
        if (transactions.isEmpty()) {
            return Map.of();
        }

        // 3. 按 Category 分组并逐组检测
        Map<String, AnomalyScore> anomalies = new HashMap<>();
        Map<Category, List<Transaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory));
        grouped.values().forEach(group -> detectGroup(group, anomalies));

        // 4. 记录日志并返回
        logResults(anomalies);
        return anomalies;
    }

    /**
     * 校验输入非空且不含 null 元素。
     */
    private void validateInput(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "交易列表不能为空");
        for (Transaction tx : transactions) {
            if (tx == null) {
                throw new IllegalArgumentException("交易列表不能包含 null 元素");
            }
        }
    }

    /**
     * 检测单组交易：单笔不检测，多笔则逐笔与"排除自身"的组内平均比较。
     */
    private void detectGroup(List<Transaction> group, Map<String, AnomalyScore> anomalies) {
        // 单笔交易无法计算异常（自身 = 平均）
        if (group.size() <= 1) {
            return;
        }
        // 检测每笔交易（排除自身计算平均，避免异常值抬高基准线）
        for (Transaction tx : group) {
            BigDecimal amount = tx.getMoney().amount().abs();
            BigDecimal avgExcluding = computeAverageExcluding(group, tx);
            AnomalyScore score = detectAmountAnomaly(amount, avgExcluding);
            if (score != null) {
                anomalies.put(resolveKey(tx), score);
            }
        }
    }

    /**
     * 统计异常数量与类型并打印日志（不打印具体金额）。
     */
    private void logResults(Map<String, AnomalyScore> anomalies) {
        long spikeCount = anomalies.values().stream()
                .filter(s -> REASON_AMOUNT_SPIKE.equals(s.reasonCode())).count();
        long highCount = anomalies.values().stream()
                .filter(s -> REASON_AMOUNT_HIGH.equals(s.reasonCode())).count();
        log.info("异常检测完成: 共检测 {} 笔异常 (AMOUNT_SPIKE: {}, AMOUNT_HIGH: {})",
                anomalies.size(), spikeCount, highCount);
    }

    /**
     * 计算排除指定交易后的组内平均金额（绝对值）。
     */
    private BigDecimal computeAverageExcluding(List<Transaction> group, Transaction excluded) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Transaction tx : group) {
            if (tx == excluded) {
                continue;
            }
            total = total.add(tx.getMoney().amount().abs());
            count++;
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * 检测单笔金额是否异常。
     *
     * @return AnomalyScore 或 null（无异常）
     */
    private AnomalyScore detectAmountAnomaly(BigDecimal amount, BigDecimal avgAmount) {
        if (avgAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal ratio = amount.divide(avgAmount, 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(SPIKE_THRESHOLD) > 0) {
            return new AnomalyScore(SPIKE_SCORE, REASON_AMOUNT_SPIKE);
        }
        if (ratio.compareTo(HIGH_THRESHOLD) > 0) {
            return new AnomalyScore(HIGH_SCORE, REASON_AMOUNT_HIGH);
        }
        return null;
    }

    /**
     * 解析交易标识 key：优先 externalId，为 null 时使用 fingerprint.hashValue。
     */
    private String resolveKey(Transaction tx) {
        if (tx.getExternalId() != null && !tx.getExternalId().isBlank()) {
            return tx.getExternalId();
        }
        return tx.getFingerprint().hashValue();
    }

    /*
     * ========== 预留扩展点 ==========
     *
     * 重复扣款检测（后期实现）：
     *   7 天内相同金额 + 相同对方户名 > 2 次 → AnomalyScore("DUPLICATE_CHARGE", 0.8)
     *   需查询数据库中的历史交易，跨聚合根查询。
     *   实现时新增 detectDuplicateCharge() 方法，在 detect() 末尾调用。
     *
     * 订阅陷阱检测（后期实现）：
     *   同一商户小额（< 30 元）按月规律扣款 → AnomalyScore("SUBSCRIPTION_TRAP", 0.7)
     *   需跨批次数据，MVP 阶段不实现。
     *   实现时新增 detectSubscriptionTrap() 方法，在 detect() 末尾调用。
     *
     * 扩展方式：在 detect() 方法末尾追加新规则调用，不影响现有规则。
     */
}
