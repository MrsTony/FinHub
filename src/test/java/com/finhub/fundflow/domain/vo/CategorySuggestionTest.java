package com.finhub.fundflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CategorySuggestion 值对象测试契约。
 *
 * <p>核心契约：{@code confidence > 0.8} 且 {@code source} 为 {@code "RULE"} 或 {@code "AI"}
 * 时允许自动采纳（{@link CategorySuggestion#isAdoptable()}）。
 * 边界 0.8 本身不采纳（严格大于）。</p>
 */
class CategorySuggestionTest {

    // =========================================================================
    // isAdoptable：高置信度 + 合规来源
    // =========================================================================

    @Test
    @DisplayName("高置信度且来源为 RULE 应可采纳")
    void shouldReturnTrueWhenHighConfidenceAndRuleSource() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.81"), "RULE");

        assertThat(suggestion.isAdoptable()).isTrue();
    }

    @Test
    @DisplayName("高置信度且来源为 AI 应可采纳")
    void shouldReturnTrueWhenHighConfidenceAndAiSource() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.81"), "AI");

        assertThat(suggestion.isAdoptable()).isTrue();
    }

    // =========================================================================
    // isAdoptable：置信度不足
    // =========================================================================

    @Test
    @DisplayName("置信度等于阈值 0.8 应不可采纳（严格大于）")
    void shouldReturnFalseWhenConfidenceEqualsThreshold() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.8"), "RULE");

        assertThat(suggestion.isAdoptable()).isFalse();
    }

    @Test
    @DisplayName("低置信度应不可采纳")
    void shouldReturnFalseWhenLowConfidence() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.5"), "RULE");

        assertThat(suggestion.isAdoptable()).isFalse();
    }

    // =========================================================================
    // isAdoptable：来源不合规
    // =========================================================================

    @Test
    @DisplayName("未知来源（如 MANUAL）即使高置信度也不可采纳")
    void shouldReturnFalseWhenSourceIsUnknown() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.9"), "MANUAL");

        assertThat(suggestion.isAdoptable()).isFalse();
    }

    @Test
    @DisplayName("来源大小写敏感：小写 rule 不视为合规来源")
    void shouldReturnFalseWhenSourceIsLowercaseRule() {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal("0.9"), "rule");

        assertThat(suggestion.isAdoptable()).isFalse();
    }

    // =========================================================================
    // 边界值参数化覆盖（confidence, source -> adoptable）
    // =========================================================================

    @ParameterizedTest
    @DisplayName("临界值应按严格大于 0.8 且来源为 RULE/AI 判定")
    @CsvSource({
            "0.79, RULE, false",
            "0.80, RULE, false",
            "0.81, RULE, true",
            "0.81, AI,    true",
            "0.81, MANUAL,false",
            "1.00, RULE, true",
            "0.00, RULE, false"
    })
    void shouldJudgeAdoptableByThresholdAndSource(String confidenceValue,
                                                  String source,
                                                  boolean expectedAdoptable) {
        CategorySuggestion suggestion = new CategorySuggestion(
                Category.FOOD, new BigDecimal(confidenceValue), source);

        assertThat(suggestion.isAdoptable()).isEqualTo(expectedAdoptable);
    }
}
