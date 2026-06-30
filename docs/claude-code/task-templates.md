
---

```markdown
# Claude Code 任务模板库

> 版本：v1.0
> 生效日期：2026-06-30
> 关联文件：`CLAUDE_CODE.md`（禁令与角色规范）
>
> 所有 Claude Code 任务必须基于以下模板下发，禁止自由发挥。
> 每次任务前复制对应模板，填充 `[方括号]` 内容。
> 若模板与 `CLAUDE_CODE.md` 冲突，以 `CLAUDE_CODE.md` 为准。

---

## 使用流程

1. **确定任务类型**：根据工作对象选择对应模板（A/B/C/D/E）
2. **复制模板**：复制模板全文，填充所有 `[方括号]` 占位符
3. **下发指令**：粘贴到 Claude Code 对话框，禁止口头描述
4. **执行监控**：Claude Code 工作期间，禁止离开终端超过 10 分钟
5. **完成检查**：执行 `CLAUDE_CODE.md` 中的 Review 清单
6. **结果处理**：通过 → `git add -p` → `git commit`；不通过 → `/revert` 或打回修改

---

## 模板 A：填充值对象方法

### 适用场景
- `fundflow.domain.vo` 包内值对象（Money, Category, Fingerprint...）
- `knowledge.domain.vo` 包内值对象（KnowledgeChunk...）
- 方法体标记为 `// TODO` 或 `throw new UnsupportedOperationException("TODO")`
- 不涉及外部依赖注入（纯计算/校验逻辑）

### 指令模板

```markdown
/task: 实现 [文件名] 中所有 TODO 的方法

范围：
- 仅限文件：[完整路径，如 src/main/java/.../Money.java]
- 禁止修改：方法签名、类名、包名、字段定义、import 语句

要求：
1. 使用 Java 17 语法，保持不可变性（返回新实例）
2. 所有校验逻辑在构造器/方法内完成
3. 异常使用 IllegalArgumentException（业务非法）、NullPointerException（空值）
4. 异常消息包含具体原因（如"金额不能为负数: " + amount）
5. 完成后执行：mvn test -Dtest=[测试类名，如 MoneyTest]
6. 若测试失败，报告失败信息（类名、方法名、断言详情），不自动修改测试

检查点：完成后暂停，等待我 review，不要继续下一步
```

### 示例（Money.java）

```markdown
/task: 实现 Money.java 中所有 TODO 的方法

范围：
- 仅限文件：src/main/java/com/finhub/fundflow/domain/vo/Money.java
- 禁止修改：方法签名、类名、包名、字段定义、import 语句

要求：
1. 使用 Java 17 Record 语法，保持不可变性
2. 所有校验在构造器/方法内完成
3. 异常使用 IllegalArgumentException（业务非法）、NullPointerException（空值）
4. 精度强制：setScale(2, RoundingMode.HALF_UP)
5. 加法/比较必须校验币种一致性
6. toString 脱敏：金额替换为 "***"，保留币种
7. 完成后执行：mvn test -Dtest=MoneyTest
8. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 检查清单（任务完成后执行）

- [ ] `git diff --stat` 只改了 1 个文件
- [ ] `git diff` 没有修改方法签名、类名、包名、字段
- [ ] `fundflow.domain` 包无 Spring 注解（`@Autowired`、`@Service` 等）
- [ ] 值对象不可变（final 字段或 Record）
- [ ] 无 `public setter`
- [ ] 无 `System.out.println`
- [ ] `mvn test -Dtest=[测试类名]` 全部通过
- [ ] 无敏感数据明文打印

---

## 模板 B：实现领域服务

### 适用场景
- `fundflow.domain.service` 接口实现（DeduplicationServiceImpl...）
- `knowledge.domain.service` 接口实现（KnowledgeIndexerImpl...）
- 需要依赖注入（Repository、Cache、其他领域服务）
- 封装跨聚合根的业务规则

### 指令模板

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径，如 src/main/java/.../DeduplicationServiceImpl.java]
- 接口定义：[接口完整路径，如 com.example.finhub.fundflow.domain.service.DeduplicationService]（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 严格按接口 Javadoc 的业务规则实现
2. 使用构造器注入依赖，禁止 @Autowired 字段注入
3. 日志使用 SLF4J，禁止打印敏感字段（金额、对方户名、备注、密钥）
4. 可打印字段：[明确列出可打印字段，如 fingerprint 哈希值、external_id、source_system]
5. 领域服务无状态：禁止在类中定义 `List<Transaction>` 等缓存字段
6. 完成后执行：mvn test -Dtest=[测试类名，如 DeduplicationServiceTest]
7. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 示例（预期使用）

```markdown
/task: 实现 DeduplicationServiceImpl 的 deduplicate 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/service/DeduplicationServiceImpl.java
- 接口定义：com.example.finhub.fundflow.domain.service.DeduplicationService（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 严格按接口 Javadoc 的三重防重顺序实现（external_id → fingerprint → 缓存预检）
2. 使用构造器注入依赖，禁止 @Autowired 字段注入
3. 日志使用 SLF4J，禁止打印敏感字段（金额、对方户名、备注）
4. 可打印字段：fingerprint 哈希值、external_id、source_system
5. 领域服务无状态：禁止定义缓存字段
6. 完成后执行：mvn test -Dtest=DeduplicationServiceTest
7. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 检查清单（任务完成后执行）

- [ ] `git diff --stat` 只改了 1 个实现文件
- [ ] 无接口文件修改
- [ ] 构造器注入（`private final XxxRepository repository`）
- [ ] 无 `@Autowired` 字段注入
- [ ] 类中无状态字段（无 `List`、`Map` 等集合缓存）
- [ ] 无 `HttpClient`、`RestTemplate`、`FileWriter` 等技术对象
- [ ] 无 `TransactionTemplate` 事务控制（事务由应用层开）
- [ ] `mvn test -Dtest=[测试类名]` 全部通过

---

## 模板 C：生成防腐层实现

### 适用场景
- `fundflow.acl` 接口实现（AlipayCSVAdapter...）
- `knowledge.acl` 接口实现（TikaDocumentParserImpl...）
- 涉及外部技术细节（CSV 解析、PDF 解析、HTTP 调用、Embedding 生成）
- 技术实现隔离在防腐层，核心域不感知

### 指令模板

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径]
- 接口定义：[接口完整路径]（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. [明确支持/不支持的格式/协议，如"仅支持 2024 新版支付宝 CSV"]
2. [编码/异常处理规则，如"编码自动识别 UTF-8/GBK，失败抛 IllegalArgumentException"]
3. [返回类型限制，如"返回 RawRecord 列表，禁止直接创建 Transaction 聚合根"]
4. 异常行处理：[跳过并记录 WARN / 阻断 / 其他]
5. 完成后执行：mvn test -Dtest=[测试类名]
6. 若测试失败，报告失败信息，不自动修改测试

已知限制：
- [明确列出不支持的场景，直接抛 UnsupportedOperationException]

检查点：完成后暂停，等待我 review
```

### 示例（预期使用：AlipayCSVAdapter）

```markdown
/task: 实现 AlipayCSVAdapter 的 adapt 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/acl/AlipayCSVAdapter.java
- 接口定义：com.example.finhub.fundflow.acl.DataSourceAdapter（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 仅支持 2024 新版支付宝 CSV 格式（字段：交易时间,交易分类,交易对方,对方账号,商品说明,金额,收/支,交易状态,交易订单号,商家订单号,备注,资金状态）
2. 编码自动识别 UTF-8 / GBK，失败时抛 IllegalArgumentException("无法识别文件编码: " + filename)
3. 返回 RawRecord 列表，禁止在适配器内直接创建 Transaction 聚合根
4. 异常行（如退款记录格式不一致、字段缺失）跳过并记录 WARN 日志，不阻断整个文件
5. 金额解析：去除 "¥" 符号，处理 "+/-" 前缀，转换为 BigDecimal
6. 完成后执行：mvn test -Dtest=AlipayCSVAdapterTest

已知限制：
- 不支持微信 / 银行格式，遇到直接抛 UnsupportedOperationException("暂不支持该数据源: " + sourceType)

检查点：完成后暂停，等待我 review
```

### 检查清单（任务完成后执行）

- [ ] `git diff --stat` 只改了 1 个实现文件
- [ ] 无接口文件修改
- [ ] 无 `fundflow.domain` 包引用（防腐层不反向依赖核心域）
- [ ] 返回类型为接口定义的类型（如 `List<RawRecord>`）
- [ ] 无直接 `new Transaction()` 或 `Transaction.createFrom()` 调用
- [ ] `mvn test -Dtest=[测试类名]` 全部通过

---

## 模板 D：生成基础设施实现（Repository / Cache / VectorStore）

### 适用场景
- `fundflow.infrastructure.repository` 实现（TransactionRepositoryImpl...）
- `fundflow.infrastructure.cache` 实现（CaffeineCacheImpl...）
- `knowledge.infrastructure` 实现（ChromaVectorStore...）
- 涉及 MyBatis-Plus、Caffeine、Chroma、Ollama 等技术框架

### 指令模板

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径]
- 接口定义：[接口完整路径]（禁止修改）
- 禁止修改：接口文件、测试文件、Mapper 接口

要求：
1. [技术约束，如"使用 MyBatis-Plus BaseMapper 的 insert 方法，或手写 XML 批量 INSERT"]
2. [性能优化要求，如"开启 MySQL rewriteBatchedStatements，批量大小 500"]
3. 事务边界：由应用层 @Transactional 控制，Repository 内不开启新事务
4. 完成后执行：mvn test -Dtest=[测试类名]
5. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 示例（预期使用：TransactionRepositoryImpl）

```markdown
/task: 实现 TransactionRepositoryImpl 的 saveBatch 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java
- 接口定义：com.example.finhub.fundflow.domain.repository.TransactionRepository（禁止修改）
- 禁止修改：接口文件、测试文件、TransactionMapper 接口

要求：
1. 使用 MyBatis-Plus BaseMapper 的 insert 方法，或手写 XML 批量 INSERT INTO ... VALUES (...), (...)
2. 开启 MySQL rewriteBatchedStatements=true（已在 application.yml 配置），批量大小 500 条
3. 事务边界：由应用层 @Transactional 控制，Repository 内不开启新事务
4. 完成后执行：mvn test -Dtest=TransactionRepositoryTest

检查点：完成后暂停，等待我 review
```

### 检查清单（任务完成后执行）

- [ ] `git diff --stat` 只改了 1 个实现文件
- [ ] 无接口文件修改
- [ ] 无 `@Transactional` 注解在 Repository 方法上
- [ ] 无 `new TransactionTemplate(...)` 等手动事务控制
- [ ] 技术框架引用仅在 `infrastructure` 包内
- [ ] `mvn test -Dtest=[测试类名]` 全部通过

---

## 模板 E：补测试

### 适用场景
- 为已实现类补充单元测试
- 覆盖边界场景、并发场景、异常场景
- 测试契约由你设计，Claude Code 填充实现

### 指令模板

```markdown
/test: 为 [类名] 生成单元测试

范围：
- 测试文件：src/test/java/[对应包路径]/[类名]Test.java
- 禁止修改：已有测试方法、断言、测试类名

要求：
1. 覆盖场景：[明确列出需要覆盖的场景，如"正常构造、null 参数、负金额、精度强制、边界值"]
2. 使用 JUnit 5 + AssertJ
3. 测试方法命名规范：`should[Expected]When[Condition]` 或 `should[Expected]With[Input]`
4. 异常测试使用 `assertThat[Exception]().isThrownBy(...)`
5. 参数化测试使用 `@ParameterizedTest` + `@ValueSource` / `@CsvSource`
6. 完成后执行：mvn test -Dtest=[测试类名]
7. 若测试失败，报告失败信息，不自动修改实现代码

检查点：完成后暂停，等待我 review
```

### 示例（预期使用：CategoryTest）

```markdown
/test: 为 Category 生成单元测试

范围：
- 测试文件：src/test/java/com/finhub/fundflow/domain/vo/CategoryTest.java
- 禁止修改：已有测试方法、断言、测试类名

要求：
1. 覆盖场景：INCOME 与 IN 方向兼容、INCOME 与 OUT 方向不兼容、FOOD 与 OUT 方向兼容、FOOD 与 IN 方向不兼容
2. 使用 JUnit 5 + AssertJ
3. 测试方法命名：`should[BeCompatible/BeIncompatible]With[Direction]`
4. 完成后执行：mvn test -Dtest=CategoryTest

检查点：完成后暂停，等待我 review
```

### 检查清单（任务完成后执行）

- [ ] `git diff --stat` 只改了 1 个测试文件
- [ ] 无实现代码修改
- [ ] 测试方法命名符合规范
- [ ] 使用 AssertJ 而非 JUnit 原生断言
- [ ] 异常测试使用 `assertThatExceptionOfType()` 而非 `@Test(expected=...)`
- [ ] `mvn test -Dtest=[测试类名]` 全部通过

---

## 快速指令集

| 意图 | 指令 | 使用场景 |
|------|------|---------|
| 紧急停止 | `/stop: 立即停止当前任务，保留已修改文件，报告当前进度和未完成项` | Claude 失控、需求变更、发现重大设计缺陷 |
| 回滚变更 | `/revert: 回滚本次任务所有修改，恢复到上次 commit，报告回滚文件列表` | Review 不通过、违反禁令、测试大面积失败 |
| 解释代码 | `/explain: 解释 [类名].[方法名] 的业务逻辑，不修改代码，用中文说明` | 理解遗留代码、面试准备、文档编写 |
| 性能优化 | `/optimize: 在不变更接口前提下，优化 [方法名] 性能，完成后报告 benchmark 数据` | 慢查询、内存泄漏、批量操作优化 |
| 安全审查 | `/security: 审查 [文件名] 的安全风险（敏感数据泄露、注入风险、密钥硬编码），不修改代码` | 代码审计、合规检查、面试准备 |
| 生成文档 | `/docs: 为 [类名] 生成 JavaDoc 注释，包含业务规则说明、异常说明、调用示例` | 补文档、API 文档生成 |
| 重构建议 | `/refactor: 在不改变行为前提下，建议 [类名] 的重构方案，报告收益与风险，不修改代码` | 技术债务评估、代码评审 |

---

## 模板演进记录

| 版本 | 日期 | 变更 | 原因 |
|------|------|------|------|
| v1.0 | 2026-06-30 | 初始版本 | 基于 Money.java 实践提炼 |

---

## 关联文件

- `CLAUDE_CODE.md`：禁令、角色规范、Review 清单、Git 规范
- `docs/adr/`：架构决策记录，解释模板中约束的技术选型原因
- `docs/ubiquitous-language.md`：统一语言词汇表，确保模板中术语一致
```

---

保存路径：`docs/claude-code/task-templates.md`

建议同步更新 `CLAUDE_CODE.md`，在文件头部增加关联引用：

```markdown
> 关联文件：`docs/claude-code/task-templates.md`（任务指令模板库）
> 使用方式：每次任务前复制对应模板，填充 `[方括号]` 内容
```