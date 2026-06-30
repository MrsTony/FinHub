# ADR-003: AI 只做翻译 + AST 白名单校验

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | AI 辅助上下文、查询分析上下文 |

## 背景

LLM 输出的 SQL 不可信——幻觉可能生成 DELETE、子查询拖垮数据库、或查询非白名单表。不能把 LLM 的输出直接当 SQL 执行。

## 决策

**AI 辅助上下文只负责"翻译"（NL → SQL），所有输出必须经 `SqlAstValidator` 白名单校验：**

- 仅允许 `SELECT` 语句
- 仅允许 `fin_transactions` 表 + 聚合视图
- 禁止子查询、UNION、JOIN（MVP 阶段）
- 禁止所有 DML（INSERT/UPDATE/DELETE/DROP）
- 校验失败 → 返回降级提示，不穿透至核心域

## 实现

```java
// ai/infrastructure/validator/SqlAstValidator.java
@Component
public class SqlAstValidator {
    public ValidatedSql validate(String candidateSql) {
        // JSqlParser 解析 AST → 白名单校验
        // 失败返回 ValidatedSql(sql, false, rejectionReason)
    }
}
```

## AI 分类链路（同样受 ACL 约束）

```
Transaction 特征 → CategorySuggestionEngine (ACL) → CategorySuggestion (建议)
  → TransactionClassifier 领域服务评估置信度
    → isAdoptable() ? markClassified() : 保持 UNCLASSIFIED
```

AI 只给出 **分类建议**（`CategorySuggestion`），最终采纳由聚合根的 `markClassified()` 方法决策。

## 后果

- AI 不能直接接触业务数据——SQL 必须过海关（AST 校验）
- 校验失败返回降级提示："抱歉，这个查询超出了我当前的支持范围。请尝试更简单的问法。"
- 查询分析上下文的 QueryRouter 在 AI 兜底失败时，仍可通过规则路由返回结果

## 面试话术

> "我画了明确红线：AI 不能直接接触业务数据。它生成的 SQL 必须经过 AST 白名单解析——就像海关查护照，不合格的当场退回。"

## 参考

- [SqlAstValidator.java](../../src/main/java/com/finhub/ai/infrastructure/validator/SqlAstValidator.java)
- [CategorySuggestionEngine.java](../../src/main/java/com/finhub/ai/acl/CategorySuggestionEngine.java)
- [CLAUDE_CODE.md — 禁令 2：核心域纯净不可污染](../claude-code/CLAUDE_CODE.md)