package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import java.util.List;
import java.util.Map;

/**
 * 异常侦探领域服务：基于统计规则检测异常消费模式。
 *
 * <h3>检测规则</h3>
 * <ol>
 *   <li>金额异常：单笔 &gt; 月均同类消费 3 倍 → HIGH，&gt; 1.5 倍 → MEDIUM</li>
 *   <li>重复扣款：7 天内相同金额 + 相同对方户名 &gt; 2 次 → 疑似重复</li>
 *   <li>订阅陷阱：同一商户小额（&lt;30 元）按月规律扣款 → SUBSCRIPTION 提醒</li>
 * </ol>
 *
 * <p>注意：此服务读取历史交易数据（跨聚合根查询），不修改输入对象。</p>
 */
public interface AnomalyDetector {

    /**
     * 检测异常交易。
     *
     * @param transactions 待检测的交易列表（通常是本次导入的批次）
     * @return 异常标识 → 异常评分的映射（key 为临时标识或 external_id）
     */
    Map<String, AnomalyScore> detect(List<Transaction> transactions);
}