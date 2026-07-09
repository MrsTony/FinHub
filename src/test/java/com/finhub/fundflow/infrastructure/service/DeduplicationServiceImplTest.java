package com.finhub.fundflow.infrastructure.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.service.DeduplicationService;
import com.finhub.fundflow.domain.service.DeduplicationServiceTest;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeduplicationServiceImpl 测试实现。
 * 使用 Mock Repository + 真实 Caffeine 缓存验证三重防重逻辑。
 */
class DeduplicationServiceImplTest extends DeduplicationServiceTest {

    private TransactionRepository repository;
    private Cache<String, Boolean> cache;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        // 默认：数据库中没有重复记录
        when(repository.findByExternalId(any())).thenReturn(Optional.empty());
        when(repository.findByFingerprint(any())).thenReturn(Optional.empty());
    }

    @Override
    protected DeduplicationService createService() {
        return new DeduplicationServiceImpl(repository, cache);
    }
}
