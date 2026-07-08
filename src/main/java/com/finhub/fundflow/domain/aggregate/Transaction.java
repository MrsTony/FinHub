package com.finhub.fundflow.domain.aggregate;

import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
import com.finhub.fundflow.domain.vo.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private final List<Object> domainEvents = new ArrayList<>();

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
        Objects.requireNonNull(money, "金额不能为空");
        Objects.requireNonNull(direction, "方向不能为空");
        Objects.requireNonNull(category, "分类不能为空");
        Objects.requireNonNull(transTime, "交易时间不能为空");
        Objects.requireNonNull(sourceSystem, "来源系统不能为空");

        if (!isCompatible(category, direction)) {
            throw new IllegalArgumentException("分类 " + category.getDisplayName()
                    + " 与方向 " + direction + " 不兼容");
        }

        if ((externalId == null || externalId.isBlank()) && fingerprint == null) {
            throw new IllegalArgumentException("externalId 缺失时 fingerprint 必须提供");
        }

        return new Transaction(externalId, money, direction, category, transTime,
                counterparty, remark, fingerprint, sourceSystem);
    }

    /** 标记分类结果。需校验 newCategory 与 direction 兼容性。 */
    public void markClassified(Category newCategory, String source) {
        Objects.requireNonNull(newCategory, "分类不能为空");
        Objects.requireNonNull(source, "分类来源不能为空");

        if (!isCompatible(newCategory, this.direction)) {
            throw new IllegalArgumentException("分类 " + newCategory.getDisplayName()
                    + " 与方向 " + this.direction + " 不兼容");
        }

        this.category = newCategory;
        registerEvent(new TransactionClassifiedEvent(this.id, newCategory, source));
    }

    /** 标记异常。需设置 anomalyFlag = true，记录 anomalyScore。 */
    public void markAnomaly(AnomalyScore score) {
        if (score == null) {
            throw new IllegalArgumentException("异常评分不能为空");
        }

        this.anomalyFlag = true;
        this.anomalyScore = score;
        registerEvent(new AnomalyDetectedEvent(this.id, score));
    }

    /** 领域事件列表（只读） */
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /** 清空已发布的事件 */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    private void registerEvent(Object event) {
        domainEvents.add(event);
    }

    private static boolean isCompatible(Category category, Direction direction) {
        if (direction == Direction.IN) {
            return category.isIncomeCompatible();
        }
        return category.isExpenseCompatible();
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