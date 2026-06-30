package com.finhub.query.application;

import com.finhub.query.domain.view.AggregationView;
import java.util.List;

/**
 * 查询路由器：责任链模式。
 *
 * <pre>
 * 正则匹配（总支出/本月/上月等固定模式）
 *   → 规则路由（LambdaQueryWrapper 模板查询）
 *     → AI 辅助兜底（NLTranslator）
 *       → 返回结果
 * </pre>
 */
public interface QueryRouter {

    QueryResult route(String naturalLanguageQuery);

    record QueryResult(String type, List<AggregationView> data, String explanation) {
    }
}