package com.finhub.fundflow.infrastructure.repository;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.repository.TransactionRepository;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.Direction;
import com.finhub.fundflow.domain.vo.EncryptedString;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link TransactionRepositoryImpl} 集成测试契约（真实远程 MySQL）。
 *
 * <p>使用 {@code @Transactional} 自动回滚，不污染远程库。
 * 通过 {@code SELECT 1} 探活，DB 不可达时整类跳过（assumeTrue）。</p>
 */
@Tag("integration")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class TransactionRepositoryImplTest {

    private static final String ENC_KEY = "0123456789abcdef0123456789abcdef"; // 32 字节 AES key

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void probeDatabase() throws Exception {
        boolean reachable;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            reachable = rs.next();
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "远程 MySQL 不可达，跳过 Repository 集成测试");
    }

    // =========================================================================
    // save + findByExternalId 往返
    // =========================================================================

    @Test
    @DisplayName("save 后应能按 externalId 查回，金额/方向/分类/指纹 hashValue 一致")
    void shouldSaveAndFindByExternalId() {
        Transaction tx = buildTransaction("ext-save-001", "fp-save-001");

        repository.save(tx);
        Optional<Transaction> found = repository.findByExternalId("ext-save-001");

        assertThat(found).isPresent();
        Transaction loaded = found.get();
        assertThat(loaded.getExternalId()).isEqualTo("ext-save-001");
        assertThat(loaded.getMoney().amount()).isEqualByComparingTo(new BigDecimal("88.88"));
        assertThat(loaded.getMoney().currency()).isEqualTo("CNY");
        assertThat(loaded.getDirection()).isEqualTo(Direction.OUT);
        assertThat(loaded.getCategory()).isEqualTo(Category.UNCLASSIFIED);
        assertThat(loaded.getFingerprint().hashValue()).isEqualTo("fp-save-001");
        assertThat(loaded.getSourceSystem()).isEqualTo("ALIPAY");
    }

    @Test
    @DisplayName("findByExternalId 查不到应返回 Optional.empty")
    void shouldReturnEmptyWhenExternalIdNotExists() {
        Optional<Transaction> found = repository.findByExternalId("ext-not-exist-999");

        assertThat(found).isEmpty();
    }

    // =========================================================================
    // findByFingerprint
    // =========================================================================

    @Test
    @DisplayName("save 后应能按 fingerprint hashValue 查回")
    void shouldSaveAndFindByFingerprint() {
        Transaction tx = buildTransaction("ext-fp-001", "fp-unique-001");
        repository.save(tx);

        Optional<Transaction> found = repository.findByFingerprint(new Fingerprint("fp-unique-001", "any-salt"));

        assertThat(found).isPresent();
        assertThat(found.get().getFingerprint().hashValue()).isEqualTo("fp-unique-001");
    }

    // =========================================================================
    // saveBatch + count
    // =========================================================================

    @Test
    @DisplayName("saveBatch 后 count 应增加对应数量")
    void shouldSaveBatchAndCount() {
        long before = repository.count();

        List<Transaction> batch = List.of(
                buildTransaction("ext-batch-001", "fp-batch-001"),
                buildTransaction("ext-batch-002", "fp-batch-002"),
                buildTransaction("ext-batch-003", "fp-batch-003"));
        repository.saveBatch(batch);

        long after = repository.count();
        assertThat(after - before).isEqualTo(3L);
    }

    // =========================================================================
    // findByCategoryAndTimeRange
    // =========================================================================

    @Test
    @DisplayName("findByCategoryAndTimeRange 应按分类+时间区间过滤命中")
    void shouldFindByCategoryAndTimeRange() {
        LocalDateTime t = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        Transaction food = Transaction.createFrom(
                "ext-range-001", new Money(new BigDecimal("50.00"), "CNY"),
                Direction.OUT, Category.FOOD, t,
                EncryptedString.fromPlain("餐厅", ENC_KEY), EncryptedString.fromPlain("饭", ENC_KEY),
                new Fingerprint("fp-range-001", "salt"), "ALIPAY");
        repository.save(food);

        List<Transaction> results = repository.findByCategoryAndTimeRange(
                "FOOD",
                LocalDateTime.of(2024, 6, 1, 0, 0),
                LocalDateTime.of(2024, 6, 30, 23, 59));

        assertThat(results).extracting(Transaction::getExternalId)
                .contains("ext-range-001");
    }

    // =========================================================================
    // save/saveBatch 回填聚合根 id（insert 后回填）
    // =========================================================================

    @Test
    @DisplayName("save 后应回填聚合根 id")
    void shouldBackfillIdAfterSave() {
        Transaction tx = buildTransaction("ext-idback-001", "fp-idback-001");
        assertThat(tx.getId()).isNull();

        repository.save(tx);

        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getId()).isPositive();
    }

    @Test
    @DisplayName("saveBatch 后每条聚合根 id 均应回填")
    void shouldBackfillIdAfterSaveBatch() {
        List<Transaction> batch = List.of(
                buildTransaction("ext-idback-002", "fp-idback-002"),
                buildTransaction("ext-idback-003", "fp-idback-003"));

        repository.saveBatch(batch);

        assertThat(batch).allMatch(tx -> tx.getId() != null && tx.getId() > 0);
    }

    // =========================================================================
    // 辅助构造
    // =========================================================================

    private Transaction buildTransaction(String externalId, String fpHash) {
        return Transaction.createFrom(
                externalId,
                new Money(new BigDecimal("88.88"), "CNY"),
                Direction.OUT,
                Category.UNCLASSIFIED,
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                EncryptedString.fromPlain("测试商户", ENC_KEY),
                EncryptedString.fromPlain("测试备注", ENC_KEY),
                new Fingerprint(fpHash, "salt"),
                "ALIPAY");
    }
}
