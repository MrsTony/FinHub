以下是 **Day 5 执行计划**。Day 1~4 已造好全部零件（值对象、Transaction 聚合根、领域服务接口、4 个基础设施实现、CSV 适配器、建表 SQL），但**端到端导入流水线尚未组装**：`TransactionRepositoryImpl` 7 个方法全是空壳、`IngestionAppService.importFile()` 抛 TODO、Caffeine 缓存 Bean 缺失导致应用无法启动、无入口层。Day 5 的主题是 **"组装 + 闭环"**。

> 三项已定决策：① 入口层 = REST Controller；② 测试库 = 远程 MySQL (121.89.92.242)；③ 范围 = 仅导入流水线（查询/知识库/事件监听器推迟）。

---

## 🔒 TDD 铁律（全局约束，适用于 Day5 全部任务）

**核心规则**：任何新类创建或旧类修改，**必须先写测试契约**。无测试契约，不执行任何代码改动。

### 三段式执行流程（每个 task 都走一遍）

```
RED    先写测试契约（测试类 + 测试方法 + 断言），跑 mvn test -Dtest=<TestClass>
       -> 必须看到测试因「实现缺失 / 抛 UnsupportedOperationException / Bean 不存在」而 FAILED
       -> 若测试因编译错误失败 = 契约写错了，修契约，重新看红
       -> 若测试直接 PASS = 没测到真东西，重写契约

GREEN  写最小实现让测试通过，再跑 mvn test -Dtest=<TestClass> -> 必须 PASS

回归   跑全量 mvn test -> 改动的测试契约 + 之前所有测试契约必须全绿
       -> 任一旧测试红 = 本次改动破坏了既有行为，当场修，不放过
       -> 全绿 = 本 task 完成，进入检查点暂停
```

### 测试契约编写要求

1. **一个测试方法测一个行为**，方法名描述行为（如 `shouldReturnTrueWhenScoreAboveThreshold`），禁止 `test1`
2. **优先用真实代码**，只在「外部依赖（DB/HTTP/Spring Context）不可避免」时用 Mock
3. **边界值必测**：阈值点（0.7/0.8）、null、空串、非法枚举
4. **契约先于实现落盘**：测试类文件先提交 review，实现代码后写
5. **禁止「先写实现再补测试」**：发现已有实现无测试时，先补 RED 测试（看它因现状通过 = 没测到东西，调整断言再看红），再 refactor

### 配置类改动的测试契约（不豁免）

yml / 注解 / @Bean 这类「无显式行为」改动也要有契约：
- `CacheConfig` -> 测试 `@SpringBootTest` 能注入 `Cache<String,Boolean>`、规格（maxSize/expire）正确
- `@MapperScan` + yml salt/key -> 测试 `@SpringBootTest` contextLoads 成功（应用能启动 = 配置装配正确）
- yml 默认值 -> 通过 `@Value` 注入到测试属性断言非空/长度

---

## 📋 Day 5 目标

| 模块 | 产出 | 阻塞项解除 |
| ---- | ---- | ---------- |
| VO 决策方法补全 | `AnomalyScore.isAlert()` / `CategorySuggestion.isAdoptable()` | AppService 编排决策依赖 |
| `TransactionRepositoryImpl` | MyBatis-Plus 实现 7 方法 + Mapper + PO + 装配器 | 持久化闭环 |
| 配置补全 | yml（盐值/密钥）+ `CacheConfig`（Caffeine Bean）+ `@MapperScan` | **应用启动阻塞** |
| `CompositeDataSourceAdapter` | 按文件名路由 Alipay/Wechat | NoUniqueBeanDefinition |
| `IngestionAppService.importFile()` | 端到端编排 9 步 | 核心主线 |
| `IngestionController` | `POST /api/transactions/import` + Knife4j | 入口层 |
| 测试 | AppService 单测 + Repository 集成测 + Controller MockMvc + E2E | 验证 |

---

## Day 0：开工前自检（5 分钟）

```bash
# 1. 确认 Day4 测试全绿（TDD 回归基线）
mvn test

# 2. 确认远程 MySQL 可达（Day5 测试目标库）
#    若不可达：Repository 集成测试将跳过（assumeTrue），但应用本地启动仍需连库
mysql -h 121.89.92.242 -P 3306 -u finhub -p finhub -e "SELECT 1"   # 或用 IDE 数据源面板

# 3. 确认 4 个 Day4 实现类均带 @Service/@Component 注解
#    （git log 显示曾误用 @Server，已修正，复核一遍）
```

> ⚠️ 风险：远程 MySQL 不可达时，`@SpringBootTest` 加载 Context 即失败。Repository 集成测试用 `@Tag("integration")` 标记，CI 默认跳过；本地需保证库可达或手动 `-Dgroups=integration`。

---

## 第一步：补全 VO 决策方法（最小且优先，解锁编排）

### task: 实现 `AnomalyScore.isAlert()` 与 `CategorySuggestion.isAdoptable()`

范围：
- 实现文件：`src/main/java/com/finhub/fundflow/domain/vo/AnomalyScore.java`、`CategorySuggestion.java`
- 禁止修改：record 签名、构造器 compact 构造、其他 VO

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/fundflow/domain/vo/AnomalyScoreTest.java`
  - `shouldReturnTrueWhenScoreAboveThreshold` -> score=0.71, 期望 true
  - `shouldReturnFalseWhenScoreEqualsThreshold` -> score=0.7（边界，Javadoc 写 `> 0.7`，0.7 本身 false）
  - `shouldReturnFalseWhenScoreBelowThreshold` -> score=0.5, 期望 false
- 新增 `src/test/java/com/finhub/fundflow/domain/vo/CategorySuggestionTest.java`
  - `shouldReturnTrueWhenHighConfidenceAndRuleSource` -> confidence=0.81, source="RULE", 期望 true
  - `shouldReturnTrueWhenHighConfidenceAndAiSource` -> source="AI", 期望 true
  - `shouldReturnFalseWhenConfidenceEqualsThreshold` -> confidence=0.8（边界 false）
  - `shouldReturnFalseWhenLowConfidence` -> confidence=0.5
  - `shouldReturnFalseWhenSourceIsUnknown` -> source="MANUAL", confidence=0.9, 期望 false

**执行流程**：
1. **RED**：写两个测试类，跑 `mvn test -Dtest=AnomalyScoreTest,CategorySuggestionTest` -> 因 `isAlert()`/`isAdoptable()` 抛 `UnsupportedOperationException("TODO")` 而 FAILED（确认失败原因正确，非编译错误）
2. **GREEN**：实现 `isAlert()` = `score.compareTo(new BigDecimal("0.7")) > 0`；`isAdoptable()` = `confidence.compareTo(new BigDecimal("0.8")) > 0 && (source.equals("RULE") || source.equals("AI"))`。跑同命令 -> PASS
3. **回归**：`mvn test` 全绿

要求：
1. `AnomalyScore.isAlert()`：`score > 0.7`（Javadoc 已声明阈值），用 `BigDecimal.compareTo`，禁止 `doubleValue()`
2. `CategorySuggestion.isAdoptable()`：`confidence > 0.8` 且 `source` 为 `"RULE"` 或 `"AI"`（与 TransactionClassifierImpl 返回的 source="RULE" 对齐）

检查点：完成后暂停，等待 review

---

## 第二步：实现 `TransactionRepositoryImpl`（MyBatis-Plus）

### task: 实现 Repository 7 方法 + Mapper + PO + 装配器

范围：
- 实现文件：`src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java`
- 新增文件：
  - `infrastructure/repository/mapper/TransactionMapper.java`（`extends BaseMapper<TransactionPO>`）
  - `infrastructure/repository/po/TransactionPO.java`（持久化对象，带 `@TableName/@TableField/@TableId`）
  - `infrastructure/repository/TransactionConverter.java`（领域 ↔ PO 转换，静态方法即可）
- 修改文件：`FinHubApplication.java` 增加 `@MapperScan("com.finhub.**.infrastructure.repository.mapper")`
- 禁止修改：`TransactionRepository` 接口、`Transaction` 聚合根、`fin_transactions` 建表 SQL

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/fundflow/infrastructure/repository/TransactionConverterTest.java`（**纯单测，不依赖 DB，最先做**）
  - `shouldConvertDomainToPoWithAllFields` -> Money->amount+currency、EncryptedString->cipherText、Fingerprint->hashValue（salt 不入库）
  - `shouldConvertPoToDomainWithSentinelSalt` -> PO 重建 Fingerprint 时 salt="PERSISTED"，hashValue 对齐
  - `shouldRoundTripMoneyAmount` -> 往返金额精度不丢
  - `shouldHandleNullExternalId` -> externalId 为 null 的 Transaction 转换不报错
- 新增 `src/test/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImplTest.java`（**`@SpringBootTest` + 远程 MySQL + `@Tag("integration")` + `@Transactional` 回滚**）
  - `@BeforeAll` 用 `SELECT 1` 探活，不可达 `assumeTrue(false)` 跳过整类
  - `shouldSaveAndFindByExternalId` -> save 后 findByExternalId 命中，金额/方向/分类/fingerprint.hashValue 一致
  - `shouldSaveAndFindByFingerprint` -> 按 fingerprint hashValue 命中
  - `shouldReturnEmptyWhenExternalIdNotExists` -> 查不到返回 Optional.empty
  - `shouldSaveBatchAndCount` -> saveBatch N 条后 count 增加 N
  - `shouldFindByCategoryAndTimeRange` -> 时间区间 + 分类过滤命中
  - 注意：`@Transactional` 自动回滚，不污染远程库

**执行流程**：
1. **RED（Converter）**：先建空壳 `TransactionConverter`（方法签名抛 UnsupportedOperationException），写 `TransactionConverterTest`，跑 -> FAILED（UnsupportedOperationException）。确认红
2. **GREEN（Converter）**：实现 toPO/toDomain，跑 Converter 测试 -> PASS
3. **RED（Repository）**：写 `TransactionRepositoryImplTest`，跑 -> 各方法抛 UnsupportedOperationException -> FAILED（DB 可达时）；DB 不可达 -> assumeTrue 跳过（需先确认 DB 可达性）
4. **GREEN（Repository）**：新建 PO/Mapper，装配 Converter，实现 7 方法，`FinHubApplication` 加 `@MapperScan`，跑 Repository 测试 -> PASS
5. **回归**：`mvn test` 全绿（含 Day1~4 所有旧测试）

要求：
1. **PO 设计**（`@TableName("fin_transactions")`）：字段与建表 SQL 一一对应。MyBatis-Plus 驼峰映射（yml 已开）。枚举处理：`direction`/`category` 用 `EnumOrdinalTypeHandler`（yml 已配全局枚举处理器）或 `@EnumValue`，**实现前确认 Direction/Category 枚举是否有 `@EnumValue` 字段或 ordinal 与 DB 存储一致**（DB 的 category 是 VARCHAR 中文名？还是枚举名？需对齐建表 SQL 的 CHECK 约束值）

2. **Converter（领域 ↔ PO）**：
   - `toPO(Transaction)`：`Money` -> `amount`+`currency`；`EncryptedString` -> `cipherText`（直存密文）；`Fingerprint` -> `hashValue`（**salt 不入库**）；其余直映
   - `toDomain(TransactionPO)`：逆转换。**关键决策**：`Fingerprint` 重建时 `salt` 用哨兵值 `"PERSISTED"`（构造器强制非空非 blank；`matches()` 只比 `hashValue`，哨兵不影响匹配/排重）；`EncryptedString` 重建用 `new EncryptedString(po.getCounterpartyCipher())`（record 构造器收密文，无需密钥）

3. **7 方法实现**：见测试契约，对应实现 findById/findByExternalId/findByFingerprint/findByCategoryAndTimeRange/save/saveBatch/count

4. **不回填聚合根 id（MVP 决策）**：导入流程中 Transaction 用后即弃，`save/saveBatch` 不把 PO id 写回领域对象（Transaction 无 setId，亦不新增）。**领域事件中的 transactionId 在 save 前注册、此时为 null，属已知缺口**（Day5 无监听器，不触发；Day6+ 落地监听器时通过 `assignPersistedId` 或事件 enrichment 解决，记 TODO）

5. **异常**：DB 异常自然上抛（不吞），交由 `@Transactional` 回滚 + AppService 统一处理

检查点：完成后暂停，等待 review

---

## 第三步：补全配置（解除应用启动阻塞）

### task: 补 yml 配置项 + Caffeine Cache Bean + @MapperScan

范围：
- 修改文件：`src/main/resources/application.yml`、`FinHubApplication.java`
- 新增文件：`src/main/java/com/finhub/infra/config/CacheConfig.java`

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/infra/config/CacheConfigTest.java`（`@SpringBootTest`，验证配置装配）
  - `shouldProvideDedupCacheBean` -> `@Autowired Cache<String,Boolean>` 非空，类型匹配
  - `shouldHaveCaffeineSpec` -> 缓存实例是 `Caffeine` 实现（通过 put/get 验证可用）
- 新增 `src/test/java/com/finhub/FinHubApplicationTest.java`（`@SpringBootTest` contextLoads）
  - `contextLoads` -> 应用上下文加载成功（隐含验证：@MapperScan 扫描、yml salt/key 注入、Cache Bean 存在、远程 MySQL 连通/Flyway baseline）
  - **DB 不可达时此测试 FAILED 而非跳过**（Context 加载阶段即连库）-> 执行前先确认远程 MySQL 可达；若不可达，本地用 `-Dtest=!FinHubApplicationTest` 排除，或临时切 H2（违反决策②，需与你确认）

**执行流程**：
1. **RED**：写 `CacheConfigTest` + `FinHubApplicationTest`，跑 -> 因 `CacheConfig` 不存在 / 应用因缺 Cache Bean 启动失败 -> FAILED。确认红（失败原因是 Bean 缺失，非编译错误）
2. **GREEN**：写 `application.yml` 配置项 + `CacheConfig` Bean + `@MapperScan`，跑两个测试 -> PASS
3. **回归**：`mvn test` 全绿（注意：contextLoads 跑起来后，Repository 集成测也共享同一 Context，DB 可达性影响一致）

要求：
1. **application.yml** 增加：
   ```yaml
   finhub:
     fingerprint:
       salt: ${FINHUB_FINGERPRINT_SALT:dev-salt-please-override-32b}   # 32+ 字节
     encryption:
       key: ${FINHUB_ENCRYPTION_KEY:dev-key-please-override-32bytes!!}  # AES-256 必须 32 字节
   ```
   - 同步更新 `.env.example`
   - 加密密钥必须 32 字节（`EncryptedString.validateKeyLength` 强校验），dev 默认值需满足长度

2. **`CacheConfig`**（`@Configuration`）：
   ```java
   @Bean
   public Cache<String, Boolean> dedupCache() {
       return Caffeine.newBuilder()
           .maximumSize(10_000)
           .expireAfterWrite(Duration.ofHours(24))
           .build();
   }
   ```
   - Bean 名 `dedupCache`，`DeduplicationServiceImpl` 按 `Cache<String,Boolean>` 类型注入即可
   - **当前缺此 Bean，应用无法启动**（DeduplicationServiceImpl 构造器注入 Cache）

3. **`@MapperScan`**：`FinHubApplication` 加 `@MapperScan("com.finhub.**.infrastructure.repository.mapper")`

检查点：完成后暂停，等待 review（含 `mvn spring-boot:run` 手验应用能起来）

---

## 第四步：实现 `CompositeDataSourceAdapter`（多数据源路由）

### task: 按文件名路由 Alipay/Wechat 适配器

范围：
- 新增文件：`src/main/java/com/finhub/fundflow/infrastructure/adapter/CompositeDataSourceAdapter.java`
- 实现 `DataSourceAdapter` 接口
- 禁止修改：`DataSourceAdapter` 接口、`AlipayCSVAdapter`、`WechatCSVAdapter`、`RawRecord`

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/fundflow/infrastructure/adapter/CompositeDataSourceAdapterTest.java`（纯单测，Mock 两个 delegate）
  - `shouldRouteToAlipayWhenFilenameContainsAlipay` -> filename="alipay_2024.csv"，验证 AlipayCSVAdapter.adapt 被调
  - `shouldRouteToAlipayWhenFilenameContainsChineseKeyword` -> filename="支付宝账单.csv"，同上
  - `shouldRouteToWechatWhenFilenameContainsWechat` -> filename="微信账单.csv"
  - `shouldThrowWhenSourceUnknown` -> filename="bank.csv"，期望 IllegalArgumentException 含"无法识别数据源"
  - `shouldThrowWhenFilenameNull` -> null 校验
  - `shouldPassThroughInputStreamAndFilename` -> 验证 delegate.adapt 收到原始 stream + filename（透传不篡改）

**执行流程**：
1. **RED**：先建空壳 `CompositeDataSourceAdapter`（adapt 抛 UnsupportedOperationException），写测试，跑 -> FAILED。确认红
2. **GREEN**：实现路由 + 透传，跑测试 -> PASS
3. **回归**：`mvn test` 全绿

要求：
1. **目的**：`IngestionAppService` 注入单个 `DataSourceAdapter`，但 Alipay/Wechat 两个都是 bean，会 `NoUniqueBeanDefinitionException`。Composite 作为**主 bean**（`@Primary` 或 `@Component` + 唯一实现接口注入）路由分发

2. **路由规则**（按 filename 关键字，大小写不敏感）：
   - 含 `alipay` 或 `支付宝` -> AlipayCSVAdapter
   - 含 `wechat` 或 `微信` -> WechatCSVAdapter
   - 其他 -> `IllegalArgumentException("无法识别数据源: " + filename)`

3. **注入**：构造器注入 `List<DataSourceAdapter>` 或显式两个 `AlipayCSVAdapter`/`WechatCSVAdapter`（推荐显式，避免歧义）

4. **透传**：选定适配器后调用 `delegate.adapt(inputStream, filename)`，异常自然上抛

检查点：完成后暂停，等待 review

---

## 第五步：实现 `IngestionAppService.importFile()` 编排（核心主线）

### task: 端到端编排 9 步

范围：
- 实现文件：`src/main/java/com/finhub/fundflow/application/IngestionAppService.java`
- 禁止修改：6 个依赖字段、`ImportResult` record 签名、领域层

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/fundflow/application/IngestionAppServiceTest.java`（纯单测，Mock 6 依赖 + 注入 salt/key）
  - `shouldImportAllValidRecords` -> 2 条有效 RawRecord，imported=2，saveBatch 被调 1 次（size=2），事件已发布并 clear
  - `shouldSkipInvalidDirectionRow` -> 含 1 条 Direction 非法行，skipped+1，imported 不受影响
  - `shouldSkipNullAmountRow` -> amount=null，skipped+1
  - `shouldDeduplicateBeforeSave` -> 2 条相同 externalId，imported=1（deduplicate 过滤）
  - `shouldClassifyWhenSuggestionAdoptable` -> Mock classifier 返回 confidence=0.9 source="RULE"，验证 markClassified 被调（tx.category 变更）
  - `shouldNotClassifyWhenSuggestionNotAdoptable` -> confidence=0.5，markClassified 不被调
  - `shouldMarkAnomalyWhenDetectorReturnsScore` -> Mock detector 返回 alert 分值，tx.markAnomaly 被调
  - `shouldPublishDomainEvents` -> 验证 eventPublisher.publishEvent 被调用（次数 = 事件总数），事件 clear
  - `shouldThrowWhenAdaptFails` -> Mock adapter 抛 IllegalArgumentException，透传
  - `shouldRejectNullInputStream` / `shouldRejectNullFilename` -> 400 校验

**执行流程**：
1. **RED**：写 `IngestionAppServiceTest`，跑 -> 因 `importFile()` 抛 UnsupportedOperationException -> FAILED。确认红
2. **GREEN**：实现 9 步编排 + 注入 `encryptionKey`，跑测试 -> PASS
3. **回归**：`mvn test` 全绿

要求：
1. **输入校验**：`inputStream`/`filename` 为 null -> `IllegalArgumentException`

2. **编排 9 步**：
   1. `dataSourceAdapter.adapt(inputStream, filename)` -> `List<RawRecord>`（Composite 自动路由）
   2. 注入加密密钥（`@Value("${finhub.encryption.key}")`，需在构造器或字段注入--当前 `fingerprintSalt` 已占位，补 `encryptionKey`）
   3. 遍历 `RawRecord`，**逐条 try/catch 容错**，构建 `Transaction`：
      - `Direction` 解析：`RawRecord.direction()`(String) 支持 `"IN"`/`"OUT"` 与 `"收入"`/`"支出"`，无法识别 -> 记 WARN，跳过该行（计入 skipped）
      - `Money`：`new Money(amount, currency)`（amount/currency 为 null -> 跳过）
      - `EncryptedString`：`fromPlain(counterparty, encryptionKey)`、`fromPlain(remark, encryptionKey)`（remark 为 null -> `fromPlain("__EMPTY__", key)` 与 `FingerprintGenerator` 占位逻辑一致）
      - `Fingerprint`：`fingerprintGenerator.generate(counterparty明文, money, transTime, remark明文, fingerprintSalt)`
      - `Transaction.createFrom(externalId, money, direction, Category.UNCLASSIFIED, transTime, encCounterparty, encRemark, fingerprint, sourceSystem)`
      - 单条构建异常（不变量违例等）-> 记 WARN，跳过，计入 skipped，**不阻断整批**
   4. `deduplicationService.deduplicate(candidates)` -> 去重列表（三重防重：external_id -> fingerprint -> Caffeine 缓存 + DB 查重）
   5. 逐条 `transactionClassifier.classify(tx)` -> `CategorySuggestion`；`isAdoptable()` 为 true -> `tx.markClassified(suggestion.category(), suggestion.source())`
   6. `anomalyDetector.detect(deduped)` -> `Map<key, AnomalyScore>`；对命中 tx 调 `tx.markAnomaly(score)`
   7. `transactionRepository.saveBatch(deduped)`（`@Transactional` 已在类上）
   8. **发布领域事件**：遍历每个 tx 的 `getDomainEvents()`，`eventPublisher.publishEvent(e)`，然后 `tx.clearDomainEvents()`（注意：事件在 save 前注册，transactionId 为 null，属已知缺口）
   9. 返回 `ImportResult(imported=deduped.size(), skipped=去重跳过+无效行, failed=adapt 异常数)`

3. **日志**：SLF4J，打印 imported/skipped/failed 计数、sourceSystem、fpHash 前 8 位；**禁止打印金额、户名、备注明文**

4. **异常**：`adapt()` 整体失败（编码/格式严重错误）-> 抛 `IllegalArgumentException` 交 Controller 处理；单条失败不阻断

检查点：完成后暂停，等待 review

---

## 第六步：实现 `IngestionController`（REST 入口）

### task: POST /api/transactions/import

范围：
- 新增文件：`src/main/java/com/finhub/fundflow/interfaces/IngestionController.java`（建议新增 `interfaces` 层包，与 DDD 分层对齐）
- 禁止修改：`SecurityConfig`（已有 Basic Auth 保护全部路由）、AppService

**测试契约（先行，先落盘 review）**：
- 新增 `src/test/java/com/finhub/fundflow/interfaces/IngestionControllerTest.java`（`@WebMvcTest(IngestionController.class)` + `@MockBean IngestionAppService` + Basic Auth）
  - `shouldReturn200WithCountsWhenUploadValidCsv` -> MockMultipartFile 上传，Mock appService 返回 ImportResult(2,0,0)，期望 200 + JSON body 含 imported=2
  - `shouldReturn400WhenFileIsEmpty` -> 空 MultipartFile，期望 400
  - `shouldReturn400WhenFilenameNull` -> 期望 400
  - `shouldReturn400WhenAppServiceThrowsIllegalArgument` -> Mock 抛 IllegalArgumentException，期望 400 + 错误消息
  - `shouldReturn401WithoutAuth` -> 无 Basic Auth，期望 401
  - `shouldReturn500OnUnexpectedException` -> Mock 抛 RuntimeException，期望 500
  - Knife4j：`@WebMvcTest` 不验证注解本身，注解正确性靠 review + 启动后访问 `/doc.html`

**执行流程**：
1. **RED**：先建空壳 `IngestionController`（端点抛 UnsupportedOperationException 或返回空），写 `IngestionControllerTest`，跑 -> FAILED。确认红
2. **GREEN**：实现端点 + 异常处理 + Knife4j 注解，跑测试 -> PASS
3. **回归**：`mvn test` 全绿

要求：
1. **端点**：`POST /api/transactions/import`，接收 `@RequestParam("file") MultipartFile file`
2. **校验**：`file.isEmpty()` -> 400；`originalFilename` 为 null -> 400
3. **调用**：`appService.importFile(file.getInputStream(), file.getOriginalFilename())`
4. **响应**：返回 `IngestionAppService.ImportResult`（JSON：`{imported, skipped, failed}`），200
5. **异常处理**：`IllegalArgumentException` -> 400 + 错误消息；其他 -> 500（可用 `@RestControllerAdvice` 全局处理，MVP 也可方法内 try/catch）
6. **Knife4j/OpenAPI**：`@Tag`、`@Operation`、`@Parameter` 注解（项目已引 springdoc 2.5.0；遵循 dgg-spring-style 规范）
7. **认证**：复用 `SecurityConfig` 的 Basic Auth，无需额外配置

检查点：完成后暂停，等待 review

---

## 第七步：端到端集成测试 + 全量回归验收

### task: 真实 CSV 全链路 + 回归闸门

> 前面各步的单测/集成测/Controller 测已在各自 task 内以 RED->GREEN->回归 完成。本步聚焦**端到端**验证与**最终回归闸门**。

范围：
- 新增文件：`src/test/java/com/finhub/fundflow/interfaces/IngestionEndToEndTest.java`（可选，按真实账单文件是否存在而走）

要求：
1. **端到端集成测试**（`@SpringBootTest` + `@AutoConfigureMockMvc` + 远程 MySQL + `@Tag("integration")`，参考 `RealBillingFileIntegrationTest` 范式）：
   - `@BeforeAll` 探活 DB + 探活真实账单文件 `D:/dev/文档/账单/`（`assumeTrue` 跳过机制）
   - `shouldImportRealAlipayCsvEndToEnd` -> 上传真实 alipay CSV，期望 200 + imported>0，且库中 `findByExternalId` 能查回
   - `shouldImportRealWechatCsvEndToEnd` -> 同上微信
   - `@Transactional` 回滚，不污染远程库（注意：若 AppService 内部已 `@Transactional`，嵌套行为需确认传播策略）

2. **全量回归闸门（Day5 完成的硬性验收）**：
   ```bash
   mvn test
   ```
   - 必须全绿，**零失败零跳过**（integration 标签因 DB 不可达跳过除外，但需在报告中明示）
   - 任一旧测试红 = Day5 改动破坏了既有行为，**当场修复，不算完成**
   - 对比 Day4 EOD 的测试基线：Day5 新增测试数 + 全部旧测试仍绿

3. **验证清单**（逐条勾选后本 task 才算完成）：
   - [ ] Day1~4 所有旧测试（Money/Category/Fingerprint/EncryptedString/Transaction/4 个 Impl/2 个 CSV 适配器/Deduplication）全绿
   - [ ] Day5 新增测试（VO 决策/Converter/Repository/CacheConfig/AppService/Composite/Controller/E2E）全绿
   - [ ] `mvn test` 退出码 0

检查点：完成后暂停，等待 review

---

## 今日检查清单（Day 5 EOD）

**TDD 硬约束验收（每条都需勾选）**：
- [ ] 每个 task 都先写了测试契约并落盘 review
- [ ] 每个 task 都看过 RED（测试因实现缺失而 FAILED，非编译错误）
- [ ] 每个 task 的 GREEN 实现后，改动的测试契约 PASS
- [ ] 每个 task 完成后跑了全量 `mvn test`，旧测试无一回归

**功能交付**：
- [ ] `AnomalyScore.isAlert()` / `CategorySuggestion.isAdoptable()` 实现并测试通过
- [ ] `TransactionRepositoryImpl` 7 方法实现 + Mapper + PO + Converter，测试通过
- [ ] `application.yml` 补 salt/key，`.env.example` 同步
- [ ] `CacheConfig` 提供 Caffeine `Cache<String,Boolean>` Bean，应用可启动
- [ ] `FinHubApplication` 加 `@MapperScan`
- [ ] `CompositeDataSourceAdapter` 路由实现并测试通过
- [ ] `IngestionAppService.importFile()` 9 步编排实现，单测通过
- [ ] `IngestionController` REST 端点 + Knife4j 注解，MockMvc 测试通过
- [ ] E2E 集成测试（真实 CSV）通过或按 assumeTrue 跳过

**收尾**：
- [ ] `mvn test` 全绿（退出码 0）
- [ ] 手测：`curl -u admin:xxx -F "file=@alipay.csv" http://localhost:8080/api/transactions/import` 返回 counts
- [ ] README 补 Day 5 进度
- [ ] Git commit：`✨ feat(fundflow): 实现 Day5 导入流水线端到端闭环`
- [ ] 已知缺口记录：聚合根 id 不回填、领域事件 transactionId 为 null（待 Day6+ 监听器）

---

## 下一步预告（Day 6）

| 任务 | 内容 |
| ---- | ---- |
| 领域事件监听器 | `@EventListener` 消费 TransactionClassifiedEvent/AnomalyDetectedEvent（日志/通知） |
| `assignPersistedId` | 事件 transactionId 回填 |
| `QueryAppService` | 查询上下文 + `QueryRouter` 责任链 |
| MCP Tool | `McpToolDispatcher` 实现类（导入/查询工具暴露给 AI 客户端） |
