package com.finhub.fundflow.acl;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原始记录：CSV 解析后的中间态数据。
 *
 * <p>注意：这是防腐层概念，不是领域实体。字段可能为 null 或格式错误，
 * 需要应用层校验后才能构造 Transaction 聚合根。</p>
 */
public record RawRecord(
    String externalId,      // 可能为 null（支付宝 CSV 某些版本无此字段）
    BigDecimal amount,      // 可能为 null（解析失败）
    String currency,        // 可能为 null，默认 CNY
    String direction,       // "收入"/"支出" 或 "IN"/"OUT"，需转换
    String counterparty,    // 对方户名，可能为 null
    String remark,          // 备注，可能为 null
    LocalDateTime transTime, // 可能为 null（解析失败）
    String sourceSystem     // 来源标识，如 "ALIPAY"
) {
}