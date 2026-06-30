package com.finhub.infra.mcp;

import java.util.Map;

/**
 * MCP 工具调度器：将外部 AI 客户端的 Tool 调用路由到应用层用例。
 * 物理位置：infra/mcp/（基础设施层协议适配，类似 HTTP Controller）。
 */
public interface McpToolDispatcher {

    Object dispatch(String toolName, Map<String, Object> params);

    // ── Tool 名称常量 ──
    String TOOL_QUERY_TRANSACTIONS = "query_transactions";
    String TOOL_GET_SPENDING_SUMMARY = "get_spending_summary";
    String TOOL_GET_ANOMALY_REPORT = "get_anomaly_report";
    String TOOL_CLASSIFY_TRANSACTION = "classify_transaction";
    String TOOL_SEARCH_KNOWLEDGE = "search_knowledge";
}