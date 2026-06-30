以下是可直接保存到项目根目录的 `CLAUDE_CODE.md` 文件，作为你与 Claude Code 协作的**强制性规范文档**。

---
> 术语规范：docs/ubiquitous-language.md
```markdown
# FinHub — Claude Code 协作规范 v1.0

> **核心原则**：Claude Code 是"高级实习生"，你是"架构师 + Code Reviewer"。  
> 所有架构决策、业务规则、安全策略由你把控；Claude Code 仅在限定范围内填充实现。

---

## 一、角色边界

| 职责 | 你（人类） | Claude Code（AI） |
|------|-----------|-----------------|
| 架构决策（限界上下文、聚合根边界、防腐层设计） | ✅ 唯一决策者 | ❌ 无权建议变更 |
| 业务规则（不变量、校验逻辑、阈值配置） | ✅ 定义与审核 | ❌ 无权修改 |
| 技术选型（框架版本、依赖引入、部署配置） | ✅ 决定 | ❌ 无权引入新依赖 |
| 接口/签名设计（方法名、参数、返回值、异常） | ✅ 设计 | ❌ 无权变更 |
| 测试契约（测试类、断言、覆盖率目标） | ✅ 设计 | ❌ 无权修改 |
| 代码实现（方法体、算法、数据转换） | ❌ 不亲自写 | ✅ 在限定范围内生成 |
| 单元测试实现（boilerplate、数据构造） | ❌ 不亲自写 | ✅ 基于你的契约生成 |
| 文档注释（JavaDoc、实现注释） | ❌ 不亲自写 | ✅ 生成 |

---

## 二、绝对禁令（Red Lines）

违反任何一条，任务立即中止，已修改文件回滚。

### 禁令 1：架构边界不可侵犯
- 禁止新增 / 删除 / 重命名包（package）
- 禁止移动类到不同限界上下文
- 禁止修改接口方法签名（参数类型 / 顺序、返回值、异常声明）
- 禁止修改类继承关系、实现接口

### 禁令 2：核心域纯净不可污染
- 禁止在 `fundflow.domain`、`knowledge.domain`、`ai.acl` 等核心包引入 Spring 注解（`@Service`、`@Autowired`、`@Transactional` 等）
- 禁止在领域层调用 `Repository`、`HttpClient`、`Cache`、`DataSource`
- 禁止在值对象 / 聚合根中使用 `new` 创建基础设施对象（如 `new RestTemplate()`）

### 禁令 3：聚合根不变量不可绕过
- 禁止为聚合根生成 `public` setter 方法
- 禁止在构造器外修改 `final` 字段
- 禁止绕过工厂方法直接 `new` 聚合根（如 `new Transaction()` 而非 `Transaction.createFrom(...)`）
- 禁止在聚合根方法中直接抛出技术异常（如 `SQLException`），必须转换为领域异常

### 禁令 4：依赖引入不可擅自
- 禁止修改 `pom.xml` 引入新 Maven 依赖
- 禁止修改 `build.gradle` 引入新 Gradle 依赖
- 禁止修改 `application.yml` / `application.properties` 新增配置项
- 禁止修改 `docker-compose.yml` 新增服务或卷

### 禁令 5：测试契约不可修改
- 禁止修改 `src/test` 目录下任何文件
- 禁止删除 / 重命名 / 移动测试类或测试方法
- 禁止修改测试断言（assertThat、assertEquals 等）
- 禁止修改测试数据构造（`@BeforeEach` 中的 setup）

### 禁令 6：跨上下文越界不可发生
- 禁止在 `fundflow` 包引用 `knowledge` 包的类（反之亦然）
- 禁止在 `domain` 层直接调用 `ai` 辅助上下文的实现类
- 禁止绕过防腐层（ACL）接口直接调用上游实现
- 禁止在领域服务中直接发布领域事件（应由应用层编排）

---

## 三、任务指令模板

每次向 Claude Code 下达任务，必须使用以下模板之一，明确范围、边界、检查点。

### 模板 A：填充值对象方法

```markdown
任务：实现 [Money.java] 中标记为 TODO 的方法

范围：
- 仅限文件：src/main/java/com/example/finhub/fundflow/domain/vo/Money.java
- 禁止修改：方法签名、类名、包名、字段定义、import 语句

要求：
1. 使用 Java 17 语法，保持不可变性（返回新实例）
2. 所有校验逻辑在构造器 / 方法内完成，异常消息包含具体原因
3. 异常类型：IllegalArgumentException（业务非法）、NullPointerException（空值）
4. 完成后执行：mvn test -Dtest=MoneyTest
5. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review，不要继续下一步
```

### 模板 B：实现领域服务

```markdown
任务：实现 [DeduplicationServiceImpl.java] 的 deduplicate 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/service/DeduplicationServiceImpl.java
- 接口定义：com.example.finhub.fundflow.domain.service.DeduplicationService（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 严格按接口 Javadoc 的三重防重顺序实现（external_id → fingerprint → 缓存预检）
2. 使用构造器注入依赖，禁止 @Autowired 字段注入
3. 日志使用 SLF4J，禁止打印敏感字段（金额、对方户名、备注）
4. 可打印字段：fingerprint 哈希值、external_id、source_system
5. 完成后执行：mvn test -Dtest=DeduplicationServiceTest
6. 若测试失败，报告失败信息，不自动修改测试

检查点：完成后暂停，等待我 review
```

### 模板 C：生成防腐层实现

```markdown
任务：实现 [AlipayCSVAdapter.java] 的 adapt 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/acl/AlipayCSVAdapter.java
- 接口定义：com.example.finhub.fundflow.acl.DataSourceAdapter（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 仅支持 2024 新版支付宝 CSV 格式（字段：交易时间,交易分类,交易对方,对方账号,商品说明,金额,收/支,交易状态,交易订单号,商家订单号,备注,资金状态）
2. 编码自动识别 UTF-8 / GBK，失败时抛 IllegalArgumentException("无法识别文件编码: " + filename)
3. 异常行（如退款记录格式不一致、字段缺失）跳过并记录 WARN 日志，不阻断整个文件
4. 返回 RawRecord 列表，禁止在适配器内直接创建 Transaction 聚合根
5. 金额解析：去除 "¥" 符号，处理 "+/-" 前缀，转换为 BigDecimal
6. 完成后执行：mvn test -Dtest=AlipayCSVAdapterTest

已知限制：
- 不支持微信 / 银行格式，遇到直接抛 UnsupportedOperationException("暂不支持该数据源: " + sourceType)

检查点：完成后暂停，等待我 review
```

### 模板 D：生成基础设施实现（Repository / Cache）

```markdown
任务：实现 [TransactionRepositoryImpl.java] 的 saveBatch 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java
- 接口定义：com.example.finhub.fundflow.domain.repository.TransactionRepository（禁止修改）
- 禁止修改：接口文件、测试文件、MyBatis-Plus Mapper 接口

要求：
1. 使用 MyBatis-Plus BaseMapper 的 insert 方法，或手写 XML 批量 INSERT
2. 若使用 MP saveBatch，必须开启 MySQL rewriteBatchedStatements=true（已在 application.yml 配置）
3. 批量大小：500 条 / 批次，防止内存溢出
4. 事务边界：由应用层 @Transactional 控制，Repository 内不开启新事务
5. 完成后执行：mvn test -Dtest=TransactionRepositoryTest

检查点：完成后暂停，等待我 review
```

---

## 四、代码生成规范

Claude Code 生成的代码必须遵守以下规范。

### 4.1 值对象（Value Object）

```java
// ✅ 正确：Java Record，不可变，构造时校验
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("币种必须为 3 位字母代码: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
    
    public Money add(Money other) {
        // 返回新实例，不修改 this
        return new Money(this.amount.add(other.amount), this.currency);
    }
}

// ❌ 禁止：可变类、public setter、无参构造器
public class BadMoney {
    private BigDecimal amount;          // 禁止：非 final
    public void setAmount(BigDecimal a) { this.amount = a; }  // 禁止：setter
    public BadMoney() {}                // 禁止：无参构造器
}
```

### 4.2 聚合根（Aggregate Root）

```java
// ✅ 正确：私有构造器，工厂方法，状态修改通过业务方法
public class Transaction {
    private Long id;
    private Category category;          // 可变状态，只能通过 markClassified 修改
    
    private Transaction(...) { ... }    // 私有构造器
    
    public static Transaction createFrom(...) {  // 工厂方法
        // 校验所有不变量
        return new Transaction(...);
    }
    
    public void markClassified(Category newCategory, String source) {
        // 业务校验 + 状态修改 + 事件发布
        if (!newCategory.isCompatibleWith(this.direction)) {
            throw new IllegalArgumentException(
                "分类 " + newCategory + " 与方向 " + this.direction + " 不兼容"
            );
        }
        this.category = newCategory;
        registerEvent(new TransactionClassifiedEvent(this.id, newCategory, source));
    }
}

// ❌ 禁止：public 无参构造器、public setter、直接 new
public class BadTransaction {
    public BadTransaction() {}          // 禁止
    public void setCategory(Category c) { this.category = c; }  // 禁止
}
// 外部代码：new Transaction();  // 禁止！必须走工厂方法
```

### 4.3 领域服务（Domain Service）

```java
// ✅ 正确：无状态，构造器注入，纯业务逻辑
@Service
@RequiredArgsConstructor
public class DeduplicationServiceImpl implements DeduplicationService {
    private final TransactionRepository repository;   // 构造器注入
    private final Cache<String, Boolean> cache;       // 构造器注入
    
    @Override
    public List<Transaction> deduplicate(List<Transaction> candidates) {
        // 纯计算，无状态字段，不修改输入对象
        List<Transaction> unique = new ArrayList<>();
        for (Transaction candidate : candidates) {
            // 业务逻辑...
        }
        return unique;
    }
}

// ❌ 禁止：有状态字段、直接操作 HTTP / 文件、开事务
public class BadService {
    private List<Transaction> pending = new ArrayList<>();  // 禁止：有状态
    private HttpClient client;                              // 禁止：领域服务发 HTTP
    private TransactionTemplate txTemplate;                 // 禁止：领域服务开事务
}
```

### 4.4 防腐层（ACL）

```java
// ✅ 正确：接口定义在下游，实现在上游
// fundflow.acl.DataSourceAdapter（接口，由 fundflow 定义）
public interface DataSourceAdapter {
    List<RawRecord> adapt(InputStream inputStream, String filename);
}

// infrastructure 层实现（可依赖技术框架）
@Component
@RequiredArgsConstructor
public class AlipayCSVAdapter implements DataSourceAdapter {
    private final EncodingDetector encodingDetector;  // 技术组件
    
    @Override
    public List<RawRecord> adapt(InputStream inputStream, String filename) {
        // 技术细节隔离在这里
    }
}

// ❌ 禁止：核心域直接依赖技术类
// fundflow.domain 包内出现：
import org.apache.commons.csv.CSVFormat;  // 禁止！
```

### 4.5 应用编排（AppService）

```java
// ✅ 正确：只调度，不决策，开事务，调仓库，发事件
@Service
@RequiredArgsConstructor
@Transactional
public class IngestionAppService {
    private final DataSourceAdapter adapter;
    private final DeduplicationService dedupService;
    private final TransactionClassifier classifier;
    private final TransactionRepository repository;
    private final ApplicationEventPublisher publisher;
    
    public ImportResult importFile(MultipartFile file) {
        // 1. 技术对象转领域对象
        List<RawRecord> records = adapter.adapt(file.getInputStream(), file.getOriginalFilename());
        
        // 2. 创建聚合根（工厂方法自己校验）
        List<Transaction> candidates = records.stream()
            .map(r -> Transaction.createFrom(r, ...))
            .toList();
        
        // 3. 领域服务：排重（业务规则）
        List<Transaction> unique = dedupService.deduplicate(candidates);
        
        // 4. 领域服务：分类建议（业务规则）
        for (Transaction tx : unique) {
            CategorySuggestion suggestion = classifier.classify(tx);
            if (suggestion.isAdoptable()) {
                tx.markClassified(suggestion.category(), suggestion.source());  // 聚合根自己决策
            }
        }
        
        // 5. 基础设施：持久化
        repository.saveBatch(unique);
        
        // 6. 技术机制：发事件
        unique.forEach(tx -> publisher.publish(new TransactionImportedEvent(tx.getId())));
        
        // 7. 技术对象：组装返回
        return new ImportResult(unique.size(), candidates.size() - unique.size());
    }
}

// ❌ 禁止：应用编排写业务规则
public class BadAppService {
    public void importFile(MultipartFile file) {
        for (RawRecord r : records) {
            // 禁止：应用编排直接写排重逻辑
            if (repository.findByExternalId(r.externalId()).isPresent()) continue;
            
            // 禁止：应用编排直接写分类规则
            Category category;
            if (r.counterparty().contains("美团")) category = Category.FOOD;
            else if (...) ...
            
            // 禁止：应用编排直接标记异常
            boolean isAnomaly = r.amount().compareTo(avg.multiply(new BigDecimal("3"))) > 0;
        }
    }
}
```

---

## 五、Review 检查清单

每次 Claude Code 任务完成后，你必须执行以下检查。

### 5.1 架构边界
```bash
git diff --name-only | grep -E "(src/main/java/com/example/finhub/fundflow/domain|src/main/java/com/example/finhub/knowledge/domain|src/main/java/com/example/finhub/ai/acl)" | grep -v "TODO"
# 核心域文件不应被修改（除非明确授权）
```

- [ ] 没有新增 / 删除 / 重命名包
- [ ] 没有移动类到不同上下文
- [ ] 没有修改接口方法签名

### 5.2 核心域纯净
```bash
grep -r "@Autowired\|@Service\|@Transactional\|@Component" src/main/java/com/example/finhub/fundflow/domain/
# 应该为空
```

- [ ] `fundflow.domain`、`knowledge.domain`、`ai.acl` 包无 Spring 注解
- [ ] 领域层无 `Repository`、`HttpClient`、`Cache` 引用
- [ ] 值对象无 `public` setter

### 5.3 安全
```bash
grep -rn "System.out.println\|System.err.println" src/main/java/
# 应该为空（使用 SLF4J）
```

- [ ] 无敏感字段明文打印（金额、对方户名、备注、密钥）
- [ ] 无密钥硬编码（搜索 `private static final String KEY`、`password`、`secret`）
- [ ] 异常消息不包含敏感数据

### 5.4 测试
```bash
mvn test -Dtest=*Test
```

- [ ] 所有测试通过
- [ ] 无测试被修改 / 删除（`git diff src/test` 为空）

### 5.5 依赖
```bash
git diff pom.xml
# 应该为空（除非明确授权）
```

- [ ] `pom.xml` 无变更
- [ ] `application.yml` 无新增配置
- [ ] `docker-compose.yml` 无新增服务

### 5.6 日志审计
```bash
git diff --stat
```

- [ ] 变更范围符合任务预期（如只改 1 个文件，不应出现 5 个）
- [ ] 逐文件 `git diff` 审阅逻辑

---

## 六、异常处理协议

| 场景 | Claude Code 行为 | 你的响应 |
|------|----------------|---------|
| 遇到 TODO 无法填充（技术限制 / 接口设计缺陷） | 抛异常，留 TODO，加注释 `// TODO: 因 [原因] 无法填充，需人工决策` | 决定是否调整接口设计或换技术方案 |
| 测试失败 | 报告失败信息（类名、方法名、断言详情），不自动修改测试 | 你决定修复实现还是调整测试契约 |
| 需要引入新依赖（如 Apache Tika 解析 PDF） | 停止，报告需求：`需要引入 [groupId]:[artifactId]:[version] 用于 [用途]` | 评估是否值得引入，若批准则你手动改 `pom.xml` |
| 发现接口设计缺陷（如参数不足、返回值不够） | 停止，报告问题：`接口 [类名].[方法名] 需要 [变更] 以支持 [场景]` | 你决定是否重构接口（需同步更新测试） |
| 代码超过 50 行 / 方法圈复杂度超过 10 | 主动建议拆分：`建议将 [方法] 拆分为 [子方法1]、[子方法2]，是否确认？` | 评估是否接受建议 |
| 需要修改测试以覆盖新场景 | 停止，报告：`需要新增测试 [测试类名].[测试方法] 覆盖 [场景]` | 你设计测试契约，Claude Code 填充实现 |

---

## 七、Git 协作规范

```bash
# ===== 任务开始前 =====
git stash  # 或 git commit -m "wip: before claude task [任务描述]"

# ===== 任务完成后 =====
git diff --stat        # 先看范围，是否符合预期
git diff               # 再逐文件审阅逻辑
git add -p             # 选择性添加（关键！不要 git add .）

# ===== 通过 Review 后 =====
git commit -m "[type]([scope]): [subject]

[body]

Claude-Task: [任务ID]
Reviewed-By: [你的名字]"
```

### Commit Message 规范

| type | 用途 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(fundflow/domain): 实现 Money 值对象加法与精度校验` |
| `fix` | 修复 | `fix(infra): 修复 MySQL 时区配置为 Asia/Shanghai` |
| `perf` | 性能优化 | `perf(repository): 优化 saveBatch 为手写 XML 批量 INSERT` |
| `refactor` | 重构（无行为变更） | `refactor(domain): 提取 Fingerprint 生成算法为私有方法` |
| `test` | 测试（仅测试变更） | `test(service): 补充 DeduplicationService 并发场景测试` |
| `docs` | 文档 | `docs(adr): 添加 ADR-006 本地 LLM 选型决策` |
| `chore` | 构建/工具 | `chore(deps): 升级 Spring AI 至 1.0.0-M2` |

---

## 八、快速指令集

复制粘贴即可使用。

| 意图 | 指令 |
|------|------|
| 开始新任务 | `/task: 实现 [文件名] 中所有 TODO，范围仅限 [包路径]，完成后暂停等待 review` |
| 紧急停止 | `/stop: 立即停止当前任务，保留已修改文件，报告当前进度和未完成项` |
| 回滚变更 | `/revert: 回滚本次任务所有修改，恢复到上次 commit，报告回滚文件列表` |
| 补测试 | `/test: 为 [类名] 生成单元测试，范围仅限 src/test/[对应包路径]，完成后暂停` |
| 解释代码 | `/explain: 解释 [类名].[方法名] 的业务逻辑，不修改代码，用中文说明` |
| 性能优化 | `/optimize: 在不变更接口前提下，优化 [方法名] 性能，完成后报告 benchmark 数据` |
| 安全审查 | `/security: 审查 [文件名] 的安全风险（敏感数据泄露、注入风险、密钥硬编码），不修改代码` |

---

## 九、失控应急协议

若 Claude Code 违反禁令、擅自修改范围、或生成不可控代码：

```bash
# 1. 立即中断（Ctrl+C 或关闭终端）

# 2. 回滚所有未提交变更
git checkout -- .
git clean -fd  # 若有新增未跟踪文件

# 3. 验证回滚成功
git status  # 应为空

# 4. 检查是否有已提交但未 review 的变更
git log --oneline -5

# 5. 若有误提交，回退
git reset --hard HEAD~1  # 谨慎！确认无重要变更

# 6. 重新启动，使用更严格的任务模板
```

---

## 十、附录：项目结构速查

```
com.example.finhub
├── fundflow                    ← 核心域（资金流水）
│   ├── domain                  ← 领域层：聚合根、值对象、领域服务接口、领域事件、仓库接口
│   │   ├── aggregate           ← Transaction
│   │   ├── vo                  ← Money, Fingerprint, EncryptedString, Category, Direction, AnomalyScore
│   │   ├── service             ← DeduplicationService, TransactionClassifier, AnomalyDetector, FingerprintGenerator（接口）
│   │   ├── event               ← TransactionImportedEvent, DuplicateDetectedEvent, TransactionClassifiedEvent, AnomalyDetectedEvent
│   │   └── repository          ← TransactionRepository（接口）
│   ├── application             ← 应用层：用例编排、事务边界
│   │   ├── IngestionAppService
│   │   └── QueryAppService
│   ├── infrastructure          ← 基础设施层：MyBatis-Plus 实现、缓存实现
│   │   ├── repository          ← TransactionRepositoryImpl, TransactionMapper
│   │   └── cache               ← CaffeineCacheImpl
│   └── acl                     ← 防腐层：CSV 适配器接口
│       └── DataSourceAdapter, RawRecord
├── knowledge                   ← 支撑域（本地知识库）
│   ├── domain                  ← Document, KnowledgeChunk, KnowledgeIndexer, HybridRetriever
│   ├── application             ← DocumentAppService
│   ├── infrastructure          ← ChromaVectorStore, TikaDocumentParserImpl
│   └── acl                     ← DocumentParser, EmbeddingGenerator, RawDocument
├── ai                          ← 支撑域（AI 辅助）
│   └── acl                     ← CategorySuggestionEngine, AnomalyExplainer, NLTranslator（接口）
│   └── infrastructure          ← SpringAiCategorySuggestionEngine, OllamaEmbeddingGenerator
├── query                       ← 支撑域（查询分析）
│   ├── application             ← QueryRouter
│   └── domain                  ← AggregationView
├── datagov                     ← 通用域（数据治理）
│   └── PromptRegistry, DvcPipelineConfig
├── infra                       ← 通用域（基础设施）
│   ├── mcp                     ← McpToolDispatcher, McpToolHandler
│   ├── config                  ← SecurityConfig, CacheConfig
│   └── exception               ← GlobalExceptionHandler
└── FinHubApplication.java
```

---

**版本**：v1.0  
**生效日期**：2026-06-30  
**下次评审**：完成 Phase 1（资金流水上下文核心域）后
```

---

保存为项目根目录 `CLAUDE_CODE.md`，每次启动 Claude Code 时：
1. 打开该文件
2. 复制当前任务对应的模板
3. 粘贴到 Claude Code 对话框

如需调整任何条款，直接修改该文件并提交 Git。