package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import java.util.List;
import java.util.Map;

/**
 * 异常侦探领域服务：基于统计规则检测异常消费模式。
 */
public interface AnomalyDetector {

    Map<Long, AnomalyScore> detect(List<Transaction> transactions);
}