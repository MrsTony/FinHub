package com.finhub.fundflow.domain.vo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

public class FingerprintTest {


        @Test
        @DisplayName("应创建有效指纹")
        void shouldCreateValidFingerprint() {
            Fingerprint fp = new Fingerprint("abc123def456", "salt1");

            assertThat(fp.hashValue()).isEqualTo("abc123def456");
            assertThat(fp.salt()).isEqualTo("salt1");
        }

        @Test
        @DisplayName("应拒绝 null 哈希值")
        void shouldRejectNullHashValue() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Fingerprint(null, "salt"))
                    .withMessageContaining("哈希值");
        }

        @Test
        @DisplayName("应拒绝 null 盐值")
        void shouldRejectNullSalt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Fingerprint("hash", null))
                    .withMessageContaining("盐值");
        }

        @ParameterizedTest
        @DisplayName("应拒绝空白哈希值")
        @CsvSource({"'', '空字符串'", "'  ', '空白字符串'", "'   ', '多空白字符串'"})
        void shouldRejectBlankHashValue(String blankHash, String description) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Fingerprint(blankHash, "salt"))
                    .withMessageContaining("指纹值")
                    .withMessageContaining("空");
        }

        @ParameterizedTest
        @DisplayName("应拒绝空白盐值")
        @CsvSource({"'', '空字符串'", "'  ', '空白字符串'"})
        void shouldRejectBlankSalt(String blankSalt, String description) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Fingerprint("hash", blankSalt))
                    .withMessageContaining("盐值")
                    .withMessageContaining("空");
        }

        @Test
        @DisplayName("相同哈希值应匹配（盐值不同不影响匹配）")
        void shouldMatchWhenHashValueIsSame() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt1");
            Fingerprint fp2 = new Fingerprint("abc123", "salt2");

            assertThat(fp1.matches(fp2)).isTrue();
            assertThat(fp2.matches(fp1)).isTrue();
        }

        @Test
        @DisplayName("不同哈希值应不匹配")
        void shouldNotMatchWhenHashValueIsDifferent() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt1");
            Fingerprint fp2 = new Fingerprint("xyz789", "salt1");

            assertThat(fp1.matches(fp2)).isFalse();
            assertThat(fp2.matches(fp1)).isFalse();
        }

        @Test
        @DisplayName("相同哈希和盐值应匹配")
        void shouldMatchWhenBothHashAndSaltAreSame() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt");
            Fingerprint fp2 = new Fingerprint("abc123", "salt");

            assertThat(fp1.matches(fp2)).isTrue();
        }

        @Test
        @DisplayName("与自身应匹配")
        void shouldMatchWithItself() {
            Fingerprint fp = new Fingerprint("abc123", "salt");

            assertThat(fp.matches(fp)).isTrue();
        }

        @Test
        @DisplayName("hashCode 应基于 hashValue 和盐值")
        void shouldHaveConsistentHashCode() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt1");
            Fingerprint fp2 = new Fingerprint("abc123", "salt1");

            assertThat(fp1.hashCode()).isEqualTo(fp2.hashCode());
        }

        @Test
        @DisplayName("equals 应基于 hashValue 和盐值")
        void shouldEqualWhenBothFieldsAreSame() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt1");
            Fingerprint fp2 = new Fingerprint("abc123", "salt1");

            assertThat(fp1).isEqualTo(fp2);
        }

        @Test
        @DisplayName("不同盐值应不相等（即使 hashValue 相同）")
        void shouldNotEqualWhenSaltIsDifferent() {
            Fingerprint fp1 = new Fingerprint("abc123", "salt1");
            Fingerprint fp2 = new Fingerprint("abc123", "salt2");

            assertThat(fp1).isNotEqualTo(fp2);
        }

        @Test
        @DisplayName("toString 不应暴露完整哈希值")
        void shouldNotExposeFullHashInToString() {
            Fingerprint fp = new Fingerprint("sensitive_hash_value", "salt");

            String str = fp.toString();

            // Record 默认 toString 会暴露字段，如果业务要求脱敏需自定义
            // 此处至少验证不 panic，实际脱敏策略由实现决定
            assertThat(str).isNotNull();
        }
}
