package com.finhub.fundflow.domain.vo;

import com.finhub.fundflow.domain.aggregate.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;


public class TransactionTest {
    // 准确的 32 字节密钥（AES-256 要求）
    private static final String VALID_KEY = "key-32-bytes-long-for-test-use!!";

    // 辅助方法：创建 Money
    private Money money(String amount) {
        return new Money(new BigDecimal(amount), "CNY");
    }

    // 辅助方法：创建加密字符串
    private EncryptedString encrypted(String plain) {
        return EncryptedString.fromPlain(plain, VALID_KEY);
    }

    // 辅助方法：创建指纹
    private Fingerprint fingerprint(String hash) {
        return new Fingerprint(hash, "salt");
    }

    @Test
    @DisplayName("应使用有效数据创建交易")
    void shouldCreateTransactionWithValidData() {
        Transaction tx = Transaction.createFrom(
                "ext-001",
                money("100.00"),
                Direction.OUT,
                Category.FOOD,
                LocalDateTime.now(),
                encrypted("麦当劳"),
                encrypted("午餐"),
                fingerprint("abc"),
                "ALIPAY"
        );

        assertThat(tx).isNotNull();
        assertThat(tx.getCategory()).isEqualTo(Category.FOOD);
        assertThat(tx.getDirection()).isEqualTo(Direction.OUT);
        assertThat(tx.isAnomalyFlag()).isFalse();
    }

    @Test
    @DisplayName("应拒绝分类与方向不兼容的交易")
    void shouldRejectIncompatibleCategoryAndDirection() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Transaction.createFrom(
                        "ext-002",
                        money("5000"),
                        Direction.OUT,
                        Category.INCOME,
                        LocalDateTime.now(),
                        encrypted("公司"),
                        encrypted("工资"),
                        fingerprint("fp"),
                        "ALIPAY"
                ))
                .withMessageContaining("不兼容");
    }

    @Test
    @DisplayName("externalId 缺失时必须提供 fingerprint")
    void shouldRequireFingerprintWhenExternalIdIsNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Transaction.createFrom(
                        null,
                        money("100"),
                        Direction.OUT,
                        Category.UNCLASSIFIED,
                        LocalDateTime.now(),
                        encrypted("测试"),
                        encrypted("测试"),
                        null, // fingerprint 为 null
                        "ALIPAY"
                ))
                .withMessageContaining("fingerprint");
    }

    @Test
    @DisplayName("应拒绝 null 金额")
    void shouldRejectNullMoney() {
        assertThatNullPointerException()
                .isThrownBy(() -> Transaction.createFrom(
                        "ext-003",
                        null,
                        Direction.OUT,
                        Category.FOOD,
                        LocalDateTime.now(),
                        encrypted("测试"),
                        encrypted("测试"),
                        fingerprint("fp"),
                        "ALIPAY"
                ));
    }

    @Test
    @DisplayName("应成功标记分类")
    void shouldMarkClassified() {
        Transaction tx = Transaction.createFrom(
                "ext-004",
                money("100"),
                Direction.OUT,
                Category.UNCLASSIFIED,
                LocalDateTime.now(),
                encrypted("美团"),
                encrypted("外卖"),
                fingerprint("fp"),
                "ALIPAY"
        );

        tx.markClassified(Category.FOOD, "RULE");

        assertThat(tx.getCategory()).isEqualTo(Category.FOOD);
    }

    @Test
    @DisplayName("标记不兼容分类时应拒绝")
    void shouldRejectMarkClassifiedWithIncompatibleDirection() {
        Transaction tx = Transaction.createFrom(
                "ext-005",
                money("100"),
                Direction.OUT,
                Category.FOOD,
                LocalDateTime.now(),
                encrypted("测试"),
                encrypted("测试"),
                fingerprint("fp"),
                "ALIPAY"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> tx.markClassified(Category.INCOME, "AI"))
                .withMessageContaining("不兼容");
    }

    @Test
    @DisplayName("应成功标记异常")
    void shouldMarkAnomaly() {
        Transaction tx = Transaction.createFrom(
                "ext-006",
                money("99999"),
                Direction.OUT,
                Category.SHOPPING,
                LocalDateTime.now(),
                encrypted("某商户"),
                encrypted("大额"),
                fingerprint("fp2"),
                "ALIPAY"
        );

        tx.markAnomaly(new AnomalyScore(new BigDecimal("0.95"), "AMOUNT_SPIKE"));

        assertThat(tx.isAnomalyFlag()).isTrue();
        assertThat(tx.getAnomalyScore().score()).isEqualByComparingTo(new BigDecimal("0.95"));
    }

    @Test
    @DisplayName("标记 null 异常时应拒绝")
    void shouldRejectMarkAnomalyWithNullScore() {
        Transaction tx = Transaction.createFrom(
                "ext-007",
                money("100"),
                Direction.OUT,
                Category.FOOD,
                LocalDateTime.now(),
                encrypted("测试"),
                encrypted("测试"),
                fingerprint("fp"),
                "ALIPAY"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> tx.markAnomaly(null));
    }
}
