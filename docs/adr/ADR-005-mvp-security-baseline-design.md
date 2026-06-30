# ADR-005: MVP 安全基线 — 字段加密 + 日志脱敏 + Basic Auth

| 属性 | 值 |
|------|-----|
| **状态** | 已采纳 |
| **日期** | 2026-06-30 |
| **决策者** | xiaod |
| **影响范围** | 全局 |

## 背景

个人资金数据的不可再生性决定了安全不是"后期打补丁"。即便 MVP 阶段只自己用，也必须做好三项基线：

1. 敏感字段（对方户名、备注）入库即密文
2. 日志中金额与敏感字段自动打码
3. 接口至少 Basic Auth 保护

## 决策

| 措施 | 实现 | 位置 |
|------|------|------|
| **EncryptedString 值对象** | AES-256 加密，密钥由 `ENCRYPTION_KEY` 环境变量注入 | `fundflow/domain/vo/EncryptedString.java` |
| **日志脱敏** | `Money.toString()` 输出 `Money{amount=***}` | `fundflow/domain/vo/Money.java` |
| | `EncryptedString.toString()` 输出 `EncryptedString{***}` | `fundflow/domain/vo/EncryptedString.java` |
| **HTTP Basic Auth** | Spring Security，无状态 Session，CSRF 禁用 | `infra/config/SecurityConfig.java` |
| **密码不落地** | `BASIC_AUTH_USERNAME` / `BASIC_AUTH_PASSWORD` 环境变量注入 | `application.yml` |
| | `.env` 文件在 `.gitignore` 中 | `.gitignore` |
| **数据库密码** | `${DB_PASSWORD}` 无默认值，必须注入 | `application.yml` |
| **指纹盐值** | `${FINHUB_FINGERPRINT_SALT}` 每个实例独立 | `.env.example` |

## 数据库加密字段

```sql
-- V1__create_fin_transactions_table.sql
counterparty_cipher TEXT  -- 对方户名密文
remark_cipher       TEXT  -- 交易备注密文
```

## 后果

- 对方户名和备注在数据库中是密文，即使数据库泄露也无法读取
- 日志脱敏是值对象的默认行为（`toString()`），不依赖开发者自觉
- 密码通过环境变量注入，无硬编码风险
- `/actuator/health` 免认证，健康检查不触发 Basic Auth

## 面试话术

> "即使是 MVP 我也做了 AES 加密。对方户名和备注在数据库中是密文——安全是业务属性，不是后期补丁。"

## 参考

- [EncryptedString.java](../../src/main/java/com/finhub/fundflow/domain/vo/EncryptedString.java)
- [SecurityConfig.java](../../src/main/java/com/finhub/infra/config/SecurityConfig.java)
- [.env.example](../../.env.example)
- [V1__create_fin_transactions_table.sql](../../src/main/resources/db/migration/V1__create_fin_transactions_table.sql)