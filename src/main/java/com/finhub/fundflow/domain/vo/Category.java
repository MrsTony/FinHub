package com.finhub.fundflow.domain.vo;

import lombok.Getter;

/**
 * 交易分类值对象（枚举）。
 * 与 {@link Direction} 有业务一致性约束：
 * INCOME 只能对应 IN 方向，FOOD/TRANSPORT 等只能对应 OUT 方向。
 */
@Getter
public enum Category {
    FOOD("餐饮"), TRANSPORT("交通"), SHOPPING("购物"), HOUSING("住房"), MEDICAL("医疗"),
    EDUCATION("教育"), ENTERTAINMENT("娱乐"), INCOME("收入"), SUBSCRIPTION("订阅"), UNCLASSIFIED("未分类");

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

    /** 是否允许与 IN（收入）方向共存 */
    public boolean isIncomeCompatible() {
        return this == INCOME || this == UNCLASSIFIED;
    }

    /** 是否允许与 OUT（支出）方向共存 */
    public boolean isExpenseCompatible() {
        return this != INCOME || this == UNCLASSIFIED;
    }
}