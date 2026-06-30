package com.finhub.fundflow.domain.event;

/** 领域事件：发现重复交易 */
public record DuplicateDetectedEvent(Long transactionId, String duplicateFingerprint) {
}