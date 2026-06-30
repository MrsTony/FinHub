package com.finhub.fundflow.domain.vo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
/**
 * Money 值对象测试契约
 * 验证不可变性、精度强制、校验规则、安全脱敏。
 * 所有 Money 实现必须通过这些测试。
 */
public class MoneyTest {
    // =========================================================================
    // 构造器校验
    // =========================================================================

    @Test
    @DisplayName("应创建有效金额：正常构造")
    void shouldCreateValidMoney() {
        Money money = new Money(new BigDecimal("100.50"), "CNY");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(money.currency()).isEqualTo("CNY");
    }

    @Test
    @DisplayName("应拒绝 null 金额")
    void shouldRejectNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(null, "CNY"))
                .withMessageContaining("金额");
    }

    @Test
    @DisplayName("应拒绝 null 币种")
    void shouldRejectNullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(new BigDecimal("100"), null))
                .withMessageContaining("币种");
    }

    @ParameterizedTest
    @DisplayName("应拒绝负金额")
    @ValueSource(strings = {"-0.01", "-1", "-999999.99"})
    void shouldRejectNegativeAmount(String negativeAmount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal(negativeAmount), "CNY"))
                .withMessageContaining("负数");
    }

    @Test
    @DisplayName("应拒绝零金额（业务决策：金额必须大于零）")
    void shouldRejectZeroAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.ZERO, "CNY"))
                .withMessageContaining("零");
    }

    @ParameterizedTest
    @DisplayName("应拒绝非法币种格式")
    @ValueSource(strings = {"人民币", "RMB", "US", "US Dollar", "123"})
    void shouldRejectInvalidCurrency(String invalidCurrency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("100"), invalidCurrency))
                .withMessageContaining("币种");
    }

    // =========================================================================
    // 精度强制（ADR-001 核心不变量）
    // =========================================================================

    @ParameterizedTest
    @DisplayName("应强制精度为 2 位小数（四舍五入）")
    @CsvSource({
            "100.5,   100.50",    // 1位 → 补零
            "100,     100.00",    // 整数 → 补零
            "100.555, 100.56",    // 3位 → 四舍五入
            "100.554, 100.55",    // 3位 → 舍去
            "100.999, 101.00",    // 进位
            "0.005,   0.01"       // 小金额进位
    })
    void shouldForceTwoDecimalScale(String input, String expected) {
        Money money = new Money(new BigDecimal(input), "CNY");

        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("高精度输入应正确截断")
    void shouldHandleHighPrecisionInput() {
        Money money = new Money(new BigDecimal("123.456789"), "CNY");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("123.46"));
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    // =========================================================================
    // 加法运算（不可变性验证）
    // =========================================================================

    @Test
    @DisplayName("加法应返回新实例，不修改原对象")
    void shouldReturnNewInstanceWhenAdding() {
        Money m1 = new Money(new BigDecimal("100.00"), "CNY");
        Money m2 = new Money(new BigDecimal("50.50"), "CNY");

        Money result = m1.add(m2);

        // 结果正确
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150.50"));
        // 原对象未被修改
        assertThat(m1.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(m2.amount()).isEqualByComparingTo(new BigDecimal("50.50"));
        // 新实例
        assertThat(result).isNotSameAs(m1);
        assertThat(result).isNotSameAs(m2);
    }

    @Test
    @DisplayName("加法应拒绝不同币种")
    void shouldRejectAddingDifferentCurrency() {
        Money cny = new Money(new BigDecimal("100"), "CNY");
        Money usd = new Money(new BigDecimal("50"), "USD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cny.add(usd))
                .withMessageContaining("币种");
    }

    @Test
    @DisplayName("加法应处理精度不一致的输入")
    void shouldHandleDifferentScalesWhenAdding() {
        Money m1 = new Money(new BigDecimal("100.5"), "CNY");   // 内部已强制为 100.50
        Money m2 = new Money(new BigDecimal("50.25"), "CNY");

        Money result = m1.add(m2);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("连加应累积正确")
    void shouldAccumulateMultipleAdditions() {
        Money base = new Money(new BigDecimal("100.00"), "CNY");
        Money add1 = new Money(new BigDecimal("50.00"), "CNY");
        Money add2 = new Money(new BigDecimal("25.50"), "CNY");

        Money result = base.add(add1).add(add2);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("175.50"));
        // 每个中间结果都是新实例
        assertThat(base.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // =========================================================================
    // 绝对值运算
    // =========================================================================

    @Test
    @DisplayName("绝对值应返回正数新实例")
    void shouldReturnAbsForPositive() {
        Money positive = new Money(new BigDecimal("100.50"), "CNY");

        Money result = positive.abs();

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(result).isNotSameAs(positive);
    }

    @Test
    @DisplayName("绝对值对正数无影响（已校验构造时非负）")
    void shouldReturnSameValueForAbs() {
        // 构造器已保证非负，abs 主要是 API 对称性和防御性编程
        Money money = new Money(new BigDecimal("0.01"), "CNY");

        Money result = money.abs();

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    // =========================================================================
    // 比较运算
    // =========================================================================

    @Test
    @DisplayName("比较应拒绝不同币种")
    void shouldRejectComparingDifferentCurrency() {
        Money cny = new Money(new BigDecimal("100"), "CNY");
        Money usd = new Money(new BigDecimal("50"), "USD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cny.compareTo(usd))
                .withMessageContaining("币种");
    }

    @ParameterizedTest
    @DisplayName("同币种比较应正确")
    @CsvSource({
            "100.00, 50.00,  1",   // 大于
            "50.00,  100.00, -1",  // 小于
            "100.00, 100.00, 0"    // 等于
    })
    void shouldCompareSameCurrency(String a, String b, int expected) {
        Money m1 = new Money(new BigDecimal(a), "CNY");
        Money m2 = new Money(new BigDecimal(b), "CNY");

        assertThat(m1.compareTo(m2)).isEqualTo(expected);
    }

    @Test
    @DisplayName("相等判断应基于金额和币种")
    void shouldEqualBasedOnAmountAndCurrency() {
        Money m1 = new Money(new BigDecimal("100.00"), "CNY");
        Money m2 = new Money(new BigDecimal("100.00"), "CNY");
        Money m3 = new Money(new BigDecimal("100.00"), "USD");
        Money m4 = new Money(new BigDecimal("100.50"), "CNY");

        // Record 自动实现 equals：金额和币种都相同
        assertThat(m1).isEqualTo(m2);
        assertThat(m1).isNotEqualTo(m3);  // 币种不同
        assertThat(m1).isNotEqualTo(m4);  // 金额不同
    }

    @Test
    @DisplayName("hashCode 应一致（用于集合操作）")
    void shouldHaveConsistentHashCode() {
        Money m1 = new Money(new BigDecimal("100.00"), "CNY");
        Money m2 = new Money(new BigDecimal("100.00"), "CNY");

        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    // =========================================================================
    // 安全脱敏（ADR-005 安全基线）
    // =========================================================================

    @Test
    @DisplayName("toString 不应暴露金额数值")
    void shouldNotExposeAmountInToString() {
        Money money = new Money(new BigDecimal("99999.99"), "CNY");

        String str = money.toString();

        assertThat(str).doesNotContain("99999.99");
        assertThat(str).doesNotContain("99999");
        assertThat(str).contains("***");           // 脱敏标记
        assertThat(str).contains("CNY");            // 币种可暴露
    }

    @Test
    @DisplayName("toString 不应暴露小额金额")
    void shouldNotExposeSmallAmountInToString() {
        Money money = new Money(new BigDecimal("0.01"), "CNY");

        assertThat(money.toString()).doesNotContain("0.01");
    }

    // =========================================================================
    // 边界场景
    // =========================================================================

    @Test
    @DisplayName("应处理极大金额（个人资金场景上限）")
    void shouldHandleLargeAmount() {
        // 18 位总长，2 位小数 → 整数部分 16 位
        Money large = new Money(new BigDecimal("9999999999999999.99"), "CNY");

        assertThat(large.amount()).isEqualByComparingTo(new BigDecimal("9999999999999999.99"));
    }

    @Test
    @DisplayName("极小金额应正确精度处理")
    void shouldHandleTinyAmount() {
        Money tiny = new Money(new BigDecimal("0.005"), "CNY");

        // 0.005 → 四舍五入 → 0.01
        assertThat(tiny.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("科学计数法输入应正确处理")
    void shouldHandleScientificNotation() {
        Money scientific = new Money(new BigDecimal("1E+3"), "CNY");

        assertThat(scientific.amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // =========================================================================
    // 常见错误模式（防御性测试）
    // =========================================================================

    @Test
    @DisplayName("应拒绝 NaN 金额")
    void shouldRejectNaNAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("NaN"), "CNY"));
    }

    @Test
    @DisplayName("应拒绝 Infinity 金额")
    void shouldRejectInfinityAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("Infinity"), "CNY"));
    }

    @Test
    @DisplayName("应拒绝空白币种")
    void shouldRejectBlankCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("100"), "  "))
                .withMessageContaining("3 位");
    }

    @Test
    @DisplayName("加法 null 参数应抛异常")
    void shouldRejectNullAddend() {
        Money money = new Money(new BigDecimal("100"), "CNY");

        assertThatNullPointerException()
                .isThrownBy(() -> money.add(null));
    }
}
