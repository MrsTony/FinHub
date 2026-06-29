# FinHub 个人资金数据治理中台 — 产品设计文档

> 状态：已确认 | 日期：2026-06-29 | 基于 DDD 战略行动指南的细化设计

---

## 1. 产品定位

> FinHub 是一套**先己后人**的个人资金流水聚合工具——自己用起来，用真实数据打磨，架构能力自然沉淀为面试资产。

- **当前阶段**：个人工具 + 架构能力展示作品
- **演进路径**：自己用 → 3-5 个朋友用 → 判断是否对外
- **核心价值**：把微信/支付宝/银行 CSV 聚合为一张全局账单，支持自然语言查询

---

## 2. 数据源与支付场景

### 5 路数据源

| 来源 | 类型 | 适配器 |
|------|------|--------|
| 微信支付 | 支付平台 | WechatCSVAdapter |
| 支付宝 | 支付平台 | AlipayCSVAdapter |
| 招商借记卡 | 银行借记卡 | CmbDebitCSVAdapter |
| 招商信用卡 | 银行信用卡 | CmbCreditCSVAdapter |
| 广发信用卡 | 银行信用卡 | GfCreditCSVAdapter |

### 支付链路与排重场景

```
场景 A：支付宝余额/微信零钱支付 → 仅平台账单（无需排重）
场景 B：支付宝/微信绑借记卡支付 → 平台账单 + 银行账单（需排重）
场景 C：支付宝/微信绑信用卡支付 → 平台账单 + 信用卡账单（需排重）
```

三种场景混合，跨平台排重是真实刚需。

---

## 3. 限界上下文与实现深度

| 上下文 | 子域类型 | 实现深度 | 核心产出 |
|--------|----------|----------|----------|
| **资金流水上下文** | 核心域 | **完整深入** | Transaction 聚合根、Money/Fingerprint/EncryptedString 值对象、DeduplicationService、FingerprintGenerator、5 路 CSV Adapter、分类引擎 |
| **AI 辅助上下文** | 支撑域 | **完整** | NLTranslator 防腐层、AST 校验器、分类 AI 兜底、Spring AI 多后端切换（Claude/OpenAI/Ollama）、Prompt 模板管理 |
| **查询分析上下文** | 支撑域 | **骨架 + 2-3 规则** | QueryRouter 责任链骨架、基础聚合 API、关键词检索 |
| **数据治理上下文** | 通用域 | **MVP 不做（预留包结构）** | DVC、Golden Set、CI/DI Pipeline 延后 |
| **基础设施上下文** | 通用域 | **完整** | Docker Compose、Flyway、EncryptedString 加密、日志脱敏、Basic Auth |

---

## 4. 核心域设计：资金流水上下文

### Transaction 聚合根

```text
Transaction（聚合根）
├── id: Long                    — 数据库自增 ID
├── externalId: String?         — 业务唯一标识（支付宝交易号等）
├── money: Money                — 金额值对象（不可变，BigDecimal + DECIMAL(18,2)）
├── direction: Direction        — INCOME / EXPENSE
├── transactionTime: LocalDateTime
├── counterparty: EncryptedString  — 对方户名（加密值对象）
├── remark: EncryptedString        — 备注（加密值对象）
├── fingerprint: Fingerprint       — 排重指纹（值对象）
├── sourceSystem: SourceSystem     — WECHAT / ALIPAY / CMB_DEBIT / CMB_CREDIT / GF_CREDIT
├── category: Category?            — 分类（导入后由分类引擎填充）
└── batchId: String                — 导入批次
```

**不变量**：
- money 不能为 null，精度强制 DECIMAL(18,2)
- direction 不能为 null，且与金额符号一致性（收入正、支出负）
- 若 externalId 缺失则 fingerprint 必须生成
- counterparty 和 remark 入库即密文

### 值对象

| 值对象 | 职责 | 关键行为 |
|--------|------|----------|
| **Money** | 金额精度守护 | add()、subtract()、abs()、compareTo()，不可变 |
| **Fingerprint** | 排重指纹 | matches()，由 FingerprintGenerator 工厂创建 |
| **EncryptedString** | 敏感字段加密 | decrypt(key)，构造时即 AES 加密 |
| **Category** | 支出分类 | 枚举：餐饮、交通、购物、住房、娱乐、医疗、教育、转账、其他 |

### 领域服务

**DeduplicationService**：三重防重
1. 优先 externalId 匹配（精确）
2. externalId 缺失时 fingerprint 匹配（模糊）
3. Caffeine 缓存预检 + DB 唯一约束兜底

**FingerprintGenerator**：结构化指纹
- 金额截断精度 + 时间截断到分钟 + 对方户名标准化 + 备注空值占位 + 盐值
- 边界处理：同一分钟多笔相同金额（如自动售货机连刷）标记为疑似重复，人工确认

**CategorizationEngine**：分类引擎（详见第 6 节）

### 防腐层接口

```text
DataSourceAdapter（接口，定义在领域层）
└── adapt(InputStream csv) → List<RawRecord>
    ├── AlipayCSVAdapter       — 支付宝 2024 新版格式
    ├── WechatCSVAdapter       — 微信支付导出格式
    ├── CmbDebitCSVAdapter     — 招商借记卡
    ├── CmbCreditCSVAdapter    — 招商信用卡
    └── GfCreditCSVAdapter     — 广发信用卡
```

核心域只认识 `RawRecord`（领域概念），不知道 CSV 格式细节。支付宝改字段只需改 Adapter。

---

## 5. 导入管道设计

### 三种导入方式

| 方式 | 实现 | 场景 |
|------|------|------|
| **文件夹监听** | WatchService 监听 `~/finhub/import/`，新文件自动处理 | 月度批量导入主流程 |
| **Web 上传** | REST API + MultipartFile | 临时导入 |
| **拖拽上传** | 前端拖拽区域 → 调上传 API | 想到就导 |

### 导入流程

```text
CSV 文件 → 识别来源（文件名/格式嗅探）→ 选择 Adapter
→ 解析为 RawRecord 列表（异常行跳过+告警，不阻断）
→ FingerprintGenerator 生成指纹
→ DeduplicationService 排重
→ CategorizationEngine 分类
→ Transaction 聚合根创建
→ Repository 批量持久化
→ 发布 TransactionImportedEvent
```

---

## 6. 分类引擎设计

### 分类链路

```text
RawRecord
  → 规则匹配（商户名/备注关键词，~70% 覆盖）
    → 命中：直接分类
    → 未命中：AI 兜底
      → LLM 推断分类
      → 用户可在 UI 修正
      → 修正结果反馈到规则库（未来迭代）
```

### 规则示例

| 规则 | 关键词 | 分类 |
|------|--------|------|
| 餐饮-美团 | 美团、饿了么、大众点评、肯德基、麦当劳、星巴克 | 餐饮 |
| 交通-滴滴 | 滴滴、曹操出行、T3、地铁、公交 | 交通 |
| 购物-电商 | 淘宝、天猫、京东、拼多多、唯品会 | 购物 |
| 住房 | 链家、自如、贝壳、物业、电费、水费 | 住房 |
| 转账 | 转账、红包、汇款 | 转账 |

### AI 分类 Prompt 模板（示例）

```text
你是一个账单分类助手。根据以下交易信息，判断它属于哪个类别：
- 金额：{amount}
- 对方户名：{counterparty}
- 备注：{remark}
- 来源：{sourceSystem}

类别选项：餐饮、交通、购物、住房、娱乐、医疗、教育、转账、其他

只返回类别名称。
```

---

## 7. AI 辅助上下文设计

### 两条 AI 链路

**链路一：分类兜底**（见第 6 节）

**链路二：自然语言查询**

```text
用户输入自然语言问题
  → NLTranslator 防腐层接口
    → Spring AI 加载 Prompt 模板（从 PromptRegistry 配置）
    → 调用 LLM（Claude/OpenAI/Ollama 可切换）
    → 返回候选 SQL
  → SqlAstValidator（JSqlParser AST 白名单校验）
    → 仅允许 SELECT + 白名单表 + 无子查询 + 无 DML
    → 校验失败 → 返回降级提示
    → 校验通过 → 执行 SQL → 返回结果
```

### Spring AI 多后端配置

```yaml
# application.yml
finhub:
  ai:
    active-provider: claude  # claude | openai | ollama
    providers:
      claude:
        api-key: ${ANTHROPIC_API_KEY}
        model: claude-haiku-4-5
      openai:
        api-key: ${OPENAI_API_KEY}
        model: gpt-4o-mini
      ollama:
        base-url: http://localhost:11434
        model: qwen3
```

### AST 白名单校验规则

- 仅允许 `SELECT` 语句
- 仅允许查询 `transactions` 表及其聚合视图
- 禁止子查询、UNION、JOIN（MVP 阶段）
- 禁止所有 DML（INSERT/UPDATE/DELETE/DROP）
- 失败返回："抱歉，这个查询超出了我当前的支持范围。请尝试更简单的问法。"

---

## 8. 查询分析上下文（骨架）

### QueryRouter 责任链

```text
请求 → 正则匹配（总支出/本月/上月等固定模式）
      → 规则路由（LambdaQueryWrapper 模板查询）
      → AI 辅助兜底（NLTranslator）
      → 返回结果
```

### MVP 内置查询（2-3 条规则示例）

| 问法 | 规则 | SQL 模板 |
|------|------|----------|
| "本月总支出" / "这个月花了多少" | 正则 `本月|这个月.*花` | `SELECT SUM(amount) FROM transactions WHERE direction='EXPENSE' AND MONTH(time)=?` |
| "上个月餐饮" / "上月吃饭" | 正则 `上[个]?月.*(餐饮|吃)` | `SELECT SUM(amount) FROM transactions WHERE category='餐饮' AND MONTH(time)=?` |
| "最大一笔" / "单笔最高" | 正则 `最大.*笔|单笔.*高` | `SELECT * FROM transactions WHERE direction='EXPENSE' ORDER BY amount DESC LIMIT 1` |

---

## 9. 基础设施设计

### Docker Compose 编排

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes:
      - mysql_data:/var/lib/mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: finhub
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/finhub
      ENCRYPTION_KEY: ${ENCRYPTION_KEY}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
    volumes:
      - ~/finhub/import:/app/import:ro  # 文件夹监听
```

### 安全基线

- **EncryptedString**：AES-256 加密，密钥通过容器环境变量注入
- **日志脱敏**：SLF4J 过滤器，金额 > 1000 或敏感字段自动打码
- **Basic Auth**：Spring Security，用户名/密码环境变量注入
- **临时文件清理**：容器启动清空 `/tmp/finhub/upload/`

### Flyway 数据库迁移

- 手写 SQL 迁移脚本，禁止 MyBatis-Plus 自动 DDL
- V1: transactions 表（external_id 唯一索引 + fingerprint 唯一索引 + amount DECIMAL(18,2)）
- 预留：cost_center、project_code 企业扩展字段

---

## 10. 明确不做（MVP 范围外）

| 不做 | 原因 |
|------|------|
| DVC / Golden Set / Prompt 版本化 | 单用户无数据治理需求 |
| CI/DI Pipeline（GitHub Actions） | 本地开发，按需手动 |
| 演示视频 | 先让代码能跑 |
| 读模型物理分离（CQRS 落地） | MVP 逻辑分离即可 |
| 多币种支持 | 个人场景 CNY 足够 |
| 预算管理 / 现金流预警 | 等核心域稳定后迭代 |
| 前端 SPA | 服务端渲染 HTML + HTMX 或极简前端即可 |

---

## 11. 包结构（DDD 分层 + 限界上下文物理隔离）

```text
com.finhub
├── fundflow                          — 资金流水上下文（核心域）
│   ├── domain
│   │   ├── aggregate                 — Transaction
│   │   ├── vo                        — Money, Fingerprint, EncryptedString, Category
│   │   ├── service                   — DeduplicationService, FingerprintGenerator, CategorizationEngine
│   │   ├── event                     — TransactionImportedEvent, DuplicateDetectedEvent
│   │   └── repository                — TransactionRepository（接口）
│   ├── application
│   │   └── IngestionAppService
│   ├── acl                           — DataSourceAdapter（防腐层接口）, RawRecord
│   └── infrastructure
│       ├── repository                — TransactionRepositoryImpl（MyBatis-Plus）
│       ├── adapter                   — AlipayCSVAdapter, WechatCSVAdapter, CmbDebitCSVAdapter, ...
│       ├── cache                     — CaffeineCache
│       └── watch                     — ImportFolderWatcher（WatchService）
├── ai                                — AI 辅助上下文（支撑域）
│   ├── acl                           — NLTranslator（防腐层接口）
│   ├── domain
│   │   └── vo                        — NaturalLanguageQuery, ValidatedSql
│   └── infrastructure
│       ├── translator                — SpringAiNLTranslator
│       ├── validator                 — SqlAstValidator
│       └── config                    — AiProviderConfig（多后端配置）
├── query                             — 查询分析上下文（支撑域）
│   ├── application
│   │   └── QueryRouter（责任链）
│   └── domain
│       └── view                      — AggregationView（读模型 DTO）
├── datagov                           — 数据治理上下文（通用域，预留）
├── infra                             — 基础设施上下文（通用域）
│   ├── docker
│   ├── security                      — EncryptedString 加密实现、日志脱敏
│   └── config                        — Flyway、Docker Compose 健康检查
└── shared                            — 跨上下文共享
    └── exception                     — 领域异常基类
```

---

## 12. 开发顺序（建议）

| 阶段 | 内容 | 产出 |
|------|------|------|
| **1** | 项目骨架 + Docker Compose + Flyway + 包结构 | 空项目可启动，MySQL 可连接 |
| **2** | 核心域领域层：Money、Fingerprint、EncryptedString、Transaction 聚合根 | 领域层 100% 单元测试覆盖 |
| **3** | 防腐层 + 导入管道：5 路 Adapter + 文件夹监听 + Web 上传 | 真实 CSV 能解析入库 |
| **4** | 排重 + 分类：DeduplicationService + CategorizationEngine | 导入管道完整闭环 |
| **5** | AI 辅助上下文：NLTranslator + AST 校验 + Spring AI 多后端 | 自然语言能查询 |
| **6** | 查询分析骨架：QueryRouter + 2-3 条规则 + 基础 API | 规则查询可用 |
| **7** | 安全加固 + 整合测试 | EncryptedString、日志脱敏、Basic Auth |
