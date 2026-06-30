package com.finhub.fundflow.domain.repository;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.Fingerprint;
import java.util.List;
import java.util.Optional;

/**
 * 资金流水聚合根仓库接口（领域层定义，基础设施层实现）。
 */
public interface TransactionRepository {

    Optional<Transaction> findById(Long id);

    Optional<Transaction> findByExternalId(String externalId);

    Optional<Transaction> findByFingerprint(Fingerprint fingerprint);

    List<Transaction> findByCategoryAndTimeRange(String category, java.time.LocalDateTime start, java.time.LocalDateTime end);

    void save(Transaction transaction);

    void saveBatch(List<Transaction> transactions);

    long count();
}