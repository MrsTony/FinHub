# ADR-000: DDD 战略设计决策

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 全局架构 |

## 背景

2026-06-29 完成产品设计文档后，面临核心问题：资金流水有严格的不变量（金额精度、排重规则、分类一致性），传统贫血 MVC 模型（Controller → Service → Mapper）会导致业务规则散落在多个 Service 中，易被绕过。

## 决策

采用 **DDD 领域驱动设计** 而非贫血 MVC。

## 后果

### ✅ 正面

- **Transaction 聚合根** 守护所有不变量，创建即合法（私有构造器 + 工厂方法）
- **Money 值对象** 让金额精度不可能被业务代码忽略（不可变、构造时强制精度）
- **Fingerprint 值对象** 封装排重逻辑，外部无法创建"不合法指纹"
- **防腐层（ACL）** 隔离 CSV 解析与 AI 调用细节，核心域不认识任何外部技术
- **6 个限界上下文** 物理隔离包结构（`fundflow/`、`knowledge/`、`ai/`、`query/`、`datagov/`、`infra/`）

### ❌ 负面

- 包结构比贫血模型多 3-4 层，初期理解成本高
- 领域层纯接口（Repository、领域服务），基础设施层实现，新手容易写反依赖
- MVP 阶段 CQRS 未物理分离（查询分析上下文直接读 ff_repo），需面试时补充演进话术

### ⚠️ 风险

- 工程师绕过工厂方法直接 `new Transaction()` → `CLAUDE_CODE.md` 禁令 3 明确禁止
- 跨上下文 import 污染 → `CLAUDE_CODE.md` 禁令 6 明确禁止
- 聚合根过大 → 复杂计算已委托给 FingerprintGenerator / DeduplicationService 等领域服务

## 约束

| 规则 | 来源 |
|------|------|
| fundflow 与 knowledge 零直接依赖 | README 红线规则 |
| ACL 分散在各上下文 `acl/` 包，非集中式层 | 设计文档 §2-7 |
| 领域层零 Spring 注解（除领域事件发布器） | CLAUDE_CODE.md 禁令 2 |

## 参考

- [README.md — 限界上下文图](../README.md)
- [CLAUDE_CODE.md — 禁令清单](../claude-code/CLAUDE_CODE.md)