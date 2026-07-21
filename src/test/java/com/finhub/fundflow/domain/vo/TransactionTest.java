package com.finhub.fundflow.domain.vo;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
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

    @Test
    @DisplayName("assignPersistedId 应回填 id 并丰富已注册的分类事件 transactionId")
    void shouldSetIdAndEnrichClassifiedEvent() {
        Transaction tx = Transaction.createFrom("ext-id-1", money("100"), Direction.OUT,
                Category.UNCLASSIFIED, LocalDateTime.now(), encrypted("美团"), encrypted("饭"),
                fingerprint("fp"), "ALIPAY");
        tx.markClassified(Category.FOOD, "RULE");   // 注册事件时 id 仍为 null
        assertThat(tx.getId()).isNull();

        tx.assignPersistedId(42L);

        assertThat(tx.getId()).isEqualTo(42L);
        assertThat(tx.getDomainEvents()).hasSize(1);
        Object event = tx.getDomainEvents().get(0);
        assertThat(event).isInstanceOf(TransactionClassifiedEvent.class);
        assertThat(((TransactionClassifiedEvent) event).transactionId()).isEqualTo(42L);
        assertThat(((TransactionClassifiedEvent) event).category()).isEqualTo(Category.FOOD);
    }

    @Test
    @DisplayName("assignPersistedId 应回填 id 并丰富已注册的异常事件 transactionId")
    void shouldSetIdAndEnrichAnomalyEvent() {
        Transaction tx = Transaction.createFrom("ext-id-2", money("99999"), Direction.OUT,
                Category.SHOPPING, LocalDateTime.now(), encrypted("商户"), encrypted("大额"),
                fingerprint("fp2"), "ALIPAY");
        tx.markAnomaly(new AnomalyScore(new BigDecimal("0.95"), "AMOUNT_SPIKE"));

        tx.assignPersistedId(7L);

        assertThat(tx.getId()).isEqualTo(7L);
        Object event = tx.getDomainEvents().get(0);
        assertThat(event).isInstanceOf(AnomalyDetectedEvent.class);
        assertThat(((AnomalyDetectedEvent) event).transactionId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("assignPersistedId 重复调用应抛 IllegalStateException（防重复赋值）")
    void shouldThrowWhenIdAlreadyAssigned() {
        Transaction tx = Transaction.createFrom("ext-id-3", money("100"), Direction.OUT,
                Category.FOOD, LocalDateTime.now(), encrypted("测试"), encrypted("测试"),
                fingerprint("fp"), "ALIPAY");
        tx.assignPersistedId(1L);
        assertThatIllegalStateException()
                .isThrownBy(() -> tx.assignPersistedId(2L))
                .withMessageContaining("id 已回填");
    }

    @Test
    @DisplayName("assignPersistedId 为 null 应抛 NullPointerException")
    void shouldRejectNullId() {
        Transaction tx = Transaction.createFrom("ext-id-4", money("100"), Direction.OUT,
                Category.FOOD, LocalDateTime.now(), encrypted("测试"), encrypted("测试"),
                fingerprint("fp"), "ALIPAY");
        assertThatNullPointerException()
                .isThrownBy(() -> tx.assignPersistedId(null));
    }
}
