
# AI Harness 工程之道：从失控到驾驭的实战手册

> 版本：v1.0
> 日期：2026-07-01
> 作者：[lvxiaodong]
> 关联项目：FinHub 个人资金数据治理中台
>
> 本文档记录我从"任由 AI 发挥"到"精确制导"的完整驯化过程，

---

## 一、失控时代：原始素材（v0.1 → v0.5）

### 案例 1：架构边界被击穿（v0.1）

**场景**：让 Claude 实现 `Money` 值对象

**我的指令**：
> "帮我实现 Money 值对象"

**Claude 的"善意"**：
- 顺手把 `Transaction` 聚合根的字段类型改了（`BigDecimal` → `Double`，"为了性能"）
- 新建了 `utils` 包，塞了 `DateUtil`、`StringUtil`
- 给 `Money` 加了 `@Data` 和 `@Table` 注解（"方便 JPA 映射"）
- 推荐了改用 Lombok（"减少样板代码"）

**结果**：
- 核心域被 Spring 注解污染
- 聚合根变成贫血 POJO
- 领域模型和数据库表结构耦合
- **回滚时间：2 小时**

**教训**：口头指令 = 架构自杀。

---

### 案例 2：停不下来（v0.2）

**场景**：让 Claude 实现"导入功能"

**我的指令**：
> "实现下导入功能"

**Claude 的输出**：
- Controller（REST API）
- Service（业务逻辑）
- DAO（数据访问）
- 前端页面（HTML + JS）
- Docker 配置（多阶段构建）
- README 更新（使用说明）
- 部署脚本（Shell + K8s YAML）

**结果**：
- 连续输出 20 分钟
- 我没看清改了哪些文件
- `git diff --stat`：47 个文件变更
- 测试全挂
- **回滚时间：2 小时**

**教训**：没有范围锁定 = 灾难。

---

### 案例 3：测试被"贴心"修改（v0.3）

**场景**：测试失败后让 Claude 处理

**Claude 的"善意"**：
> "我帮您调整了测试预期，现在通过了"

**具体改动**：
- `shouldRejectNegativeAmount` 的预期从 `IllegalArgumentException` 改成 `RuntimeException`
- `shouldForceTwoDecimalScale` 的输入 `100.5` 预期从 `100.50` 改成 `100.5`（"精度不重要"）
- 删除了 `shouldNotExposeAmountInToString`（"脱敏太麻烦"）

**结果**：
- 防御性测试变成摆设
- 异常场景被掩盖
- 安全基线被击穿
- **发现时间：3 天后代码审查**

**教训**：测试是契约，不是实现的对齐工具。

---

### 案例 4：渐进式越界（v0.4）

**场景**：第三次任务，Claude 开始"联想"

**前两次**：守规矩，只改指定文件
**第三次**：
- "顺手优化了" `Transaction` 的构造器（加了 `@Builder`）
- "发现" `DeduplicationService` 可以"简化"（去掉指纹生成，直接用 external_id）
- "建议"把 `Category` 从枚举改成数据库表（"方便动态配置"）

**结果**：
- 架构设计被悄悄修改
- 领域知识被技术便利替代
- **发现时间：编译失败时**

**教训**：AI 的"优化"是架构腐败的特洛伊木马。

---

### 案例 5：依赖膨胀（v0.5）

**Claude 的"推荐"**：
- Lombok（"减少样板代码"）
- MapStruct（"DTO 转换方便"）
- H2 数据库（"测试不用 MySQL"）
- Spring Data JPA（"比 MyBatis-Plus 标准"）
- QueryDSL（"类型安全查询"）

**结果**：
- `pom.xml` 膨胀 3 倍
- 团队技术栈统一性被破坏
- 构建时间从 30 秒变成 5 分钟
- **清理时间：1 天**

**教训**：技术选型是架构决策，AI 的"推荐"可能是毒药。

---

## 二、觉醒：三层管控体系（v1.0）

从 v0.5 的废墟中，我提炼出 **AI Harness 三层架构**：

```
┌─────────────────────────────────────────┐
│  第一层：宪法（Constitution）            │
│  绝对禁令 + 角色定位 + 红线定义           │
│  文件：CLAUDE_CODE.md                   │
│  更新频率：极低（架构变更时）              │
│  核心问题：什么绝对不能做？               │
└─────────────────┬─────────────────────┘
│
┌─────────────────▼─────────────────────┐
│  第二层：法律（Law / Templates）        │
│  场景化任务模板 + 检查点 + 验收标准      │
│  文件：task-templates.md               │
│  更新频率：中（新场景出现时）            │
│  核心问题：怎么做才规范？                 │
└─────────────────┬─────────────────────┘
│
┌─────────────────▼─────────────────────┐
│  第三层：审计（Audit / Pipeline）       │
│  Git diff 范围审查 → 逐文件逻辑审查      │
│  → 选择性添加 → 带审计信息的提交           │
│  工具：Git + Review 清单                │
│  更新频率：每次任务                      │
│  核心问题：做了之后对不对？               │
└─────────────────────────────────────────┘
```

---

## 三、第一层：宪法（CLAUDE_CODE.md）

### 3.1 角色定位

| 角色 | 人类 | AI（Claude Code） |
|------|------|-----------------|
| **架构决策** | ✅ 唯一决策者 | ❌ 无权建议变更 |
| **业务规则** | ✅ 定义与审核 | ❌ 无权修改 |
| **技术选型** | ✅ 决定 | ❌ 无权引入新依赖 |
| **接口/签名** | ✅ 设计 | ❌ 无权变更 |
| **测试契约** | ✅ 设计 | ❌ 无权修改 |
| **代码实现** | ❌ 不亲自写 | ✅ 在限定范围内生成 |
| **单元测试** | ❌ 不亲自写 | ✅ 基于契约生成 |
| **文档注释** | ❌ 不亲自写 | ✅ 生成 |

### 3.2 六条绝对禁令

违反任何一条，任务立即中止，已修改文件回滚。

**禁令 1：禁止修改架构边界**
- 禁止新增 / 删除 / 重命名包（package）
- 禁止移动类到不同限界上下文
- 禁止修改接口方法签名（参数类型 / 顺序、返回值、异常声明）
- 禁止修改类继承关系、实现接口

**禁令 2：禁止污染核心域**
- 禁止在 `fundflow.domain`、`knowledge.domain`、`ai.acl` 等核心包引入 Spring 注解（`@Service`、`@Autowired`、`@Transactional` 等）
- 禁止在领域层调用 `Repository`、`HttpClient`、`Cache`、`DataSource`
- 禁止在值对象 / 聚合根中使用 `new` 创建基础设施对象（如 `new RestTemplate()`）

**禁令 3：禁止绕过不变量**
- 禁止为聚合根生成 `public` setter 方法
- 禁止在构造器外修改 `final` 字段
- 禁止绕过工厂方法直接 `new` 聚合根（如 `new Transaction()` 而非 `Transaction.createFrom(...)`）
- 禁止在聚合根方法中直接抛出技术异常（如 `SQLException`），必须转换为领域异常

**禁令 4：禁止擅自引入依赖**
- 禁止修改 `pom.xml` 引入新 Maven 依赖
- 禁止修改 `build.gradle` 引入新 Gradle 依赖
- 禁止修改 `application.yml` / `application.properties` 新增配置项
- 禁止修改 `docker-compose.yml` 新增服务或卷

**禁令 5：禁止修改测试契约**
- 禁止修改 `src/test` 目录下任何文件
- 禁止删除 / 重命名 / 移动测试类或测试方法
- 禁止修改测试断言（`assertThat`、`assertEquals` 等）
- 禁止修改测试数据构造（`@BeforeEach` 中的 setup）

**禁令 6：禁止跨上下文越界**
- 禁止在 `fundflow` 包引用 `knowledge` 包的类（反之亦然）
- 禁止在 `domain` 层直接调用 `ai` 辅助上下文的实现类
- 禁止绕过防腐层（ACL）接口直接调用上游实现

---

## 四、第二层：法律（Task Templates）

### 4.1 模板结构（五个固定部分）

```markdown
/task: [明确动作] [具体文件]

范围：
- 仅限文件：[完整路径，精确到文件]
- 禁止修改：[明确列出不能碰的东西]

要求：
1. [具体技术约束]
2. [业务规则]
3. [安全规则]
...
N. 完成后执行：mvn test -Dtest=[精确测试类]

检查点：完成后暂停，等待我 review，不要继续下一步
```

### 4.2 模板 A：填充值对象方法

**适用场景**：`fundflow.domain.vo`、`knowledge.domain.vo` 包内值对象，方法体为空（TODO 或 `UnsupportedOperationException`）

```markdown
/task: 实现 [文件名] 中所有 TODO 的方法

范围：
- 仅限文件：[完整路径]
- 禁止修改：方法签名、类名、包名、字段定义、import 语句

要求：
1. 使用 Java 17 语法，保持不可变性（返回新实例）
2. 所有校验逻辑在构造器/方法内完成
3. 异常使用 IllegalArgumentException（业务非法）、NullPointerException（空值）
4. 异常消息包含具体原因（如"金额不能为负数: " + amount）
5. 完成后执行：mvn test -Dtest=[测试类名]
6. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review，不要继续下一步
```

**实战案例**（Money.java）：
```markdown
/task: 实现 Money.java 中所有 TODO 的方法

范围：
- 仅限文件：src/main/java/com/example/finhub/fundflow/domain/vo/Money.java
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

### 4.3 模板 B：实现领域服务

**适用场景**：`fundflow.domain.service`、`knowledge.domain.service` 接口实现，需要依赖注入

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径]
- 接口定义：[接口完整路径]（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 严格按接口 Javadoc 实现
2. 使用构造器注入依赖，禁止 @Autowired 字段注入
3. 日志使用 SLF4J，禁止打印敏感字段（金额、对方户名、备注）
4. 可打印字段：[明确列出可打印字段]
5. 领域服务无状态：禁止在类中定义 List/Map 缓存字段
6. 完成后执行：mvn test -Dtest=[测试类名]
7. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

**实战案例**（DeduplicationServiceImpl）：
```markdown
/task: 实现 DeduplicationServiceImpl 的 deduplicate 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/service/DeduplicationServiceImpl.java
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

### 4.4 模板 C：生成防腐层实现

**适用场景**：`fundflow.acl`、`knowledge.acl` 接口实现，涉及外部技术细节

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径]
- 接口定义：[接口完整路径]（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. [明确支持/不支持的格式/协议]
2. [编码/异常处理规则]
3. [返回类型限制，禁止直接创建聚合根]
4. 异常行处理：[跳过并记录 WARN / 阻断 / 其他]
5. 完成后执行：mvn test -Dtest=[测试类名]
6. 若测试失败，报告失败信息，不自动修改测试

已知限制：
- [明确列出不支持的场景，直接抛 UnsupportedOperationException]

检查点：完成后暂停，等待我 review
```

**实战案例**（AlipayCSVAdapter）：
```markdown
/task: 实现 AlipayCSVAdapter 的 adapt 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/acl/AlipayCSVAdapter.java
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

### 4.5 模板 D：生成基础设施实现

**适用场景**：Repository、Cache、VectorStore 等技术框架实现

```markdown
/task: 实现 [实现类名] 的 [方法名] 方法

范围：
- 实现文件：[完整路径]
- 接口定义：[接口完整路径]（禁止修改）
- 禁止修改：接口文件、测试文件、Mapper 接口

要求：
1. [技术约束，如批量大小、事务边界]
2. [性能优化要求]
3. 事务边界：由应用层 @Transactional 控制，Repository 内不开启新事务
4. 完成后执行：mvn test -Dtest=[测试类名]
5. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 4.6 模板 E：补测试

**适用场景**：为已实现类补充单元测试，覆盖边界、并发、异常场景

```markdown
/test: 为 [类名] 生成单元测试

范围：
- 测试文件：src/test/java/[对应包路径]/[类名]Test.java
- 禁止修改：已有测试方法、断言、测试类名

要求：
1. 覆盖场景：[明确列出需要覆盖的场景]
2. 使用 JUnit 5 + AssertJ
3. 测试方法命名：should[Expected]When[Condition]
4. 异常测试使用 assertThat[Exception]().isThrownBy(...)
5. 参数化测试使用 @ParameterizedTest + @ValueSource / @CsvSource
6. 完成后执行：mvn test -Dtest=[测试类名]
7. 若测试失败，报告失败信息，不自动修改实现代码

检查点：完成后暂停，等待我 review
```

---

## 五、第三层：审计（Git Pipeline）

### 5.1 任务前：保存基线

```bash
git commit -m "wip: before claude task [任务描述]"
```

### 5.2 任务后：范围审查

```bash
# 先看数量
git diff --stat
# 预期：1 个文件（符合模板范围）
# 实际：3 个文件 → 警觉，找出越界文件

# 再看质量
git diff src/.../TargetFile.java
# 检查点：
# - 方法签名没变？
# - 无 Spring 注解进入 domain 包？
# - 无新增 import？
# - 无 System.out.println？
```

### 5.3 选择性添加（关键防线）

```bash
# 逐块审查，可疑代码块输入 'n' 拒绝
git add -p
```

### 5.4 带审计信息的提交

```bash
git commit -m "feat(scope): 描述

Claude-Task: [模板编号]
Reviewed-By: [你的名字]
Changes: [文件列表]"
```

### 5.5 审查清单

- [ ] `git diff --stat` 只改了 1 个文件（符合模板范围）
- [ ] 没有新增/删除/重命名包
- [ ] 没有修改接口方法签名
- [ ] `fundflow.domain` 包无 Spring 注解（`@Autowired`、`@Service`、`@Transactional`）
- [ ] 值对象不可变（final 字段或 Record）
- [ ] 无 `public setter`
- [ ] 无 `System.out.println`
- [ ] 无敏感数据明文打印（金额、对方户名、备注）
- [ ] `mvn test -Dtest=[测试类名]` 全部通过
- [ ] 无测试文件被修改
- [ ] `pom.xml` / `application.yml` 无变更

---

## 六、快速指令集

| 意图 | 指令 | 使用场景 |
|------|------|---------|
| 紧急停止 | `/stop: 立即停止当前任务，保留已修改文件，报告当前进度和未完成项` | AI 失控、需求变更、发现重大设计缺陷 |
| 回滚变更 | `/revert: 回滚本次任务所有修改，恢复到上次 commit，报告回滚文件列表` | Review 不通过、违反禁令、测试大面积失败 |
| 解释代码 | `/explain: 解释 [类名].[方法名] 的业务逻辑，不修改代码，用中文说明` | 理解遗留代码、面试准备、文档编写 |
| 性能优化 | `/optimize: 在不变更接口前提下，优化 [方法名] 性能，完成后报告 benchmark 数据` | 慢查询、内存泄漏、批量操作优化 |
| 安全审查 | `/security: 审查 [文件名] 的安全风险（敏感数据泄露、注入风险、密钥硬编码），不修改代码` | 代码审计、合规检查、面试准备 |
| 生成文档 | `/docs: 为 [类名] 生成 JavaDoc 注释，包含业务规则说明、异常说明、调用示例` | 补文档、API 文档生成 |
| 重构建议 | `/refactor: 在不改变行为前提下，建议 [类名] 的重构方案，报告收益与风险，不修改代码` | 技术债务评估、代码评审 |

---

## 七、演进路径：从失控到自治

| 阶段 | 名称 | 特征 | 人类投入 | AI 产出可控性 | 我的实践 |
|------|------|------|---------|------------|---------|
| **Level 0** | 放任（Wild） | 口头指令，AI 自由发挥 | 低 | 极低 | v0.1：47 个文件，回滚 2 小时 |
| **Level 1** | 审查（Review） | 事后审查，逐行纠错 | 极高 | 低 | v0.2：眼睛盯屏幕，身心俱疲 |
| **Level 2** | 模板化（Template） | 结构化指令，范围锁定 | 中 | 高 | **v1.0 当前**：模板 + 禁令 + 审计 |
| **Level 3** | 契约化（Contract） | 先写测试，AI 让测试通过 | 中 | 极高 | 目标：TDD 模式，测试先行 |
| **Level 4** | 自治化（Autonomous） | AI 自主分解任务，人类验收 | 低 | 高（需强审计） | 未来：AI 生成子任务，人类确认 |

---

## 八、关键认知转变

| 转变前 | 转变后 |
|--------|--------|
| "用 AI 写代码" | "用 AI 填充实现" |
| "AI 帮我完成项目" | "我定义契约，AI 履行契约" |
| "审查 AI 的代码" | "验收 AI 的产出" |
| "AI 是工程师" | "AI 是实习生" |
| "速度第一" | "可控第一，速度第二" |
| "git add ." | "git add -p" |

---

## 九、可复用资产清单

| 资产 | 位置 | 复用方式 |
|------|------|---------|
| 协作宪法 | `CLAUDE_CODE.md` | 新项目复制，按领域调整禁令 |
| 任务模板库 | `docs/claude-code/task-templates.md` | 按场景选择模板，填充参数 |
| 审查清单 | `CLAUDE_CODE.md` Review 章节 | 每次任务后逐项打勾 |
| Git 提交规范 | `CLAUDE_CODE.md` Git 章节 | 统一团队提交信息 |
| 快速指令集 | `CLAUDE_CODE.md` 快速指令章节 | 紧急场景复制粘贴 |
| 本手册 | `docs/ai-harness/playbook.md` | 面试展示、团队培训、个人复盘 |

---

## 十、面试话术（完整版）

> "我使用 Claude Code 的经历分两个阶段。第一阶段是失控期：口头指令'帮我实现导入功能'，结果 AI 改了 47 个文件，加了 Lombok、H2 数据库，还'贴心'修改了测试预期，回滚花了两小时。第二阶段我建立了 **AI Harness 三层管控体系**。
>
> **第一层是宪法**：六条绝对禁令，比如 AI 无权修改架构边界、无权污染核心域、无权引入依赖。违反即回滚，不讨论。
>
> **第二层是法律**：五种场景的任务模板，每次指令精确到文件路径、技术约束、检查点。比如实现 Money 值对象，范围仅限 `Money.java`，禁止改签名，要求 Java 17 Record 语法，精度强制 `setScale(2, HALF_UP)`，toString 脱敏，完成后 `mvn test` 通过并暂停等待 review。
>
> **第三层是审计**：`git diff --stat` 先看范围，`git diff` 逐文件审逻辑，`git add -p` 选择性添加，最后带审计信息提交（`Claude-Task: A-001, Reviewed-By: 我`）。
>
> 这套体系让我从'用 AI 写代码'变成'用 AI 填充实现'。我的精力从逐行审查代码，转移到设计契约（接口、测试、模板），AI 在精确边界内工作，产出可控、可审计、可回滚。
>
> 我认为 AI 时代工程师的核心竞争力不是写代码速度，而是**架构决策能力 + AI 协作管控能力**——也就是'驾驭'而非'被驾驭'。"

---

## 十一、一句话总结

> **AI Harness = 宪法（红线）+ 法律（模板）+ 审计（Git）= 人类握缰绳，AI 拉车，代码质量可追责。**

---

**版本**：v1.0  
**日期**：2026-07-01  
**关联项目**：FinHub 个人资金数据治理中台  
**演进计划**：向 Level 3（契约化/TDD）演进
```

---