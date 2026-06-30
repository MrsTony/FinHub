package com.finhub.fundflow.domain.event;

import com.finhub.fundflow.domain.vo.AnomalyScore;

/** 领域事件：异常交易已标记 */
public record AnomalyDetectedEvent(Long transactionId, AnomalyScore score) {
}