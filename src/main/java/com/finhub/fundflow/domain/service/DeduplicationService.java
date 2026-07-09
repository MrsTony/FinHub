package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import java.util.List;

/**
 * 排重领域服务：跨交易记录排重判断。
 *
 * <h3>业务规则（三重防重，优先级递减）</h3>
 * <ol>
 *   <li>external_id 唯一性：外部系统提供的业务标识（如支付宝 trade_no）</li>
 *   <li>fingerprint 唯一性：结构化哈希（金额+时间+对方+备注+盐值），external_id 缺失时的兜底</li>
 *   <li>缓存预检：Caffeine 本地缓存，降低数据库查询压力（非权威，仅加速）</li>
 * </ol>
 *
 * <p>注意：此服务不修改输入对象，返回去重后的新列表。</p>
 */
public interface DeduplicationService {

    /**
     * 对候选交易列表进行排重。
     *
     * @param candidates 待排重的交易列表（已构造但未持久化）
     * @return 去重后的交易列表（新列表，不修改输入）
     * @throws IllegalArgumentException 若 candidates 为 null 或包含 null 元素
     */
    List<Transaction> deduplicate(List<Transaction> candidates);
}