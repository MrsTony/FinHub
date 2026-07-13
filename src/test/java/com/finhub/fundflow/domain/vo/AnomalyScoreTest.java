package com.finhub.fundflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnomalyScore 值对象测试契约。
 *
 * <p>核心契约：{@code score > 0.7} 视为高异常（{@link AnomalyScore#isAlert()}）。
 * 边界 0.7 本身不视为告警（严格大于）。</p>
 */
class AnomalyScoreTest {

    private static final String REASON = "AMOUNT_SPIKE";

    // =========================================================================
    // isAlert 阈值判定（严格大于 0.7）
    // =========================================================================

    @Test
    @DisplayName("score 高于阈值应返回 true")
    void shouldReturnTrueWhenScoreAboveThreshold() {
        AnomalyScore score = new AnomalyScore(new BigDecimal("0.71"), REASON);

        assertThat(score.isAlert()).isTrue();
    }

    @Test
    @DisplayName("score 等于阈值 0.7 应返回 false（严格大于）")
    void shouldReturnFalseWhenScoreEqualsThreshold() {
        AnomalyScore score = new AnomalyScore(new BigDecimal("0.7"), REASON);

        assertThat(score.isAlert()).isFalse();
    }

    @Test
    @DisplayName("score 低于阈值应返回 false")
    void shouldReturnFalseWhenScoreBelowThreshold() {
        AnomalyScore score = new AnomalyScore(new BigDecimal("0.5"), REASON);

        assertThat(score.isAlert()).isFalse();
    }

    // =========================================================================
    // 边界值参数化覆盖
    // =========================================================================

    @ParameterizedTest
    @DisplayName("临界值应按严格大于 0.7 判定")
    @CsvSource({
            "0.69, false",
            "0.70, false",
            "0.70, false",
            "0.71, true",
            "1.00, true",
            "0.00, false"
    })
    void shouldJudgeAlertByStrictGreaterThanThreshold(String scoreValue, boolean expectedAlert) {
        AnomalyScore score = new AnomalyScore(new BigDecimal(scoreValue), REASON);

        assertThat(score.isAlert()).isEqualTo(expectedAlert);
    }

    // =========================================================================
    // 精度语义：0.7 与 0.70 用 compareTo 视为相等（不影响阈值判定）
    // =========================================================================

    @Test
    @DisplayName("score=0.70 与 0.7 精度等价，均不告警")
    void shouldTreatDifferentScaleAsEqualByCompareTo() {
        AnomalyScore scoreA = new AnomalyScore(new BigDecimal("0.70"), REASON);
        AnomalyScore scoreB = new AnomalyScore(new BigDecimal("0.7"), REASON);

        assertThat(scoreA.isAlert()).isFalse();
        assertThat(scoreB.isAlert()).isFalse();
    }
}
