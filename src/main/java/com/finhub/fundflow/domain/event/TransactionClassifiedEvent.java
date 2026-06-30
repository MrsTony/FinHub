package com.finhub.fundflow.domain.event;

import com.finhub.fundflow.domain.vo.Category;

/** 领域事件：交易分类已完成 */
public record TransactionClassifiedEvent(Long transactionId, Category category, String source) {
}