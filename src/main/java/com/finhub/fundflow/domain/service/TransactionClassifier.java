package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.CategorySuggestion;

/**
 * 交易分类领域服务：规则引擎 + AI 建议，核心域保留最终决策权。
 *
 * <h3>分类优先级</h3>
 * <ol>
 *   <li>规则匹配：商户关键词映射（如"美团"→ FOOD，"滴滴"→ TRANSPORT）</li>
 *   <li>用户历史偏好：该用户通常将某商户标为什么类别</li>
 *   <li>AI 兜底：调用 CategorySuggestionEngine（防腐层），返回建议 + 置信度</li>
 * </ol>
 *
 * <p>注意：返回的是 CategorySuggestion（建议），不是最终决策。
 * 应用层根据 confidence &gt; 0.8 决定是否调用 tx.markClassified()。</p>
 */
public interface TransactionClassifier {

    /**
     * 为交易生成分类建议。
     *
     * @param transaction 待分类的交易（已构造，可能未持久化）
     * @return 分类建议（含类别、置信度、来源）
     */
    CategorySuggestion classify(Transaction transaction);
}