package com.finhub.fundflow.domain.vo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 异常评分值对象：0.0 ~ 1.0，附带原因代码。
 * score &gt; 0.7 视为高异常。
 */
public record AnomalyScore(BigDecimal score, String reasonCode) {

    public AnomalyScore {
        Objects.requireNonNull(score, "评分不能为空");
        Objects.requireNonNull(reasonCode, "原因代码不能为空");
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("评分必须在 0.0 ~ 1.0 之间");
        }
    }

    /** 告警阈值：score 严格大于此值视为高异常 */
    private static final BigDecimal ALERT_THRESHOLD = new BigDecimal("0.7");

    /** score &gt; 0.7 视为高异常 */
    public boolean isAlert() {
        return score.compareTo(ALERT_THRESHOLD) > 0;
    }
}