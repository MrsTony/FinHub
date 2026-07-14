package com.finhub.fundflow.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.Direction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction 持久化对象（MyBatis-Plus PO）。
 *
 * <p>对应表 {@code fin_transactions}，是 {@link com.finhub.fundflow.domain.aggregate.Transaction}
 * 聚合根的持久化投影。PO 仅为数据载体，无业务行为；与领域对象的转换由
 * {@link com.finhub.fundflow.infrastructure.repository.TransactionConverter} 负责。</p>
 *
 * <h3>枚举映射</h3>
 * <ul>
 *   <li>{@code direction} / {@code category}：全局 {@code EnumTypeHandler} 存枚举名
 *       （如 {@code "IN"}、{@code "FOOD"}），与 DB CHECK 约束一致</li>
 *   <li>{@code anomalyScore}：DECIMAL(5,4)，null 表示无异常</li>
 * </ul>
 */
@Data
@TableName("fin_transactions")
public class TransactionPO {

    /** 聚合根代理键，技术标识，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务唯一标识（支付宝 trade_no 等），第一重排重键，可为 null（fingerprint 兜底） */
    private String externalId;

    /** Money.amount：交易金额，精度强制 2 位小数 */
    private BigDecimal amount;

    /** Money.currency：ISO-4217 币种代码 */
    private String currency;

    /** Direction：IN（收入）/ OUT（支出） */
    private Direction direction;

    /** Category：初始 UNCLASSIFIED，经分类后变更 */
    private Category category;

    /** 业务交易时间（来自 CSV） */
    private LocalDateTime transTime;

    /** EncryptedString：对方户名密文 */
    private String counterpartyCipher;

    /** EncryptedString：交易备注密文 */
    private String remarkCipher;

    /** Fingerprint.hashValue（salt 不入库） */
    private String fingerprint;

    /** 来源系统标识：ALIPAY/WECHAT/BANK/MANUAL */
    private String sourceSystem;

    /** 异常标记 */
    private Boolean anomalyFlag;

    /** 异常评分（0.0~1.0），无异常时为 null */
    private BigDecimal anomalyScore;

    /** 记录创建时间（DB 自动填充） */
    private LocalDateTime createdAt;

    /** 记录更新时间（DB 自动填充） */
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    private Integer version;
}
