package com.finhub.fundflow.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额值对象：不可变，强制精度 2 位小数（DECIMAL(18,2)）
 *
 * <p>领域不变量：
 * <ul>
 *   <li>amount 不能为 null，且 &ge; 0</li>
 *   <li>currency 为 ISO 4217 三位字母代码</li>
 *   <li>构造时自动四舍五入到 2 位小数</li>
 * </ul>
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("币种必须为 3 位字母代码");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 加法：返回新实例，保持不可变。币种不同时抛异常。 */
    public Money add(Money other) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 绝对值 */
    public Money abs() {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 比较金额大小。同币种比较，币种不同抛异常。 */
    public int compareTo(Money other) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public String toString() {
        return "Money{amount=***, currency='" + currency + "'}";
    }
}