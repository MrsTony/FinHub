package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import java.util.List;

/**
 * 排重领域服务：跨记录排重判断（需读取已有聚合，故不放在聚合根内）。
 *
 * <p>三重防重：external_id → fingerprint → 缓存预检 + DB 唯一约束兜底。</p>
 */
public interface DeduplicationService {

    List<Transaction> deduplicate(List<Transaction> candidates);
}