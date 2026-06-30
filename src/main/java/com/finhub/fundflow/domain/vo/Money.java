package com.finhub.fundflow.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

/**
 * 金额值对象：不可变，强制精度 2 位小数（DECIMAL(18,2)）
 *
 * <p>领域不变量：
 * <ul>
 *   <li>amount 不能为 null，且 &gt; 0（业务决策：金额必须大于零）</li>
 *   <li>currency 为 ISO 4217 白名单内的三位字母代码</li>
 *   <li>构造时自动四舍五入到 2 位小数</li>
 * </ul>
 */
public record Money(BigDecimal amount, String currency) {

    /** ISO 4217 币种白名单，按需扩展 */
    private static final Set<String> VALID_CURRENCIES = Set.of(
        "CNY", "USD", "EUR", "JPY", "GBP", "HKD", "AUD", "CAD", "CHF", "SGD"
    );

    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("金额不能为零");
        }
        if (currency.isBlank() || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("币种必须为 3 位字母代码");
        }
        if (!VALID_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("不支持的币种: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 加法：返回新实例，保持不可变。币种不同时抛异常。 */
    public Money add(Money other) {
        Objects.requireNonNull(other, "加数不能为空");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("币种不一致，无法相加");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /** 绝对值：返回新实例，保持不可变 */
    public Money abs() {
        return new Money(this.amount.abs(), this.currency);
    }

    /** 比较金额大小。同币种比较，币种不同抛异常。 */
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "比较对象不能为空");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("币种不一致，无法比较");
        }
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return "Money{amount=***, currency='" + currency + "'}";
    }
}