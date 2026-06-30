package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.vo.Fingerprint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指纹生成领域服务：结构化哈希算法（金额截断 + 时间截断到分钟 + 户名标准化 + 空值占位 + 盐值）。
 */
public interface FingerprintGenerator {

    Fingerprint generate(String counterparty, BigDecimal amount,
                         LocalDateTime transTime, String remark, String salt);
}