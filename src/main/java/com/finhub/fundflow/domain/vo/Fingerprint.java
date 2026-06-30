package com.finhub.fundflow.domain.vo;

import java.util.Objects;

/**
 * 排重指纹值对象：结构化哈希 + 盐值。
 * 匹配仅比较 hashValue，盐值不参与匹配（仅用于生成差异化哈希）。
 */
public record Fingerprint(String hashValue, String salt) {

    public Fingerprint {
        Objects.requireNonNull(hashValue, "哈希值不能为空");
        Objects.requireNonNull(salt, "盐值不能为空");
        if (hashValue.isBlank() || salt.isBlank()) {
            throw new IllegalArgumentException("指纹值和盐值不能为空字符串");
        }
    }

    /** 与另一个指纹匹配（仅比较 hashValue，盐值不参与） */
    public boolean matches(Fingerprint other) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}