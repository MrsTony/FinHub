package com.finhub.fundflow.domain.vo;

import java.util.Objects;

/**
 * 加密字符串值对象：入库即密文，解密需密钥。
 *
 * <p>安全属性内聚到值对象中，日志输出自动脱敏。</p>
 */
public record EncryptedString(String cipherText) {

    public EncryptedString {
        Objects.requireNonNull(cipherText, "密文不能为空");
    }

    /** 工厂方法：明文 + 密钥 → 密文值对象 */
    public static EncryptedString fromPlain(String plainText, String key) {
        // TODO: AES 加密
        throw new UnsupportedOperationException("TODO");
    }

    /** 解密：密文 + 密钥 → 明文 */
    public String decrypt(String key) {
        // TODO: AES 解密
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public String toString() {
        return "EncryptedString{***}";
    }
}