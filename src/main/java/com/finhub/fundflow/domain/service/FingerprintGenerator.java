package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import java.time.LocalDateTime;

/**
 * 指纹生成领域服务：复杂算法委托，避免聚合根臃肿。
 *
 * <h3>生成算法（结构化哈希）</h3>
 * <ol>
 *   <li>金额：截断精度（BigDecimal setScale(2)）</li>
 *   <li>时间：截断到分钟（LocalDateTime truncatedTo(MINUTES)）</li>
 *   <li>对方户名：标准化（去除空格、特殊字符、转小写）</li>
 *   <li>备注：空值占位（null → "__EMPTY__"）</li>
 *   <li>盐值：每个用户/实例独立（环境变量注入）</li>
 * </ol>
 *
 * <p>注意：盐值是生成参数，不参与匹配比较（matches() 只比较 hashValue）。</p>
 */
public interface FingerprintGenerator {

    /**
     * 生成排重指纹。
     *
     * @param counterparty 对方户名（明文，标准化后哈希）
     * @param money        交易金额（含币种，精度已由 Money 构造器强制）
     * @param transTime    交易时间（截断到分钟）
     * @param remark       备注（可能为 null）
     * @param salt         盐值（32 字节以上，环境变量注入）
     * @return 指纹值对象（含 hashValue 和 salt）
     * @throws IllegalArgumentException 若参数非法
     */
    Fingerprint generate(String counterparty, Money money,
                         LocalDateTime transTime, String remark, String salt);
}