package com.finhub.fundflow.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.infrastructure.repository.mapper.TransactionMapper;
import com.finhub.fundflow.infrastructure.repository.po.TransactionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link TransactionRepository} 的 MyBatis-Plus 实现。
 *
 * <p>领域层只依赖接口，基础设施层负责持久化细节：PO 装配、Mapper 调用、领域 ↔ PO 转换。
 * 不回填聚合根 id（MVP 决策，详见 {@link TransactionConverter} 已知缺口）。</p>
 */
@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionMapper transactionMapper;

    @Override
    public Optional<Transaction> findById(Long id) {
        TransactionPO po = transactionMapper.selectById(id);
        return Optional.ofNullable(po).map(TransactionConverter::toDomain);
    }

    @Override
    public Optional<Transaction> findByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        TransactionPO po = transactionMapper.selectOne(
                new LambdaQueryWrapper<TransactionPO>()
                        .eq(TransactionPO::getExternalId, externalId));
        return Optional.ofNullable(po).map(TransactionConverter::toDomain);
    }

    @Override
    public Optional<Transaction> findByFingerprint(Fingerprint fingerprint) {
        if (fingerprint == null) {
            return Optional.empty();
        }
        TransactionPO po = transactionMapper.selectOne(
                new LambdaQueryWrapper<TransactionPO>()
                        .eq(TransactionPO::getFingerprint, fingerprint.hashValue()));
        return Optional.ofNullable(po).map(TransactionConverter::toDomain);
    }

    @Override
    public List<Transaction> findByCategoryAndTimeRange(String category, LocalDateTime start, LocalDateTime end) {
        if (category == null || category.isBlank() || start == null || end == null) {
            return List.of();
        }
        // category 列存储枚举名（EnumTypeHandler），直接以字符串匹配，避免枚举转换歧义
        List<TransactionPO> pos = transactionMapper.selectList(
                new LambdaQueryWrapper<TransactionPO>()
                        .eq(TransactionPO::getCategory, category)
                        .between(TransactionPO::getTransTime, start, end)
                        .orderByDesc(TransactionPO::getTransTime));
        return pos.stream().map(TransactionConverter::toDomain).toList();
    }

    @Override
    public void save(Transaction transaction) {
        TransactionPO po = TransactionConverter.toPO(transaction);
        transactionMapper.insert(po);
        // MVP 决策：不回填 PO 自增 id 到聚合根（Transaction 无 setId）
    }

    @Override
    public void saveBatch(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        for (Transaction transaction : transactions) {
            transactionMapper.insert(TransactionConverter.toPO(transaction));
        }
        // MVP 逐条插入；Day6+ 可切换 SqlSession BATCH 模式利用 rewriteBatchedStatements
    }

    @Override
    public long count() {
        return transactionMapper.selectCount(null);
    }
}
