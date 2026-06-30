package com.finhub.fundflow.domain.vo;

/**
 * 交易分类值对象（枚举）。
 * 与 {@link Direction} 有业务一致性约束：
 * INCOME 只能对应 IN 方向，FOOD/TRANSPORT 等只能对应 OUT 方向。
 */
public enum Category {
    FOOD, TRANSPORT, SHOPPING, HOUSING, MEDICAL,
    EDUCATION, ENTERTAINMENT, INCOME, SUBSCRIPTION, UNCLASSIFIED;

    /** 是否允许与 IN（收入）方向共存 */
    public boolean isIncomeCompatible() {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 是否允许与 OUT（支出）方向共存 */
    public boolean isExpenseCompatible() {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}