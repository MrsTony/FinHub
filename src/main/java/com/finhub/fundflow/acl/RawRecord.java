package com.finhub.fundflow.acl;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 防腐层：从 CSV 解析后的原始记录（中间态，非领域实体）。
 * 核心域只认识此 record，不知道 CSV 格式细节。
 */
public record RawRecord(String externalId, BigDecimal amount, String currency,
                        String direction, String counterparty, String remark,
                        LocalDateTime transTime, String sourceSystem) {
}