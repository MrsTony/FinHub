package com.finhub.fundflow.domain.vo;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * 加密字符串值对象：入库即密文，解密需密钥。
 *
 * <p>安全属性内聚到值对象中，日志输出自动脱敏。</p>
 */
public record EncryptedString(String cipherText) {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 16;

    public EncryptedString {
        Objects.requireNonNull(cipherText, "密文不能为空");
    }

    /** 工厂方法：明文 + 密钥 → 密文值对象 */
    public static EncryptedString fromPlain(String plainText, String key) {
        Objects.requireNonNull(plainText, "明文不能为空");
        Objects.requireNonNull(key, "密钥不能为空");
        validateKeyLength(key);

        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(key), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return new EncryptedString(Base64.getEncoder().encodeToString(combined));
        } catch (Exception e) {
            throw new IllegalStateException("加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密：密文 + 密钥 → 明文 */
    public String decrypt(String key) {
        Objects.requireNonNull(key, "密钥不能为空");
        validateKeyLength(key);

        try {
            byte[] combined = Base64.getDecoder().decode(this.cipherText);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, buildKey(key), new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "EncryptedString{***}";
    }

    private static void validateKeyLength(String key) {
        if (key.getBytes(StandardCharsets.UTF_8).length != KEY_LENGTH) {
            throw new IllegalArgumentException("密钥必须为 32 字节");
        }
    }

    private static SecretKeySpec buildKey(String key) {
        return new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}