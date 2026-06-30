package com.finhub.fundflow.domain.aggregate;

import com.finhub.fundflow.domain.vo.*;
import java.time.LocalDateTime;

/**
 * 交易记录聚合根。入口即校验，非法状态不可创建。
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>money 不能为 null</li>
 *   <li>direction 与 category 必须业务兼容（如 INCOME 只能对应 IN）</li>
 *   <li>若 externalId 缺失，则 fingerprint 必须非 null</li>
 * </ul>
 */
public class Transaction {

    private Long id;
    private final String externalId;
    private final Money money;
    private final Direction direction;
    private Category category;
    private final LocalDateTime transTime;
    private final EncryptedString counterparty;
    private final EncryptedString remark;
    private final Fingerprint fingerprint;
    private final String sourceSystem;
    private boolean anomalyFlag;
    private AnomalyScore anomalyScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    private Transaction(String externalId, Money money, Direction direction, Category category,
                        LocalDateTime transTime, EncryptedString counterparty, EncryptedString remark,
                        Fingerprint fingerprint, String sourceSystem) {
        this.externalId = externalId;
        this.money = money;
        this.direction = direction;
        this.category = category;
        this.transTime = transTime;
        this.counterparty = counterparty;
        this.remark = remark;
        this.fingerprint = fingerprint;
        this.sourceSystem = sourceSystem;
        this.anomalyFlag = false;
        this.version = 0;
    }

    /** 工厂方法：从原始记录创建聚合根，校验所有不变量。 */
    public static Transaction createFrom(String externalId, Money money, Direction direction,
                                         Category category, LocalDateTime transTime,
                                         EncryptedString counterparty, EncryptedString remark,
                                         Fingerprint fingerprint, String sourceSystem) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 标记分类结果。需校验 newCategory 与 direction 兼容性。 */
    public void markClassified(Category newCategory, String source) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 标记异常。需设置 anomalyFlag = true，记录 anomalyScore。 */
    public void markAnomaly(AnomalyScore score) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    // ── Getters ──
    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public Money getMoney() { return money; }
    public Direction getDirection() { return direction; }
    public Category getCategory() { return category; }
    public LocalDateTime getTransTime() { return transTime; }
    public EncryptedString getCounterparty() { return counterparty; }
    public EncryptedString getRemark() { return remark; }
    public Fingerprint getFingerprint() { return fingerprint; }
    public String getSourceSystem() { return sourceSystem; }
    public boolean isAnomalyFlag() { return anomalyFlag; }
    public AnomalyScore getAnomalyScore() { return anomalyScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}