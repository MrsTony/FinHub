package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.CategorySuggestion;

/**
 * 交易分类领域服务：规则引擎 + AI 建议，核心域保留最终决策权。
 */
public interface TransactionClassifier {

    CategorySuggestion classify(Transaction transaction);
}