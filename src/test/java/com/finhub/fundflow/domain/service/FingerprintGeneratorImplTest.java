package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import com.finhub.fundflow.infrastructure.service.FingerprintGeneratorImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class FingerprintGeneratorImplTest {

    private final FingerprintGenerator generator = new FingerprintGeneratorImpl();

    @Test
    @DisplayName("相同输入相同盐值应生成相同指纹")
    void shouldGenerateSameFingerprintForSameInput() {
        Fingerprint fp1 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");

        assertThat(fp1.hashValue()).isEqualTo(fp2.hashValue());
    }

    @Test
    @DisplayName("不同盐值应生成不同指纹")
    void shouldGenerateDifferentFingerprintForDifferentSalt() {
        Fingerprint fp1 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt-a");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt-b");

        assertThat(fp1.hashValue()).isNotEqualTo(fp2.hashValue());
    }

    @Test
    @DisplayName("时间秒级差异应被截断到分钟")
    void shouldTruncateTimeToMinutes() {
        Fingerprint fp1 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30, 15), "午餐", "salt");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30, 45), "午餐", "salt");

        assertThat(fp1.hashValue()).isEqualTo(fp2.hashValue());
    }

    @Test
    @DisplayName("金额精度差异应被统一")
    void shouldNormalizeAmountPrecision() {
        Fingerprint fp1 = generator.generate("美团", new Money(new BigDecimal("100.5"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.50"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");

        assertThat(fp1.hashValue()).isEqualTo(fp2.hashValue());
    }

    @Test
    @DisplayName("对方户名应标准化（去除空格、转小写）")
    void shouldNormalizeCounterparty() {
        Fingerprint fp1 = generator.generate(" 美团 ", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "午餐", "salt");

        assertThat(fp1.hashValue()).isEqualTo(fp2.hashValue());
    }

    @Test
    @DisplayName("备注 null 和 blank 应生成相同占位")
    void shouldHandleNullAndBlankRemark() {
        Fingerprint fp1 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), null, "salt");
        Fingerprint fp2 = generator.generate("美团", new Money(new BigDecimal("100.00"), "CNY"),
                LocalDateTime.of(2024, 1, 1, 12, 30), "  ", "salt");

        assertThat(fp1.hashValue()).isEqualTo(fp2.hashValue());
    }
}
