# ADR-002: 防腐层（ACL）隔离 CSV 与 AI

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 全局 |

## 背景

FinHub 对接 5 路外部数据源（支付宝/微信/招行借记卡/招行信用卡/广发信用卡），还依赖 3 个 AI 后端（Claude/OpenAI/Ollama）。如果核心域直接引用这些外部库，版本升级或数据格式变更会引发连锁反应。

## 决策

**所有外部依赖通过防腐层（ACL）接口隔离，接口定义在各上下文自身的 `acl/` 包，实现在上游或基础设施层。**

## 当前 ACL 接口清单

| 防腐层接口 | 物理位置 | 方向 |
|-----------|----------|------|
| `DataSourceAdapter` | `fundflow/acl/` | 资金流水上下文定义，隔离 CSV 格式 |
| `DocumentParser` | `knowledge/acl/` | 知识库上下文定义，隔离 PDF/MD 解析库 |
| `EmbeddingGenerator` | `knowledge/acl/` | 知识库上下文定义，隔离向量生成技术 |
| `NLTranslator` | `ai/acl/` | AI 辅助上下文定义，隔离 LLM 技术细节 |
| `CategorySuggestionEngine` | `ai/acl/` | AI 辅助上下文定义，分类建议不直接耦合模型 |
| `AnomalyExplainer` | `ai/acl/` | AI 辅助上下文定义，异常解释不依赖具体模型 |
| `McpToolDispatcher` | `infra/mcp/` | 基础设施上下文，MCP 协议适配（非业务 ACL） |

## 关键约束

- 核心域只认识 `RawRecord`（fundflow），不认识 CSV
- 核心域只认识 `RawDocument`（knowledge），不认识 PDF 库
- AI 上下文通过 ACL 提供"建议"，不做"决策"——决策权在聚合根
- ACL 是**分散的接口包**，不是集中式层

## 后果

- 支付宝改字段格式，只需改 `AlipayCSVAdapter` 实现，Transaction 聚合根完全无感
- LLM 后端切换（Claude → OpenAI → Ollama），核心域无感知
- 新增数据源只需加一个 Adapter 实现，符合开闭原则

## 面试话术

> "核心域的 Transaction 聚合根根本不知道 'CSV' 或 'OpenAI' 是什么。外部格式变化（支付宝改字段）只影响 Adapter 实现，核心域稳定。"

## 参考

- [README.md — 限界上下文图（ACL 黄色虚线框）](../README.md)
- [DataSourceAdapter.java](../../src/main/java/com/finhub/fundflow/acl/DataSourceAdapter.java)
- [CLAUDE_CODE.md — 禁令 6：跨上下文越界不可发生](../claude-code/CLAUDE_CODE.md)