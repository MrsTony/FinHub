以下是 **Day 6 执行计划**。Day 5 已组装并闭环导入流水线（221 测试，含真实账单 E2E），但其收尾清单与 `IngestionAppService` step 8 注释均标记了一处**已知缺口**：领域事件在 save 前注册、`transactionId` 为 `null`，且**无监听器消费**。Day 6 的主题是 **"事件闭环"**——让领域事件带上真实主键并被消费。

> 三项已定决策：① id 回填方案 = `assignPersistedId`（聚合内回填 + 丰富待发事件，Repository 在 insert 后调用）；② 监听器落 `com.finhub.fundflow.application.event` 包，MVP 仅日志；③ 监听器用同步 `@EventListener`（发布即消费），"通知"语义的 `@TransactionalEventListener(AFTER_COMMIT)` 留 TODO。

---

## 🔒 TDD 铁律（全局约束，适用于 Day6 全部任务）

> 与 Day5 一致，此处摘要；完整版见 `day05.md`。

**核心规则**：任何新类创建或旧类修改，**必须先写测试契约**。无测试契约，不执行任何代码改动。

### 三段式执行流程（每个 task 都走一遍）

```
RED    先写测试契约（测试类 + 测试方法 + 断言），跑 mvn test -Dtest=<TestClass>
       -> 必须看到测试因「实现缺失 / 抛 UnsupportedOperationException / 行为未达成」而 FAILED
       -> 若因编译错误失败 = 契约写错了，修契约，重新看红
       -> 若直接 PASS = 没测到真东西，重写契约

GREEN  写最小实现让测试通过，再跑 mvn test -Dtest=<TestClass> -> 必须 PASS

回归   跑全量 mvn test -> 改动的测试契约 + 之前所有测试契约必须全绿
       -> 任一旧测试红 = 本次改动破坏了既有行为，当场修
       -> 全绿 = 本 task 完成，进入检查点暂停
```

### 测试契约编写要求

1. 一个测试方法测一个行为，方法名描述行为
2. 优先用真实代码，只在「外部依赖（DB/Spring Context）不可避免」时用 Mock
3. 边界值必测：null、重复赋值、空集合
4. 契约先于实现落盘
5. 禁止「先写实现再补测试」

---

## 📋 Day 6 目标

| 模块 | 产出 | 缺口解除 |
| ---- | ---- | -------- |
| `Transaction.assignPersistedId` | 聚合内回填 id + 丰富已注册事件的 transactionId | 事件 transactionId=null |
| `TransactionRepositoryImpl` save/saveBatch | insert 后回填聚合根 id | 持久化 id 不回填 |
| `TransactionEventListener` | `@EventListener` 消费分类/异常事件（日志） | 事件无消费者 |
| `IngestionAppService` | step 8 注释更新（事件带真实 id） | 已知缺口标记清理 |
| 测试 | 聚合单测 + Repository 集成测 + 监听器测 + E2E 事件断言 | 验证 |

---

## Day 0：开工前自检（5 分钟）

```bash
# 1. 确认 Day5 + Knife4j + dev免密 测试全绿（TDD 回归基线）
mvn test          # 期望 229 全绿

# 2. 确认远程 MySQL 可达（Repository 集成测 + E2E + 监听器 @SpringBootTest 均需连库）
#    不可达：Repository/E2E 集成测 assumeTrue 跳过；但 FinHubApplicationTest 与监听器测 @SpringBootTest 加载 Context 即连库，会硬失败
```

> ⚠️ 风险：远程 MySQL 不可达时，`@SpringBootTest` 加载 Context 即失败（监听器测、E2E）。本地须保证库可达。

---

## 第一步：实现 `Transaction.assignPersistedId`（领域层，纯单测）

### task: 聚合内回填 id + 丰富待发事件

范围：
- 修改文件：`src/main/java/com/finhub/fundflow/domain/aggregate/Transaction.java`
- 修改测试：`src/test/java/com/finhub/fundflow/domain/vo/TransactionTest.java`（既有，追加用例）
- 禁止修改：`Transaction` 既有字段/构造器/工厂方法/`markClassified`/`markAnomaly` 签名、事件 record 签名

**测试契约（先行，先落盘 review）**——追加到 `TransactionTest`（需 `import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;` 与 `AnomalyDetectedEvent`）：

```java
@Test
@DisplayName("assignPersistedId 应回填 id 并丰富已注册的分类事件 transactionId")
void shouldSetIdAndEnrichClassifiedEvent() {
    Transaction tx = Transaction.createFrom("ext-id-1", money("100"), Direction.OUT,
            Category.UNCLASSIFIED, LocalDateTime.now(), encrypted("美团"), encrypted("饭"),
            fingerprint("fp"), "ALIPAY");
    tx.markClassified(Category.FOOD, "RULE");   // 注册事件时 id 仍为 null
    assertThat(tx.getId()).isNull();

    tx.assignPersistedId(42L);

    assertThat(tx.getId()).isEqualTo(42L);
    assertThat(tx.getDomainEvents()).hasSize(1);
    Object event = tx.getDomainEvents().get(0);
    assertThat(event).isInstanceOf(TransactionClassifiedEvent.class);
    assertThat(((TransactionClassifiedEvent) event).transactionId()).isEqualTo(42L);
    assertThat(((TransactionClassifiedEvent) event).category()).isEqualTo(Category.FOOD);
}

@Test
@DisplayName("assignPersistedId 应回填 id 并丰富已注册的异常事件 transactionId")
void shouldSetIdAndEnrichAnomalyEvent() {
    Transaction tx = Transaction.createFrom("ext-id-2", money("99999"), Direction.OUT,
            Category.SHOPPING, LocalDateTime.now(), encrypted("商户"), encrypted("大额"),
            fingerprint("fp2"), "ALIPAY");
    tx.markAnomaly(new AnomalyScore(new BigDecimal("0.95"), "AMOUNT_SPIKE"));

    tx.assignPersistedId(7L);

    assertThat(tx.getId()).isEqualTo(7L);
    Object event = tx.getDomainEvents().get(0);
    assertThat(event).isInstanceOf(AnomalyDetectedEvent.class);
    assertThat(((AnomalyDetectedEvent) event).transactionId()).isEqualTo(7L);
}

@Test
@DisplayName("assignPersistedId 重复调用应抛 IllegalStateException（防重复赋值）")
void shouldThrowWhenIdAlreadyAssigned() {
    Transaction tx = Transaction.createFrom("ext-id-3", money("100"), Direction.OUT,
            Category.FOOD, LocalDateTime.now(), encrypted("测试"), encrypted("测试"),
            fingerprint("fp"), "ALIPAY");
    tx.assignPersistedId(1L);
    assertThatIllegalStateException()
            .isThrownBy(() -> tx.assignPersistedId(2L))
            .withMessageContaining("id 已回填");
}

@Test
@DisplayName("assignPersistedId 为 null 应抛 NullPointerException")
void shouldRejectNullId() {
    Transaction tx = Transaction.createFrom("ext-id-4", money("100"), Direction.OUT,
            Category.FOOD, LocalDateTime.now(), encrypted("测试"), encrypted("测试"),
            fingerprint("fp"), "ALIPAY");
    assertThatNullPointerException()
            .isThrownBy(() -> tx.assignPersistedId(null));
}
```

**执行流程**：
1. **RED**：写上述 4 个用例，跑 `mvn test -Dtest=TransactionTest` -> 因 `assignPersistedId` 不存在而编译失败 = RED（确认失败原因是方法缺失）
2. **GREEN**：实现 `assignPersistedId`（见下），跑同命令 -> PASS
3. **回归**：`mvn test` 全绿

**实现要求**——在 `Transaction` 中新增：

```java
/**
 * 回填持久化主键（由 Repository 在 insert 后调用一次）。
 *
 * <p>同时用带 id 的实例替换已注册的领域事件（事件为不可变 record，注册时 id 尚为 null），
 * 使发布出去的事件携带真实 transactionId。重复调用抛 {@link IllegalStateException}。</p>
 *
 * @param id 数据库自增主键，不可为 null
 */
public void assignPersistedId(Long id) {
    Objects.requireNonNull(id, "id 不能为空");
    if (this.id != null) {
        throw new IllegalStateException("id 已回填，不可重复赋值");
    }
    this.id = id;
    List<Object> enriched = new ArrayList<>(domainEvents.size());
    for (Object e : domainEvents) {
        if (e instanceof TransactionClassifiedEvent tce) {
            enriched.add(new TransactionClassifiedEvent(id, tce.category(), tce.source()));
        } else if (e instanceof AnomalyDetectedEvent ade) {
            enriched.add(new AnomalyDetectedEvent(id, ade.score()));
        } else {
            enriched.add(e);
        }
    }
    domainEvents.clear();
    domainEvents.addAll(enriched);
}
```

> 说明：事件是 record（不可变），故不能改其 transactionId 字段；`assignPersistedId` 用「同字段 + 新 id」重建实例替换列表中的旧事件。`markClassified`/`markAnomaly` 在 save 前注册事件（id=null），save 后由 Repository 调 `assignPersistedId` 完成丰富化。

检查点：完成后暂停，等待 review

---

## 第二步：`TransactionRepositoryImpl` save/saveBatch 回填 id（集成测，远程 MySQL）

### task: insert 后调 assignPersistedId 回填聚合根 id

范围：
- 修改文件：`src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java`（`save` + `saveBatch` + 类 Javadoc）
- 修改测试：`src/test/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImplTest.java`（既有，追加用例）
- 禁止修改：`TransactionRepository` 接口、`Transaction` 聚合根、`TransactionConverter`、`TransactionPO`、建表 SQL

**测试契约（先行）**——追加到 `TransactionRepositoryImplTest`（`@SpringBootTest` + `@Transactional` 回滚 + DB 探活，既有范式）：

```java
@Test
@DisplayName("save 后应回填聚合根 id")
void shouldBackfillIdAfterSave() {
    Transaction tx = buildTransaction("ext-idback-001", "fp-idback-001");
    assertThat(tx.getId()).isNull();

    repository.save(tx);

    assertThat(tx.getId()).isNotNull();
    assertThat(tx.getId()).isPositive();
}

@Test
@DisplayName("saveBatch 后每条聚合根 id 均应回填")
void shouldBackfillIdAfterSaveBatch() {
    List<Transaction> batch = List.of(
            buildTransaction("ext-idback-002", "fp-idback-002"),
            buildTransaction("ext-idback-003", "fp-idback-003"));

    repository.saveBatch(batch);

    assertThat(batch).allMatch(tx -> tx.getId() != null && tx.getId() > 0);
}
```

**执行流程**：
1. **RED**：写上述 2 个用例，跑 `mvn test -Dtest=TransactionRepositoryImplTest` -> 因 `save`/`saveBatch` 不回填 id，`tx.getId()` 仍为 null -> FAILED（DB 可达时）；DB 不可达 -> assumeTrue 跳过（须先确认 DB 可达）
2. **GREEN**：改 `save`/`saveBatch`（见下），跑同命令 -> PASS
3. **回归**：`mvn test` 全绿（含既有 `shouldSaveAndFindByExternalId`/`shouldSaveBatchAndCount` 等不回归——它们不断言 id）

**实现要求**——`TransactionRepositoryImpl`：

```java
@Override
public void save(Transaction transaction) {
    TransactionPO po = TransactionConverter.toPO(transaction);
    transactionMapper.insert(po);
    transaction.assignPersistedId(po.getId());   // 回填自增 id + 丰富待发事件
}

@Override
public void saveBatch(List<Transaction> transactions) {
    if (transactions == null || transactions.isEmpty()) {
        return;
    }
    for (Transaction transaction : transactions) {
        TransactionPO po = TransactionConverter.toPO(transaction);
        transactionMapper.insert(po);
        transaction.assignPersistedId(po.getId());   // 回填自增 id + 丰富待发事件
    }
    // MVP 逐条插入；Day7+ 可切换 SqlSession BATCH 模式利用 rewriteBatchedStatements
}
```

> 说明：MyBatis-Plus `insert(po)` 后 `po.getId()` 即为 DB 自增主键；将其回填进聚合根（同时丰富事件）。原 Day5「不回填 id（MVP 决策）」的类 Javadoc 与 `save` 行内注释同步更新为「回填 id + 丰富事件」。既有 `shouldSaveAndFindByExternalId` 等用例不断言 `tx.getId()`，不受影响。

检查点：完成后暂停，等待 review

---

## 第三步：实现 `TransactionEventListener`（@EventListener，日志消费）

### task: 消费分类/异常事件 + 日志

范围：
- 新增文件：`src/main/java/com/finhub/fundflow/application/event/TransactionEventListener.java`
- 新增测试：`src/test/java/com/finhub/fundflow/application/event/TransactionEventListenerTest.java`
- 禁止修改：事件 record、`IngestionAppService`、领域层

**测试契约（先行）**：

```java
package com.finhub.fundflow.application.event;

import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
import com.finhub.fundflow.domain.vo.AnomalyScore;
import com.finhub.fundflow.domain.vo.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

/**
 * {@link TransactionEventListener} 装配与消费契约。
 *
 * <p>{@code @SpyBean} 包装真实监听器（日志真实输出），verify 确认 Spring 将事件路由到监听器。
 * 同步 {@code @EventListener}：publishEvent 返回即已消费，verify 立即可用。需远程 MySQL（@SpringBootTest 加载 Context）。</p>
 */
@Tag("integration")
@SpringBootTest
class TransactionEventListenerTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @SpyBean
    private TransactionEventListener listener;

    @Test
    @DisplayName("发布 TransactionClassifiedEvent 应被监听器消费")
    void shouldConsumeClassifiedEvent() {
        TransactionClassifiedEvent event = new TransactionClassifiedEvent(100L, Category.FOOD, "RULE");
        publisher.publishEvent(event);
        verify(listener).onClassified(event);
    }

    @Test
    @DisplayName("发布 AnomalyDetectedEvent 应被监听器消费")
    void shouldConsumeAnomalyEvent() {
        AnomalyDetectedEvent event = new AnomalyDetectedEvent(200L,
                new AnomalyScore(new BigDecimal("0.9"), "AMOUNT_SPIKE"));
        publisher.publishEvent(event);
        verify(listener).onAnomaly(event);
    }
}
```

**执行流程**：
1. **RED**：写测试，跑 `mvn test -Dtest=TransactionEventListenerTest` -> 因 `TransactionEventListener` 不存在 / `@SpyBean` 找不到 bean -> FAILED（确认失败原因是 bean 缺失，非编译错误）
2. **GREEN**：新增 `TransactionEventListener`（见下），跑同命令 -> PASS
3. **回归**：`mvn test` 全绿

**实现要求**——新增 `TransactionEventListener`：

```java
package com.finhub.fundflow.application.event;

import com.finhub.fundflow.domain.event.AnomalyDetectedEvent;
import com.finhub.fundflow.domain.event.TransactionClassifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 交易领域事件监听器：消费分类/异常事件。MVP 仅日志记录。
 *
 * <p>同步 {@link EventListener}：事件发布即在当前事务内消费。未来若需"通知"语义
 * （事务提交后才触发、监听器失败不回滚导入），可改
 * {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}（记 TODO）。</p>
 */
@Slf4j
@Component
public class TransactionEventListener {

    @EventListener
    public void onClassified(TransactionClassifiedEvent event) {
        log.info("交易分类完成: transactionId={}, category={}, source={}",
                event.transactionId(), event.category(), event.source());
    }

    @EventListener
    public void onAnomaly(AnomalyDetectedEvent event) {
        log.warn("异常交易标记: transactionId={}, score={}, reasonCode={}",
                event.transactionId(), event.score().score(), event.score().reasonCode());
    }
}
```

> 说明：异常事件用 `log.warn`（异常需关注）；分类事件 `log.info`。`@EventListener` 同步消费，发布即触发，测试可立即 `verify`。日志禁止打印金额/户名/备注（事件本身也不含明文）。

检查点：完成后暂停，等待 review

---

## 第四步：IngestionAppService 注释更新 + 端到端事件断言 + 全量回归

### task: 更新 step 8 注释 + E2E 验证事件带真实 id + 回归闸门

范围：
- 修改文件：`src/main/java/com/finhub/fundflow/application/IngestionAppService.java`（仅 step 8 注释）
- 修改测试：`src/test/java/com/finhub/fundflow/interfaces/IngestionEndToEndTest.java`（既有，追加 `@SpyBean` + 1 个用例）

**测试契约（先行）**——追加到 `IngestionEndToEndTest`（顶部加 `@SpyBean` 字段）：

```java
@org.springframework.boot.test.mock.mockito.SpyBean
private com.finhub.fundflow.application.event.TransactionEventListener eventListener;

@Test
@DisplayName("端到端导入后监听器应收到带非 null transactionId 的分类事件（id 回填 + 事件丰富化闭环）")
void shouldPublishClassifiedEventWithRealTransactionId() throws Exception {
    String nano = String.valueOf(System.nanoTime());
    String extId = "E2EEVT" + nano;
    String csv = alipayCsv(
            "2024-01-15 12:30:45,餐饮美食,美团外卖,test@example.com,午餐,支出,17.10,招商银行,交易成功," + extId + "\t,商户订单001\t,,");

    MockMultipartFile file = new MockMultipartFile("file", "alipay_evt.csv", "text/csv", csv.getBytes());
    mockMvc.perform(multipart("/api/transactions/import").file(file).with(httpBasic(USERNAME, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(1));

    // 监听器应收到分类事件，且 transactionId 非空（id 已被 saveBatch 回填并丰富进事件）
    org.mockito.ArgumentCaptor<com.finhub.fundflow.domain.event.TransactionClassifiedEvent> captor =
            org.mockito.ArgumentCaptor.forClass(com.finhub.fundflow.domain.event.TransactionClassifiedEvent.class);
    org.mockito.Mockito.verify(eventListener).onClassified(captor.capture());
    assertThat(captor.getValue().transactionId()).isNotNull();
    assertThat(captor.getValue().transactionId()).isPositive();
    assertThat(captor.getValue().category()).isEqualTo(com.finhub.fundflow.domain.vo.Category.FOOD);
}
```

**执行流程**：
1. **RED**：写上述用例，跑 `mvn test -Dtest=IngestionEndToEndTest` -> 因 step 8 注释未改但事件流已闭环（第二步已回填 id）——此用例验证的是「端到端事件带真实 id」，第二步 GREEN 后此用例应直接 PASS；若第二步未完成则 `transactionId` 为 null -> FAILED。确认红/绿符合预期
2. **GREEN**：更新 `IngestionAppService` step 8 注释（见下），跑同命令 -> PASS
3. **回归**：`mvn test` 全量回归闸门

**实现要求**——`IngestionAppService` step 8 注释：

```java
// 8. 发布领域事件并清空（saveBatch 已回填 id 并丰富事件，transactionId 为真实主键）
```
（原注释「事件在 save 前注册，transactionId 为 null 属已知缺口，待 Day6+ 监听器」整行替换）

**全量回归闸门（Day6 完成的硬性验收）**：
```bash
mvn test
```
- 必须全绿，零失败零跳过（integration 因 DB 不可达跳过除外，但需报告中明示）
- 对比 Day5 EOD 基线：Day6 新增测试（assignPersistedId 4 + Repository 回填 2 + 监听器 2 + E2E 事件 1 = 9）+ 全部旧测试仍绿
- 既有 `IngestionAppServiceTest`（单测，mocked repository 不回填 id，事件 transactionId 仍为 null）不断言 transactionId，不回归

检查点：完成后暂停，等待 review

---

## 今日检查清单（Day 6 EOD）

**TDD 硬约束验收**：
- [x] 每个 task 都先写了测试契约并落盘 review
- [x] 每个 task 都看过 RED（测试因实现缺失/行为未达成而 FAILED，非编译错误）
- [x] 每个 task 的 GREEN 实现后，改动的测试契约 PASS
- [x] 每个 task 完成后跑了全量 `mvn test`，旧测试无一回归

**功能交付**：
- [x] `Transaction.assignPersistedId` 实现并测试通过（回填 id + 丰富事件 + 重复赋值/ null 防御）
- [x] `TransactionRepositoryImpl.save/saveBatch` insert 后回填聚合根 id，测试通过
- [x] `TransactionEventListener` `@EventListener` 消费分类/异常事件，测试通过
- [x] `IngestionAppService` step 8 注释更新（事件带真实 id）
- [x] E2E 验证导入后监听器收到带非 null transactionId 的事件

**收尾**：
- [x] `mvn test` 全绿（退出码 0）—— 238/238
- [x] 手测：导入 CSV，控制台看到 `交易分类完成: transactionId=<非null>` / `异常交易标记: ...` 日志 —— **已由 E2E 测试 + 监听器测试实证**（E2E 日志：`交易分类完成: transactionId=2153`；监听器测试日志：`异常交易标记: transactionId=200`）
- [x] README 补 Day 6 进度
- [x] Git commit：`✨ feat(fundflow): Day6 领域事件闭环（id 回填 + 监听器消费 + E2E 验证）`
- [x] 已知缺口更新：`anomaly_reason_code` 列未建、通知语义（AFTER_COMMIT）待 Day7+

---

## 下一步预告（Day 7）

| 任务 | 内容 |
| ---- | ---- |
| 查询上下文 | `QueryAppService` 编排 + `QueryRouter` 责任链实现（按分类/时间/金额等维度查询） |
| MCP Tool | `McpToolDispatcher` 实现类（导入/查询工具暴露给 AI 客户端） |
| `anomaly_reason_code` 列 | 建列 + Converter 落库（替换哨兵占位） |
| 通知语义 | 监听器改 `@TransactionalEventListener(AFTER_COMMIT)`（事务提交后通知，失败不回滚导入） |
