package com.finhub.fundflow.infrastructure.repository;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.vo.Fingerprint;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TransactionRepository 的 MyBatis-Plus 实现。
 * 领域层只依赖接口，基础设施层实现细节。
 */
@Repository
public class TransactionRepositoryImpl implements TransactionRepository {

    @Override
    public Optional<Transaction> findById(Long id) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Optional<Transaction> findByExternalId(String externalId) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Optional<Transaction> findByFingerprint(Fingerprint fingerprint) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public List<Transaction> findByCategoryAndTimeRange(String category, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void save(Transaction transaction) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void saveBatch(List<Transaction> transactions) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public long count() {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}