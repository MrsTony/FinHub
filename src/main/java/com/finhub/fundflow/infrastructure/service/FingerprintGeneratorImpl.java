package com.finhub.fundflow.infrastructure.service;

import com.finhub.fundflow.domain.service.FingerprintGenerator;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * FingerprintGenerator 实现：SHA-256 结构化哈希。
 *
 * <p>算法：SHA-256(金额截断精度 + "|" + 时间截断到分钟 + "|" + 对方户名标准化 + "|" + 备注空值占位 + "|" + 盐值)</p>
 */
@Slf4j
@Server
public class FingerprintGeneratorImpl implements FingerprintGenerator {

    /** 备注空值占位符 */
    private static final String EMPTY_REMARK_PLACEHOLDER = "__EMPTY__";

    /** 分隔符 */
    private static final String SEPARATOR = "|";

    @Override
    public Fingerprint generate(String counterparty, Money money,
                                LocalDateTime transTime, String remark, String salt) {
        // 1. 空值校验（remark 允许为 null，用占位符处理）
        validateInputs(counterparty, money, transTime, salt);

        // 2. 金额截断：Money 构造器已强制 2 位小数，直接取 plain string
        String amountStr = money.amount().toPlainString();

        // 3. 时间截断到分钟
        String timeStr = transTime.truncatedTo(ChronoUnit.MINUTES).toString();

        // 4. 对方户名标准化：去空格、转小写、去特殊字符（只保留字母数字汉字）
        String normalizedCounterparty = normalizeCounterparty(counterparty);

        // 5. 备注空值占位
        String remarkPart = (remark == null || remark.isBlank()) ? EMPTY_REMARK_PLACEHOLDER : remark;

        // 6. 拼接原始字符串
        String raw = amountStr + SEPARATOR + timeStr + SEPARATOR
                + normalizedCounterparty + SEPARATOR + remarkPart + SEPARATOR + salt;

        log.debug("指纹原始串: {}", raw);

        // 7. SHA-256 哈希
        String hashHex = sha256Hex(raw);

        // 8. 返回指纹值对象
        return new Fingerprint(hashHex, salt);
    }

    /**
     * 校验必填参数非空（remark 允许为 null）。
     */
    private void validateInputs(String counterparty, Money money,
                                LocalDateTime transTime, String salt) {
        Objects.requireNonNull(counterparty, "对方户名不能为空");
        Objects.requireNonNull(money, "金额不能为空");
        Objects.requireNonNull(transTime, "交易时间不能为空");
        Objects.requireNonNull(salt, "盐值不能为空");
    }

    /**
     * 标准化对方户名：去除前后空格、转小写、去除特殊字符（只保留字母、数字、汉字）。
     *
     * @param counterparty 原始户名
     * @return 标准化后的户名
     * @throws IllegalArgumentException 若标准化后为空字符串
     */
    private String normalizeCounterparty(String counterparty) {
        String trimmed = counterparty.trim();
        String lowercased = trimmed.toLowerCase();
        // 只保留：小写字母 a-z、数字 0-9、中文字符 一-鿿
        String cleaned = lowercased.replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("对方户名标准化后为空");
        }
        return cleaned;
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 字节数组转十六进制小写字符串。
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
