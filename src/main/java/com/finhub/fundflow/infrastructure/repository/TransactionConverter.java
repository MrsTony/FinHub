package com.finhub.fundflow.infrastructure.repository;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.EncryptedString;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.infrastructure.repository.po.TransactionPO;

/**
 * 领域聚合根 {@link Transaction} 与持久化对象 {@link TransactionPO} 之间的转换器。
 *
 * <h3>映射要点</h3>
 * <ul>
 *   <li>{@link com.finhub.fundflow.domain.vo.Money} 平铺为 amount + currency 两列</li>
 *   <li>{@link com.finhub.fundflow.domain.vo.EncryptedString} 直接存密文（cipherText）</li>
 *   <li>{@link com.finhub.fundflow.domain.vo.Fingerprint} 仅存 hashValue（salt 不入库）；
 *       从 PO 重建时用哨兵 salt {@link #PERSISTED_SENTINEL_SALT}，因 matches() 只比 hashValue</li>
 *   <li>{@link AnomalyScore} 仅存 score（reasonCode 不入库），重建时用哨兵 {@link #PERSISTED_SENTINEL_REASON}</li>
 * </ul>
 *
 * <h3>已知缺口（待 Day6+ 补列）</h3>
 * <ul>
 *   <li>{@code anomaly_reason_code} 列未建：toDomain 时 reasonCode 用哨兵占位，丢失原始原因代码</li>
 *   <li>聚合根 id 不回填：save 后 PO.id 不写回 Transaction（无 setId），事件 transactionId 为 null</li>
 * </ul>
 */
public final class TransactionConverter {

    /** 从持久化层重建 Fingerprint 时使用的哨兵盐值（真实 salt 不入库，仅占位满足构造器非空约束） */
    public static final String PERSISTED_SENTINEL_SALT = "PERSISTED";

    /** 从持久化层重建 AnomalyScore 时使用的哨兵原因代码（reasonCode 不入库，仅占位） */
    public static final String PERSISTED_SENTINEL_REASON = "PERSISTED";

    private TransactionConverter() {
        // 工具类，禁止实例化
    }

    /**
     * 领域聚合根 -> 持久化对象。
     *
     * <p>注意：不回填 id/version/createdAt/updatedAt（这些由 DB 或 save 阶段维护）。
     * anomalyFlag 为 null 时按 false 处理（PO 落库前归一化）。</p>
     */
    public static TransactionPO toPO(Transaction tx) {
        TransactionPO po = new TransactionPO();
        po.setId(tx.getId());
        po.setExternalId(tx.getExternalId());

        // Money 平铺
        po.setAmount(tx.getMoney().amount());
        po.setCurrency(tx.getMoney().currency());

        po.setDirection(tx.getDirection());
        po.setCategory(tx.getCategory());
        po.setTransTime(tx.getTransTime());

        // EncryptedString 存密文
        po.setCounterpartyCipher(tx.getCounterparty() != null ? tx.getCounterparty().cipherText() : null);
        po.setRemarkCipher(tx.getRemark() != null ? tx.getRemark().cipherText() : null);

        // Fingerprint 仅存 hashValue
        po.setFingerprint(tx.getFingerprint() != null ? tx.getFingerprint().hashValue() : null);

        po.setSourceSystem(tx.getSourceSystem());

        // 异常状态
        po.setAnomalyFlag(tx.isAnomalyFlag());
        po.setAnomalyScore(tx.getAnomalyScore() != null ? tx.getAnomalyScore().score() : null);

        po.setVersion(tx.getVersion());
        return po;
    }

    /**
     * 持久化对象 -> 领域聚合根。
     *
     * <p>注意：通过 {@link Transaction#createFrom} 工厂方法重建，触发不变量校验。
     * Fingerprint 用哨兵 salt 重建；AnomalyScore 用哨兵 reasonCode 重建。
     * 若 fingerprint 与 externalId 均为 null，工厂方法会抛异常（不变量要求至少一个）。</p>
     */
    public static Transaction toDomain(TransactionPO po) {
        Fingerprint fingerprint = po.getFingerprint() != null
                ? new Fingerprint(po.getFingerprint(), PERSISTED_SENTINEL_SALT)
                : null;

        EncryptedString counterparty = po.getCounterpartyCipher() != null
                ? new EncryptedString(po.getCounterpartyCipher())
                : null;
        EncryptedString remark = po.getRemarkCipher() != null
                ? new EncryptedString(po.getRemarkCipher())
                : null;

        Transaction tx = Transaction.createFrom(
                po.getExternalId(),
                new com.finhub.fundflow.domain.vo.Money(po.getAmount(), po.getCurrency()),
                po.getDirection(),
                po.getCategory(),
                po.getTransTime(),
                counterparty,
                remark,
                fingerprint,
                po.getSourceSystem()
        );

        // 异常状态在工厂方法后回填（工厂方法初始 anomalyFlag=false）
        if (Boolean.TRUE.equals(po.getAnomalyFlag()) && po.getAnomalyScore() != null) {
            tx.markAnomaly(new AnomalyScore(po.getAnomalyScore(), PERSISTED_SENTINEL_REASON));
        }
        return tx;
    }
}
