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
    @DisplayName("支出类别应仅与 OUT 方向兼容")
    void shouldBeExpenseCompatibleOnlyForExpenseCategories() {
        assertThat(Category.FOOD.isExpenseCompatible()).isTrue();
        assertThat(Category.TRANSPORT.isExpenseCompatible()).isTrue();
        assertThat(Category.SHOPPING.isExpenseCompatible()).isTrue();
        assertThat(Category.HOUSING.isExpenseCompatible()).isTrue();
        assertThat(Category.MEDICAL.isExpenseCompatible()).isTrue();
        assertThat(Category.EDUCATION.isExpenseCompatible()).isTrue();
        assertThat(Category.ENTERTAINMENT.isExpenseCompatible()).isTrue();
        assertThat(Category.SUBSCRIPTION.isExpenseCompatible()).isTrue();

        assertThat(Category.FOOD.isIncomeCompatible()).isFalse();
        assertThat(Category.TRANSPORT.isIncomeCompatible()).isFalse();
        assertThat(Category.SHOPPING.isIncomeCompatible()).isFalse();
    }

    @Test
    @DisplayName("UNCLASSIFIED 应与 IN 和 OUT 均兼容")
    void shouldBeCompatibleWithBothDirectionsForUnclassified() {
        assertThat(Category.UNCLASSIFIED.isIncomeCompatible()).isTrue();
        assertThat(Category.UNCLASSIFIED.isExpenseCompatible()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("所有支出类别应拒绝 IN 方向")
    @EnumSource(names = {"FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "MEDICAL", "EDUCATION", "ENTERTAINMENT", "SUBSCRIPTION"})
    void shouldRejectIncomeDirectionForExpenseCategories(Category expenseCategory) {
        assertThat(expenseCategory.isIncomeCompatible()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("所有类别应至少与一个方向兼容")
    @EnumSource(value = Category.class)
    void shouldBeCompatibleWithAtLeastOneDirection(Category category) {
        boolean compatibleWithAny = category.isIncomeCompatible() || category.isExpenseCompatible();
        assertThat(compatibleWithAny).isTrue();
    }
}
