package com.finhub.fundflow.domain.vo;

import lombok.Getter;

/**
 * 交易分类值对象（枚举）。
 * 与 {@link Direction} 有业务一致性约束（由 {@link #isIncomeCompatible()} / {@link #isExpenseCompatible()} 表达）：
 * INCOME 仅允许 IN（真收入）；支出类（FOOD/TRANSPORT 等）允许 OUT（消费）与 IN（退款保留原分类）；TRANSFER 双向。
 */
@Getter
public enum Category {
    FOOD("餐饮"), TRANSPORT("交通"), SHOPPING("购物"), HOUSING("住房"), MEDICAL("医疗"),
    EDUCATION("教育"), ENTERTAINMENT("娱乐"), INCOME("收入"), SUBSCRIPTION("订阅"),
    INSURANCE("保险"), TRANSFER("转账"), UNCLASSIFIED("未分类");

    /**
     * -- GETTER --
     *  获取分类的中文显示名称。
     *
     * @return 中文显示名
     */
    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    /** 是否允许与 IN（收入）方向共存。
     *  所有分类均允许 IN：INCOME 是正常收入，支出类是退款（保留原消费分类）。 */
    public boolean isIncomeCompatible() {
        return true;
    }

    /** 是否允许与 OUT（支出）方向共存。
     *  仅 INCOME 不允许 OUT（工资等真收入不能是支出）。 */
    public boolean isExpenseCompatible() {
        return this != INCOME;
    }
}