package com.finhub.fundflow.domain.vo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 分类建议值对象：AI 或规则引擎返回的分类建议（非最终决策）。
 * confidence &gt; 0.8 且来源为 RULE 或 AI 时允许自动采纳。
 */
public record CategorySuggestion(Category category, BigDecimal confidence, String source) {

    public CategorySuggestion {
        Objects.requireNonNull(category, "类别不能为空");
        Objects.requireNonNull(confidence, "置信度不能为空");
        Objects.requireNonNull(source, "来源不能为空");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("置信度必须在 0.0 ~ 1.0 之间");
        }
    }

    /** 自动采纳阈值：confidence 严格大于此值方可采纳 */
    private static final BigDecimal ADOPT_THRESHOLD = new BigDecimal("0.8");

    /** 允许自动采纳的来源白名单 */
    private static final java.util.Set<String> ADOPTABLE_SOURCES = java.util.Set.of("RULE", "AI");

    /** confidence &gt; 0.8 且来源为 RULE 或 AI 时允许自动采纳 */
    public boolean isAdoptable() {
        return confidence.compareTo(ADOPT_THRESHOLD) > 0
                && ADOPTABLE_SOURCES.contains(source);
    }
}