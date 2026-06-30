package com.finhub.ai.acl;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.CategorySuggestion;

/**
 * 防腐层：分类建议引擎。
 * 输入 Transaction 特征，输出 CategorySuggestion（建议，非最终决策）。
 */
public interface CategorySuggestionEngine {

    CategorySuggestion suggest(Transaction transaction);
}