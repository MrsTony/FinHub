package com.finhub.ai.acl;

import com.finhub.fundflow.domain.vo.AnomalyScore;
import java.math.BigDecimal;

/**
 * 防腐层：异常解释引擎。
 * 将结构化异常评分翻译为自然语言解释（建议，非决策）。
 */
public interface AnomalyExplainer {

    AnomalyExplanation explain(AnomalyScore score, String counterparty, BigDecimal amount);

    record AnomalyExplanation(String explanation, String suggestedAction) {
    }
}