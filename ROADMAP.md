# FinHub ROADMAP

> 项目真实进度源。记录当前阶段、已完成、进行中、待办、阻塞、最近验证。
> 架构介绍与使用方式见 `README.md`；战略设计与战术展开见 `行动指南.md` 与 `docs/adr/`。
> **维护规则**：只有已实现并验证过的事项才能进「已完成」；未确认的信息写「待确认」，不要猜；做完代码但未验证时不得标为已完成。纯查询/只读分析不更新本文件。

---

## 当前阶段

- **Day 6（领域事件闭环）已完成**。截至 2026-07-29 实跑 `mvn test`：252 个测试全绿（Day6 基线 238 + 自驱深化新增 14）。
- Day 6 之后转入**自驱深化阶段**：导入分类质量打磨 + 基础设施补强（Knife4j / Actuator / dev 免密）。
- **Day 7（数据治理 + CI/DI）尚未开始**（`docs/superpowers/specs/` 下无 day07 规划文件）。
- 基线 commit：`3ad9e66`（main 分支）；其上已提交 JaCoCo 覆盖率插件 + 日志 UTF-8 字符集（`pom.xml` / `application.yml`）+ 配套 `jacoco-report` skill（`.claude/skills/`），工作区 clean。

---

## 9 天冲刺计划总览

| 阶段 | 主题 | 状态 |
| :--- | :--- | :--- |
| Day 0 | 战略设计 + 环境准备 | ✅ 已完成 |
| Day 1 | 骨架搭建 + ADR | ✅ 已完成 |
| Day 2 | 核心域值对象 + Transaction 聚合根 | ✅ 已完成 |
| Day 3 | 领域服务接口 + 防腐层 + 应用层骨架 | ✅ 已完成 |
| Day 4 | 领域服务实现 + CSV 适配器 | ✅ 已完成 |
| Day 5 | 端到端导入流水线闭环 | ✅ 已完成（221 测试） |
| Day 6 | 领域事件闭环（id 回填 + 监听器） | ✅ 已完成（238 测试） |
| Day 7 | 数据治理 + CI/DI（DVC / Golden Set / GitHub Actions / PromptRegistry） | ⏳ 未开始 |
| Day 8 | 安全加固 + 容器化收尾 | 🟡 部分完成 |
| Day 9 | 整合测试 + BENCHMARK + 演示视频 | ⏳ 未开始 |

---

## 已完成（Day 0~6）

### Day 0 - 战略设计
- 限界上下文图、子域划分、统一语言词汇表
- 6 篇 ADR（DDD 战略、Money 值对象、ACL 防腐层、AI AST 校验、Repository 模式、MVP 安全基线、Docker 容器化）
- Docker Compose（MySQL + Redis）启动验证

### Day 1 - 骨架 + ADR
- Maven 多模块骨架、Flyway 迁移目录、`docker-compose.yml`
- Claude Code 协作规范

### Day 2 - 核心域值对象 + 聚合根（98 测试）
- 值对象：`Money`（精度截断/币种白名单/日志脱敏）、`Category`、`Direction`、`Fingerprint`、`EncryptedString`（AES-256-CBC）、`AnomalyScore`、`CategorySuggestion`
- 聚合根：`Transaction`（`createFrom` 校验 6 条不变量 + `markClassified`/`markAnomaly` + 领域事件收集）
- 领域事件：`TransactionImportedEvent`、`DuplicateDetectedEvent`、`TransactionClassifiedEvent`、`AnomalyDetectedEvent`

### Day 3 - 领域服务接口 + 防腐层 + 应用层骨架
- 领域服务接口（Javadoc 完整）：`DeduplicationService`、`TransactionClassifier`、`AnomalyDetector`、`FingerprintGenerator`
- ACL：`DataSourceAdapter` + `RawRecord`
- `IngestionAppService` 空壳骨架；`DeduplicationServiceImpl` 三重防重（external_id → fingerprint → Caffeine）
- 契约测试 + 实现测试

### Day 4 - 领域服务实现 + CSV 适配器
- `FingerprintGeneratorImpl`（SHA256 五步结构化哈希）
- `TransactionClassifierImpl`（商户关键词规则引擎，预留 AI 接口）
- `AnomalyDetectorImpl`（3 倍均值 SPIKE / 1.5 倍 HIGH）
- `AlipayCSVAdapter` / `WechatCSVAdapter`（2024 新版账单，编码自动识别，异常行容错）
- `TransactionRepositoryImpl` 空壳（7 方法待 Day5）

### Day 5 - 端到端导入流水线闭环（221 测试全绿）
- `TransactionRepositoryImpl` 7 方法 + `TransactionMapper` + `TransactionPO` + `TransactionConverter`
- 配置补全（fingerprint salt / encryption key / Caffeine / `@MapperScan` / `lombok.config`）
- `CompositeDataSourceAdapter`（`@Primary`）按文件名路由 alipay/wechat
- `IngestionAppService.importFile()` 9 步编排闭环
- `IngestionController`（`POST /api/transactions/import`）+ `GlobalExceptionHandler` + Knife4j 注解
- `IngestionEndToEndTest` 端到端（合成 CSV + 真实支付宝账单）

### Day 6 - 领域事件闭环（238 测试全绿）
- `Transaction.assignPersistedId(Long)`：聚合内回填 id + 重建待发事件（不可变 record 替换实例）
- `TransactionRepositoryImpl.save/saveBatch`：insert 后回填自增主键
- `TransactionEventListener`：`@EventListener` 同步消费分类（info）/异常（warn）事件
- E2E 断言：`@SpyBean` + ArgumentCaptor 验证监听器收到带非 null transactionId 的事件

### Day 6 之后 - 基础设施补强（已落）
- Knife4j 4.5.0 集成 + springdoc 2.5.0；prod 双重禁用文档；SecurityConfig profile 感知路由
- `StartupBanner` 启动面板（非 prod 打印 doc.html 链接 + 凭据）
- dev 模式 `/api/**` 免密调试（非 prod 放行）
- `spring-boot-starter-actuator` 启用 `/actuator` 端点
- `GlobalExceptionHandler` 增加 `NoResourceFoundException` 404 处理

---

## 进行中 / 自驱深化

> Day 6 之后的导入分类质量打磨，非 9 天计划原始任务。主题：**账务口径校正**——退款方向、不计收支、资金流向 IN 与收入口径的区分。

- 支付宝「不计收支」特殊处理：退款成功转收入，交易关闭/信用借还跳过（`7b30f88`、`8b5ab24`）
- 分类聚合统计日志：总数 / 已分类 / 未匹配 + 未匹配商户 Top 20（`ab8d666`）
- 补 `TRANSFER`/`INSURANCE` 枚举 + V2 迁移 + 扩分类规则 + 修复退款方向（`f7358c0`）
- 区分资金流向 `IN` 与收入口径，退款定为冲减支出而非收入（`3ad9e66`，最新）

**验证状态**：已验证。2026-07-29 实跑 `mvn test`，252 全绿（含上述提交新增的 14 个测试）。

### 基础设施 - JaCoCo 覆盖率（已验证 / 已提交）

- 2026-07-29 配 JaCoCo 0.8.12：`prepare-agent`（initialize 阶段注入 `-javaagent`）+ `report`（绑 `verify` 阶段，生成 HTML+XML）
- surefire `<argLine>@{argLine}</argLine>` 延迟求值：合并 `properties.argLine`（UTF-8 编码参数）与 JaCoCo 注入的 agent
- `application.yml` 增 `logging.charset.console/file: UTF-8`
- 配套 `jacoco-report` skill（`.claude/skills/jacoco-report/`，`parse_jacoco.py` + `SKILL.md`），纳入版本控制作项目级 skill

**状态：已验证 / 已提交。** 2026-07-30 实跑 `mvn verify`，252 全绿，`target/site/jacoco/jacoco.xml` + `index.html` 正常生成，覆盖率数据非全 0（LINE 87.8% / BRANCH 59.2% / METHOD 85.6% / CLASS 72.9%），证明 prepare-agent 注入与 surefire `@{argLine}` 继承均生效。`pom.xml` + `application.yml` + `.claude/skills/jacoco-report/` 已提交。覆盖率缺口（domain 94.4% / ai 28.6% / knowledge 3.6% / query 0%）属测试补强范畴，不在本次配置提交范围。

---

## 待办

### Day 7 - 数据治理 + CI/DI（未开始）
- [ ] DVC 配置（管理 `prompts/`、`golden-set/`）
- [ ] Golden Set：20 条真实查询 + 错误模式 → Eval 用例
- [ ] GitHub Actions CI（编译 + 单元测试 + 打包镜像）
- [ ] DVC DI Pipeline（Golden Set 通过率）
- [ ] `PromptRegistry` 落地，禁止硬编码 Prompt

### Day 8 - 安全加固 + 容器化收尾（部分完成）
- [x] Basic Auth（SecurityConfig profile 感知）
- [x] Actuator 健康检查端点
- [ ] 字段加密验证：DB 中 counterparty/remark 为密文（待确认）
- [ ] 日志脱敏全局 SLF4J 过滤器（Money 已脱敏，全局过滤器待确认）
- [ ] 临时文件清理（容器启动清空 `/tmp/finhub/upload/`）
- [ ] MySQL 数据卷备份服务
- [ ] 镜像优化 < 100MB（待确认当前体积）

### Day 9 - 整合测试 + 修坑（未开始）
- [ ] 端到端全流程测试（导入 → 排重 → 入库 → 查询 → 聚合）
- [ ] `BENCHMARK.md` 性能基线（1000 行 < 500ms / 10000 行 < 2s 等）
- [ ] ADR 补全（对照 `行动指南.md` 清单核对）
- [ ] 3 分钟演示视频

### 已知技术缺口（跨 Day，待 Day7+ 处理）
- [ ] `anomaly_reason_code` 列未建，`TransactionConverter` 用哨兵占位
- [ ] 通知语义升级为 `@TransactionalEventListener(AFTER_COMMIT)`（当前为同步 `@EventListener`）

---

## 阻塞与风险

| 项 | 说明 | 处置 |
| :--- | :--- | :--- |
| `anomaly_reason_code` 列缺失 | Converter 哨兵占位，异常原因码无法持久化 | Day7+ 建 Flyway 迁移补列 |
| 事件通知语义 | 当前同步消费，事务回滚时事件已发 | Day7+ 改 `AFTER_COMMIT` |
| 重建聚合 null-id 事件隐患 | `TransactionConverter.toDomain` 重建异常聚合时注册 null-transactionId 事件，当前不发布 | Day7+ 改事件语义时审计（见 memory） |
| ROADMAP 此前缺失 | 进度散落在 README + git log + `行动指南.md`，无单一真实源 | 本次补建本文件 |

---

## 最近验证

| 时间点 | 验证内容 | 结果 | 来源 |
| :--- | :--- | :--- | :--- |
| Day 5 | `mvn test` 全量 | 221 全绿（含真实账单 E2E） | `README.md` |
| Day 6 | `mvn test` 全量 | 238 全绿（Day5 基线 229 + Day6 新增 9） | `README.md` |
| 2026-07-29 | `mvn test` 全量 | 252 全绿（Failures: 0, Errors: 0, Skipped: 0），耗时 1:48 | 实跑 |
| 2026-07-30 | `mvn verify` 全量 | 252 全绿 + JaCoCo 报告生成（LINE 87.8% / BRANCH 59.2%） | 实跑 |
| 当前基线 | commit / 分支 / 工作区 | JaCoCo 提交后 main / clean（上一基线 `3ad9e66`） | git |

---

## 维护规则

1. 完成开发、修复、文档补齐、重要调研后，同步更新本文件。
2. 只有已实现**并验证**的事项进「已完成」；做完未验证写「待确认」。
3. 纯查询、只读分析、临时命令不更新本文件。
4. `README.md` 写项目介绍与使用方式；本文件只写会变化的进度。
5. 新阶段开始时，在「9 天计划总览」更新状态，并在「待办」勾选明细。
