# ADR-001: Money 值对象 + BigDecimal 精度决策

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 资金流水上下文（fundflow.domain.vo） |

## 背景

账务系统金额精度是安全底线。`float` / `double` 会产生二进制浮点误差（0.1 + 0.2 ≠ 0.3），`DECIMAL(10,0)` 精度不够。需要一种机制确保金额精度在编译期就被强制约束，而非运行时靠约定。

## 决策

**金额用 `Money` 不可变值对象（Java Record）+ `DECIMAL(18,2)`，禁止直接操作裸 `BigDecimal`。**

## 实现

```java
// src/main/java/com/finhub/fundflow/domain/vo/Money.java
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("金额不能为负数");
        if (amount.compareTo(BigDecimal.ZERO) == 0)
            throw new IllegalArgumentException("金额不能为零");
        if (currency.isBlank() || !currency.matches("[A-Z]{3}"))
            throw new IllegalArgumentException("币种必须为 3 位字母代码");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
    // add(), abs(), compareTo() — 返回新实例，保持不可变
}
```

## 后果

- Money 构造时自动 `setScale(2, HALF_UP)`，精度不可能被绕过
- `toString()` 脱敏输出 `Money{amount=***}`，日志不泄露金额
- `add()` / `abs()` / `compareTo()` 返回新实例，保持不可变
- 零金额被拒绝（业务决策：金额必须大于零）
- 币种格式强制 `[A-Z]{3}`（ISO 4217 三位大写字母）

## 替代方案

| 方案 | 拒绝原因 |
|------|---------|
| 原始 `BigDecimal` 散落在 Service 中 | 无法强制精度，3 个 Service 各写一遍 `setScale` |
| `long` 分为单位（如微信支付） | 除以 100 时精度丢失风险，与 MySQL `DECIMAL` 不一致 |

## 参考

- [Money.java](../../src/main/java/com/finhub/fundflow/domain/vo/Money.java)
- [MoneyTest.java](../../src/test/java/com/finhub/fundflow/domain/vo/MoneyTest.java)
- [V1__create_fin_transactions_table.sql](../../src/main/resources/db/migration/V1__create_fin_transactions_table.sql) — `amount DECIMAL(18,2)`