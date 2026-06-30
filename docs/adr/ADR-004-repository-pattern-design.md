# ADR-004: Repository 模式 — 领域层定义接口 + 基础设施层实现

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 全局基础设施层 |

## 背景

MyBatis-Plus 的 `BaseMapper<T>` 可以直接在 Service 中调用，但这会让领域层依赖 MP 实现细节。需要一种机制隔离 ORM 框架，让领域层保持纯粹。

## 决策

**领域层只定义 Repository 接口（`TransactionRepository` / `DocumentRepository`），基础设施层用 MyBatis-Plus 实现（`TransactionRepositoryImpl`）。**

## 实现

```
fundflow/domain/repository/TransactionRepository.java   ← 接口（领域层，纯 Java）
fundflow/infrastructure/repository/TransactionRepositoryImpl.java  ← 实现（基础设施层，可用 MP）

knowledge/domain/repository/DocumentRepository.java     ← 接口（领域层，纯 Java）
knowledge/infrastructure/...                            ← 实现（基础设施层）
```

## 关键约束

- 领域层不认识 `BaseMapper`、`LambdaQueryWrapper`
- 基础设施层可以自由切换实现（MP → 手写 XML → JPA）
- 批量插入细节（`rewriteBatchedStatements=true`）封装在基础设施层
- 事务边界由应用层 `@Transactional` 控制，Repository 内不开启新事务

## 后果

- ORM 框架变更不影响领域层
- 批量插入优化（500 条/批次）封装在基础设施层，领域层无感
- 测试时可用内存实现替代真实数据库

## 失败案例（面试加分）

> "我曾过度依赖 MP 的便捷性，结果发现它生成的聚合 SQL 全表扫描，saveBatch 也不是真批量。这让我认识到：ORM 是效率工具，但不能替代 SQL 优化思维，Repository 接口可以隐藏这些技术细节。"

## 参考

- [TransactionRepository.java](../../src/main/java/com/finhub/fundflow/domain/repository/TransactionRepository.java)
- [TransactionRepositoryImpl.java](../../src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java)
- [DocumentRepository.java](../../src/main/java/com/finhub/knowledge/domain/repository/DocumentRepository.java)