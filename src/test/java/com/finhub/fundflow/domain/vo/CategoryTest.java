package com.finhub.fundflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

public class CategoryTest {

    @Test
    @DisplayName("INCOME 应仅与 IN 方向兼容")
    void shouldBeIncomeCompatibleOnlyForIncomeCategory() {
        assertThat(Category.INCOME.isIncomeCompatible()).isTrue();
        assertThat(Category.INCOME.isExpenseCompatible()).isFalse();
    }

    @Test
    @DisplayName("支出类别应与 OUT(消费) 和 IN(退款) 方向均兼容")
    void shouldBeCompatibleWithBothDirectionsForExpenseCategories() {
        assertThat(Category.FOOD.isExpenseCompatible()).isTrue();
        assertThat(Category.TRANSPORT.isExpenseCompatible()).isTrue();
        assertThat(Category.SHOPPING.isExpenseCompatible()).isTrue();
        assertThat(Category.HOUSING.isExpenseCompatible()).isTrue();
        assertThat(Category.MEDICAL.isExpenseCompatible()).isTrue();
        assertThat(Category.EDUCATION.isExpenseCompatible()).isTrue();
        assertThat(Category.ENTERTAINMENT.isExpenseCompatible()).isTrue();
        assertThat(Category.SUBSCRIPTION.isExpenseCompatible()).isTrue();

        // 支出类允许 IN 方向（退款保留原消费分类）
        assertThat(Category.FOOD.isIncomeCompatible()).isTrue();
        assertThat(Category.TRANSPORT.isIncomeCompatible()).isTrue();
        assertThat(Category.SHOPPING.isIncomeCompatible()).isTrue();
    }

    @Test
    @DisplayName("UNCLASSIFIED 应与 IN 和 OUT 均兼容")
    void shouldBeCompatibleWithBothDirectionsForUnclassified() {
        assertThat(Category.UNCLASSIFIED.isIncomeCompatible()).isTrue();
        assertThat(Category.UNCLASSIFIED.isExpenseCompatible()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("所有支出类别应允许 IN 方向（退款保留原消费分类）")
    @EnumSource(names = {"FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "MEDICAL", "EDUCATION", "ENTERTAINMENT", "SUBSCRIPTION"})
    void shouldAllowIncomeDirectionForExpenseCategories(Category expenseCategory) {
        assertThat(expenseCategory.isIncomeCompatible()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("所有类别应至少与一个方向兼容")
    @EnumSource(value = Category.class)
    void shouldBeCompatibleWithAtLeastOneDirection(Category category) {
        boolean compatibleWithAny = category.isIncomeCompatible() || category.isExpenseCompatible();
        assertThat(compatibleWithAny).isTrue();
    }

    @Test
    @DisplayName("TRANSFER 和 INSURANCE 应存在且与 IN/OUT 双向兼容（转账/保险退款）")
    void shouldExistAndBeBidirectionalForTransferAndInsurance() {
        // TRANSFER：转账双向（转出 OUT / 转入或退回 IN）
        Category transfer = Category.valueOf("TRANSFER");
        assertThat(transfer.isIncomeCompatible()).isTrue();
        assertThat(transfer.isExpenseCompatible()).isTrue();

        // INSURANCE：保险支出 OUT，退款 IN 保留原分类
        Category insurance = Category.valueOf("INSURANCE");
        assertThat(insurance.isIncomeCompatible()).isTrue();
        assertThat(insurance.isExpenseCompatible()).isTrue();
    }
}
