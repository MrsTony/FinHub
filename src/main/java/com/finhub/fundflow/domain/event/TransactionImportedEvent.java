package com.finhub.fundflow.domain.event;

import java.time.LocalDateTime;

/** 领域事件：交易已成功导入 */
public record TransactionImportedEvent(Long transactionId, LocalDateTime occurredOn) {
}