package com.finhub.fundflow.infrastructure.repository;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.Direction;
import com.finhub.fundflow.domain.vo.EncryptedString;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import com.finhub.fundflow.infrastructure.repository.po.TransactionPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TransactionConverter} 测试契约（纯单测，不依赖数据库）。
 *
 * <p>验证领域聚合根 <-> 持久化对象的字段级映射正确性，重点是值对象的拆装：
 * Money 平铺、EncryptedString 存密文、Fingerprint 仅存 hashValue（哨兵 salt 重建）。</p>
 */
class TransactionConverterTest {

    private static final String ENC_KEY = "0123456789abcdef0123456789abcdef"; // 32 字节 AES key

    // =========================================================================
    // toPO：领域 -> 持久化
    // =========================================================================

    @Test
    @DisplayName("toPO 应平铺 Money 为 amount+currency，EncryptedString 存密文，Fingerprint 仅存 hashValue")
    void shouldConvertDomainToPoWithAllFields() {
        Transaction tx = buildTransaction("ext-001", "fp-hash-001");

        TransactionPO po = TransactionConverter.toPO(tx);

        assertThat(po.getExternalId()).isEqualTo("ext-001");
        // Money 平铺
        assertThat(po.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(po.getCurrency()).isEqualTo("CNY");
        // EncryptedString 存密文（非明文）
        assertThat(po.getCounterpartyCipher()).isNotEqualTo("美团外卖");
        assertThat(po.getRemarkCipher()).isNotEqualTo("午餐");
        // Fingerprint 仅存 hashValue
        assertThat(po.getFingerprint()).isEqualTo("fp-hash-001");
        // 枚举名存储
        assertThat(po.getDirection()).isEqualTo(Direction.OUT);
        assertThat(po.getCategory()).isEqualTo(Category.UNCLASSIFIED);
        // 直映字段
        assertThat(po.getSourceSystem()).isEqualTo("ALIPAY");
        assertThat(po.getTransTime()).isNotNull();
        // 异常初始态
        assertThat(po.getAnomalyFlag()).isFalse();
        assertThat(po.getAnomalyScore()).isNull();
    }

    @Test
    @DisplayName("toPO 应处理 externalId 为 null 的情况（fingerprint 兜底）")
    void shouldHandleNullExternalId() {
        Transaction tx = buildTransaction(null, "fp-hash-002");

        TransactionPO po = TransactionConverter.toPO(tx);

        assertThat(po.getExternalId()).isNull();
        assertThat(po.getFingerprint()).isEqualTo("fp-hash-002");
    }

    @Test
    @DisplayName("toPO 应保留异常标记与评分")
    void shouldCarryAnomalyStateToPo() {
        Transaction tx = buildTransaction("ext-003", "fp-hash-003");
        tx.markAnomaly(new AnomalyScore(new BigDecimal("0.9"), "AMOUNT_SPIKE"));

        TransactionPO po = TransactionConverter.toPO(tx);

        assertThat(po.getAnomalyFlag()).isTrue();
        assertThat(po.getAnomalyScore()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    // =========================================================================
    // toDomain：持久化 -> 领域
    // =========================================================================

    @Test
    @DisplayName("toDomain 应用哨兵 salt 重建 Fingerprint，hashValue 对齐")
    void shouldConvertPoToDomainWithSentinelSalt() {
        TransactionPO po = buildPO("ext-001", "fp-hash-001");

        Transaction tx = TransactionConverter.toDomain(po);

        Fingerprint fp = tx.getFingerprint();
        assertThat(fp).isNotNull();
        assertThat(fp.hashValue()).isEqualTo("fp-hash-001");
        // salt 不入库，重建时用哨兵值
        assertThat(fp.salt()).isEqualTo(TransactionConverter.PERSISTED_SENTINEL_SALT);
    }

    @Test
    @DisplayName("toDomain 应还原 Money、Direction、Category、EncryptedString")
    void shouldRestoreMoneyAndEnumsFromPo() {
        TransactionPO po = buildPO("ext-001", "fp-hash-001");

        Transaction tx = TransactionConverter.toDomain(po);

        assertThat(tx.getMoney().amount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(tx.getMoney().currency()).isEqualTo("CNY");
        assertThat(tx.getDirection()).isEqualTo(Direction.OUT);
        assertThat(tx.getCategory()).isEqualTo(Category.UNCLASSIFIED);
        assertThat(tx.getExternalId()).isEqualTo("ext-001");
        assertThat(tx.getSourceSystem()).isEqualTo("ALIPAY");
    }

    @Test
    @DisplayName("toDomain 应还原异常标记与评分")
    void shouldRestoreAnomalyStateFromPo() {
        TransactionPO po = buildPO("ext-001", "fp-hash-001");
        po.setAnomalyFlag(true);
        po.setAnomalyScore(new BigDecimal("0.9"));

        Transaction tx = TransactionConverter.toDomain(po);

        assertThat(tx.isAnomalyFlag()).isTrue();
        assertThat(tx.getAnomalyScore()).isNotNull();
        assertThat(tx.getAnomalyScore().score()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    @Test
    @DisplayName("toDomain 当 fingerprint 为 null 时应返回 null 指纹（externalId 兜底场景）")
    void shouldHandleNullFingerprintWhenExternalIdPresent() {
        TransactionPO po = buildPO("ext-only-no-fp", null);

        Transaction tx = TransactionConverter.toDomain(po);

        // externalId 存在时 fingerprint 可为 null（聚合根不变量允许）
        assertThat(tx.getExternalId()).isEqualTo("ext-only-no-fp");
        assertThat(tx.getFingerprint()).isNull();
    }

    // =========================================================================
    // 往返一致性
    // =========================================================================

    @Test
    @DisplayName("toPO -> toDomain 往返：金额精度、枚举、externalId、fingerprint hashValue 不丢失")
    void shouldRoundTripWithoutLoss() {
        Transaction original = buildTransaction("ext-001", "fp-hash-001");
        original.markClassified(Category.FOOD, "RULE");

        Transaction roundTripped = TransactionConverter.toDomain(TransactionConverter.toPO(original));

        assertThat(roundTripped.getExternalId()).isEqualTo("ext-001");
        assertThat(roundTripped.getMoney().amount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(roundTripped.getMoney().currency()).isEqualTo("CNY");
        assertThat(roundTripped.getDirection()).isEqualTo(Direction.OUT);
        assertThat(roundTripped.getCategory()).isEqualTo(Category.FOOD);
        assertThat(roundTripped.getFingerprint().hashValue()).isEqualTo("fp-hash-001");
        assertThat(roundTripped.getSourceSystem()).isEqualTo("ALIPAY");
    }

    @Test
    @DisplayName("toPO -> toDomain 往返：金额精度边界（如 0.01）不丢失")
    void shouldRoundTripMoneyAmountPrecision() {
        Transaction original = Transaction.createFrom(
                "ext-precise", new Money(new BigDecimal("0.01"), "CNY"),
                Direction.OUT, Category.FOOD, LocalDateTime.now(),
                EncryptedString.fromPlain("x", ENC_KEY), EncryptedString.fromPlain("y", ENC_KEY),
                new Fingerprint("fp-precise", "salt"), "ALIPAY");

        Transaction roundTripped = TransactionConverter.toDomain(TransactionConverter.toPO(original));

        assertThat(roundTripped.getMoney().amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    // =========================================================================
    // 辅助构造
    // =========================================================================

    private Transaction buildTransaction(String externalId, String fpHash) {
        return Transaction.createFrom(
                externalId,
                new Money(new BigDecimal("100.50"), "CNY"),
                Direction.OUT,
                Category.UNCLASSIFIED,
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                EncryptedString.fromPlain("美团外卖", ENC_KEY),
                EncryptedString.fromPlain("午餐", ENC_KEY),
                new Fingerprint(fpHash, "salt"),
                "ALIPAY");
    }

    private TransactionPO buildPO(String externalId, String fpHash) {
        TransactionPO po = new TransactionPO();
        po.setId(1L);
        po.setExternalId(externalId);
        po.setAmount(new BigDecimal("100.50"));
        po.setCurrency("CNY");
        po.setDirection(Direction.OUT);
        po.setCategory(Category.UNCLASSIFIED);
        po.setTransTime(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        po.setCounterpartyCipher("cipher-counterparty");
        po.setRemarkCipher("cipher-remark");
        po.setFingerprint(fpHash);
        po.setSourceSystem("ALIPAY");
        po.setAnomalyFlag(false);
        po.setAnomalyScore(null);
        po.setVersion(0);
        return po;
    }
}
