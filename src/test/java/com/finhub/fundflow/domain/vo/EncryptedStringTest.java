package com.finhub.fundflow.domain.vo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;


public class EncryptedStringTest {
    private static final String VALID_KEY ="key-32-bytes-long-for-test-use!!";

    @Test
    @DisplayName("应加密明文并生成密文")
    void shouldEncryptPlainText() {
        EncryptedString encrypted = EncryptedString.fromPlain("麦当劳", VALID_KEY);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.cipherText()).isNotNull();
        assertThat(encrypted.cipherText()).isNotEqualTo("麦当劳");
    }

    @Test
    @DisplayName("密文长度应大于明文")
    void shouldProduceCipherTextLongerThanPlainText() {
        String plain = "test";
        EncryptedString encrypted = EncryptedString.fromPlain(plain, VALID_KEY);

        // Base64 编码 + IV 附加，密文通常比明文长
        assertThat(encrypted.cipherText().length()).isGreaterThan(plain.length());
    }

    @Test
    @DisplayName("相同明文和密钥应生成不同密文（IV 随机）")
    void shouldProduceDifferentCipherTextForSamePlainText() {
        EncryptedString encrypted1 = EncryptedString.fromPlain("test", VALID_KEY);
        EncryptedString encrypted2 = EncryptedString.fromPlain("test", VALID_KEY);

        // 由于 IV 随机，两次加密结果应不同
        assertThat(encrypted1.cipherText()).isNotEqualTo(encrypted2.cipherText());
    }

    @Test
    @DisplayName("应正确解密回明文")
    void shouldDecryptBackToPlainText() {
        String original = "对方商户名称";
        EncryptedString encrypted = EncryptedString.fromPlain(original, VALID_KEY);

        String decrypted = encrypted.decrypt(VALID_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("应解密中文内容")
    void shouldDecryptChineseContent() {
        String original = "支付宝转账-餐饮消费";
        EncryptedString encrypted = EncryptedString.fromPlain(original, VALID_KEY);

        String decrypted = encrypted.decrypt(VALID_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("应解密包含特殊字符的内容")
    void shouldDecryptContentWithSpecialCharacters() {
        String original = "消费¥100.50，备注：午餐+饮料";
        EncryptedString encrypted = EncryptedString.fromPlain(original, VALID_KEY);

        String decrypted = encrypted.decrypt(VALID_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("错误密钥解密应失败")
    void shouldFailDecryptionWithWrongKey() {
        EncryptedString encrypted = EncryptedString.fromPlain("secret", VALID_KEY);

        String wrongKey = "wrong-key-32-bytes-long-use-!!!!";
        assertThatIllegalStateException()
                .isThrownBy(() -> encrypted.decrypt(wrongKey))
                .withMessageContaining("解密失败");
    }

    @Test
    @DisplayName("非 32 字节密钥应拒绝")
    void shouldRejectNon32ByteKey() {
        String shortKey = "short";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncryptedString.fromPlain("test", shortKey))
                .withMessageContaining("32 字节");
    }

    @Test
    @DisplayName("null 明文应拒绝")
    void shouldRejectNullPlainText() {
        assertThatNullPointerException()
                .isThrownBy(() -> EncryptedString.fromPlain(null, VALID_KEY));
    }

    @Test
    @DisplayName("null 密钥应拒绝")
    void shouldRejectNullKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> EncryptedString.fromPlain("test", null));
    }

    @Test
    @DisplayName("toString 不应暴露密文内容")
    void shouldNotExposeCipherTextInToString() {
        EncryptedString encrypted = EncryptedString.fromPlain("sensitive_data", VALID_KEY);

        String str = encrypted.toString();

        assertThat(str).doesNotContain("sensitive_data");
        assertThat(str).doesNotContain(encrypted.cipherText());
    }

    @Test
    @DisplayName("空字符串加密解密应正常")
    void shouldHandleEmptyString() {
        String original = "";
        EncryptedString encrypted = EncryptedString.fromPlain(original, VALID_KEY);

        String decrypted = encrypted.decrypt(VALID_KEY);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("超长明文应正常加密解密")
    void shouldHandleLongPlainText() {
        String original = "这是一段很长的备注信息，可能包含用户的详细消费描述，例如在某家餐厅吃了午餐，消费金额较大，需要详细记录。";
        EncryptedString encrypted = EncryptedString.fromPlain(original, VALID_KEY);

        String decrypted = encrypted.decrypt(VALID_KEY);

        assertThat(decrypted).isEqualTo(original);
    }
}
