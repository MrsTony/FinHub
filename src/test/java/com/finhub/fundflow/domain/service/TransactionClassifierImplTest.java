package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.*;
import com.finhub.fundflow.infrastructure.service.TransactionClassifierImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TransactionClassifierImplTest {
    private final TransactionClassifier classifier = new TransactionClassifierImpl();

    // 辅助方法
    private Money money(String amount) {
        return new Money(new BigDecimal(amount), "CNY");
    }

    private EncryptedString encrypted(String plain) {
        return EncryptedString.fromPlain(plain, "12345678901234567890123456789012");
    }

    private Transaction createTx(String counterparty, Direction direction) {
        return Transaction.createFrom(
                "ext-001",
                money("100.00"),
                direction,
                Category.UNCLASSIFIED,
                LocalDateTime.now(),
                encrypted(counterparty),
                encrypted("测试"),
                new Fingerprint("abc", "salt"),
                "ALIPAY"
        );
    }

    @Test
    @DisplayName("美团商户应分类为 FOOD")
    void shouldClassifyMeituanAsFood() {
        Transaction tx = createTx("美团外卖", Direction.OUT);
        CategorySuggestion suggestion = classifier.classify(tx);

        assertThat(suggestion.category()).isEqualTo(Category.FOOD);
        assertThat(suggestion.confidence()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(suggestion.source()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("滴滴商户应分类为 TRANSPORT")
    void shouldClassifyDidiAsTransport() {
        Transaction tx = createTx("滴滴出行", Direction.OUT);
        CategorySuggestion suggestion = classifier.classify(tx);

        assertThat(suggestion.category()).isEqualTo(Category.TRANSPORT);
    }

    @Test
    @DisplayName("未知商户应返回 UNCLASSIFIED")
    void shouldReturnUnclassifiedForUnknownMerchant() {
        Transaction tx = createTx("未知商户", Direction.OUT);
        CategorySuggestion suggestion = classifier.classify(tx);

        assertThat(suggestion.category()).isEqualTo(Category.UNCLASSIFIED);
        assertThat(suggestion.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("INCOME 类别但方向为 OUT 应返回 UNCLASSIFIED")
    void shouldRejectIncomeCategoryWithOutDirection() {
        Transaction tx = createTx("工资", Direction.OUT);
        CategorySuggestion suggestion = classifier.classify(tx);

        assertThat(suggestion.category()).isEqualTo(Category.UNCLASSIFIED);
    }
}
