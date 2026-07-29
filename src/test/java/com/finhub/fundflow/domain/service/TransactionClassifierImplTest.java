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
        return createTx(counterparty, "测试", direction);
    }

    private Transaction createTx(String counterparty, String remark, Direction direction) {
        return Transaction.createFrom(
                "ext-001",
                money("100.00"),
                direction,
                Category.UNCLASSIFIED,
                LocalDateTime.now(),
                encrypted(counterparty),
                encrypted(remark),
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

    @Test
    @DisplayName("哈啰出行应分类为 TRANSPORT")
    void shouldClassifyHelloBikeAsTransport() {
        Transaction tx = createTx("哈啰出行", Direction.OUT);
        assertThat(classifier.classify(tx).category()).isEqualTo(Category.TRANSPORT);
    }

    @Test
    @DisplayName("铁路12306 应分类为 TRANSPORT")
    void shouldClassify12306AsTransport() {
        Transaction tx = createTx("铁路12306", Direction.OUT);
        assertThat(classifier.classify(tx).category()).isEqualTo(Category.TRANSPORT);
    }

    @Test
    @DisplayName("铁路12306 退款(IN)应保留 TRANSPORT 分类（退款不降级为 UNCLASSIFIED）")
    void shouldKeepTransportFor12306Refund() {
        CategorySuggestion suggestion = classifier.classify(createTx("铁路12306", Direction.IN));
        assertThat(suggestion.category()).isEqualTo(Category.TRANSPORT);
        assertThat(suggestion.confidence()).isEqualByComparingTo(new BigDecimal("1.0"));
    }

    @Test
    @DisplayName("蜜雪冰城应分类为 FOOD")
    void shouldClassifyMixueAsFood() {
        assertThat(classifier.classify(createTx("蜜雪冰城", Direction.OUT)).category()).isEqualTo(Category.FOOD);
    }

    @Test
    @DisplayName("众安保险应分类为 INSURANCE")
    void shouldClassifyZhongAnAsInsurance() {
        assertThat(classifier.classify(createTx("众安在线财产保险股份有限公司", Direction.OUT)).category())
                .isEqualTo(Category.INSURANCE);
    }

    @Test
    @DisplayName("众安保险退款(IN)应保留 INSURANCE 分类")
    void shouldKeepInsuranceForRefund() {
        assertThat(classifier.classify(createTx("众安保险", Direction.IN)).category())
                .isEqualTo(Category.INSURANCE);
    }

    @Test
    @DisplayName("金山WPS应分类为 SUBSCRIPTION")
    void shouldClassifyWpsAsSubscription() {
        assertThat(classifier.classify(createTx("金山WPS", Direction.OUT)).category())
                .isEqualTo(Category.SUBSCRIPTION);
    }

    @Test
    @DisplayName("燃气集团应分类为 HOUSING")
    void shouldClassifyGasAsHousing() {
        assertThat(classifier.classify(createTx("北京市燃气集团有限责任公司", Direction.OUT)).category())
                .isEqualTo(Category.HOUSING);
    }

    @Test
    @DisplayName("大药房应分类为 MEDICAL")
    void shouldClassifyPharmacyAsMedical() {
        assertThat(classifier.classify(createTx("北京高济百康大药房有限公司将台路分店", Direction.OUT)).category())
                .isEqualTo(Category.MEDICAL);
    }

    @Test
    @DisplayName("盒马应分类为 SHOPPING")
    void shouldClassifyHemaAsShopping() {
        assertThat(classifier.classify(createTx("上海盒马网络科技有限公司", Direction.OUT)).category())
                .isEqualTo(Category.SHOPPING);
    }

    @Test
    @DisplayName("商户名未命中时备注含关键词应命中分类")
    void shouldClassifyByRemarkWhenCounterpartyMisses() {
        CategorySuggestion suggestion = classifier.classify(createTx("某未知商户", "美团外卖订单", Direction.OUT));
        assertThat(suggestion.category()).isEqualTo(Category.FOOD);
    }
}
