package com.finhub.query.domain.view;

import com.finhub.fundflow.domain.vo.Money;

/**
 * 读模型：聚合查询结果 DTO（按月/按分类等）。
 * 允许为查询性能反规范化，与 Transaction 聚合根解耦。
 */
public record AggregationView(String groupKey, Money totalAmount, Long count, String direction) {
}