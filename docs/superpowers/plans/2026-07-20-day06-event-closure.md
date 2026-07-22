# Day 6 领域事件闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Day5 遗留的领域事件缺口闭环——事件在 `save` 后携带真实自增主键 `transactionId`，并被一个同步日志监听器消费。

**Architecture:** 在 `Transaction` 聚合根新增 `assignPersistedId(Long)`：insert 后由 Repository 回填 id，并因事件为不可变 record，用「同字段 + 新 id」重建实例替换已注册事件。新增 `TransactionEventListener`（`@EventListener`，MVP 仅日志）消费分类/异常事件。`IngestionAppService` step 8 注释同步更新。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, JUnit 5, AssertJ, Mockito（`@SpyBean`），远程 MySQL。

## Global Constraints

- **TDD 铁律**：任何新类创建或旧类修改，必须先写测试契约并看到 RED（因实现缺失/行为未达成 FAILED，非编译错误），再 GREEN，再全量回归。完整版见 `day05.md`。
- 测试命令统一为 `mvn test`；单类回归用 `mvn test -Dtest=<TestClass>`。
- 禁止修改既有公开签名：`Transaction` 字段/构造器/工厂方法/`markClassified`/`markAnomaly`、事件 record 签名、`TransactionRepository` 接口、`TransactionConverter`、`TransactionPO`、建表 SQL。
- 日志禁止打印金额/户名/备注等明文（事件本身也不含明文）。
- 远程 MySQL 必须可达：`@SpringBootTest`（Repository 集成测、监听器测、E2E）加载 Context 即连库，不可达会硬失败。
- 决策已定：① id 回填用 `assignPersistedId`；② 监听器落 `com.finhub.fundflow.application.event`；③ 同步 `@EventListener`，`@TransactionalEventListener(AFTER_COMMIT)` 仅留 TODO。

---

## Day 0：开工前自检

- [ ] **Step 1: 确认 baseline 绿灯**

Run: `mvn test`
Expected: 退出码 0，全部测试 PASS。记录实际测试总数作为基线（当前 branches 含 Day5 + knife4j + dev 免密 + actuator 等近 10 次提交，测试数已从 spec 写明的 229 变化——以实际控制台输出为准）。若有红，必须定位原因修复后再开工。

- [ ] **Step 2: 确认远程 MySQL 可达**

`TransactionRepositoryImplTest`、`TransactionEventListenerTest`、`IngestionEndToEndTest` 均使用 `@SpringBootTest` 且加载 Context 即连库，**不可达会直接启动失败**。在 Step 1 `mvn test` 中若 `@SpringBootTest` 类因数据库连接超时而报错，需先解决网络/认证问题再继续。

> ⚠️ 上述 3 个 `@SpringBootTest` 集成测试均有 DB 探活 `assumeTrue` 兜底，但 `FinHubApplicationTest` 与 `TransactionEventListenerTest` 无探活——`@SpringBootTest` 加载自动配置时即需连接数据源，**不可达时直接启动失败**。

- [ ] **Step 3: 确认工作树干净**

Run: `git status --short`
Expected: 仅 `.claude/`、`docs/superpowers/` 等非代码文件为 untracked。src/main 与 src/test 均无修改——确保 TDD 从干净基线出发。

### Task 1: `Transaction.assignPersistedId`（聚合内回填 id + 丰富待发事件）

**Files:**
- Modify: `src/main/java/com/finhub/fundflow/domain/aggregate/Transaction.java`
- Test: `src/test/java/com/finhub/fundflow/domain/vo/TransactionTest.java`（追加用例）

**Interfaces:**
- Consumes: 既有 `Transaction.createFrom(...)`、`markClassified(Category, String)`、`markAnomaly(AnomalyScore)`、`getDomainEvents()`、事件 record `TransactionClassifiedEvent(Long transactionId, Category category, String source)` 与 `AnomalyDetectedEvent(Long transactionId, AnomalyScore score)`。
- Produces: `public void assignPersistedId(Long id)`——Task 2 的 Repository 在 insert 后调用；Task 4 的 E2E 依赖其丰富化后的事件。

**测试契约追加位置说明：** `TransactionTest` 已有私有辅助 `money(String)`、`encrypted(String)`、`fingerprint(String)` 与 `private static final String VALID_KEY`。文件顶部需新增两个 import：`com.finhub.fundflow.domain.event.TransactionClassifiedEvent` 与 `com.finhub.fundflow.domain.event.AnomalyDetectedEvent`（`AnomalyScore`、`BigDecimal`、`Category`、`Direction`、`LocalDateTime` 已导入或在同包 `domain.vo`）。

- [ ] **Step 1: 写失败的测试契约（追加到 `TransactionTest`）**

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

- [ ] **Step 2: 跑测试确认 RED**

Run: `mvn test -Dtest=TransactionTest`
Expected: 编译失败（`assignPersistedId` 方法缺失）。确认失败原因是「找不到符号 assignPersistedId」，而非契约本身的语法/类型错误——若是后者，修契约后重新看红。

- [ ] **Step 3: 最小实现（追加到 `Transaction.java`，置于 `markAnomaly` 之后）**

`Transaction.java` 顶部已有 `import com.finhub.fundflow.domain.event.*`、`java.util.ArrayList`、`java.util.List`、`java.util.Objects`，无需新增 import。

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

- [ ] **Step 4: 跑测试确认 GREEN**

Run: `mvn test -Dtest=TransactionTest`
Expected: 4 个新用例 + 全部旧用例 PASS。

- [ ] **Step 5: 全量回归 + Commit**

Run: `mvn test`（期望全绿，无回归）

```bash
git add src/main/java/com/finhub/fundflow/domain/aggregate/Transaction.java src/test/java/com/finhub/fundflow/domain/vo/TransactionTest.java
git commit -m "✨ feat(fundflow): Transaction.assignPersistedId 回填 id 并丰富待发领域事件"
```

**检查点：完成后暂停，等待 review**

---

### Task 2: `TransactionRepositoryImpl` save/saveBatch 回填 id（集成测，远程 MySQL）

**Files:**
- Modify: `src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java`（`save` + `saveBatch` + 类 Javadoc）
- Test: `src/test/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImplTest.java`（追加用例）

**Interfaces:**
- Consumes: Task 1 的 `Transaction.assignPersistedId(Long)`；既有 `TransactionConverter.toPO(Transaction)`、`transactionMapper.insert(TransactionPO)`（MyBatis-Plus，insert 后 `po.getId()` 即为自增主键）。
- Produces: `save(Transaction)` 与 `saveBatch(List<Transaction>)` 现在会回填聚合根 id——Task 4 的 E2E 事件断言依赖 saveBatch 的回填。

**前置：** 确认远程 MySQL 可达（`TransactionRepositoryImplTest` 的 `@BeforeAll probeDatabase` 用 `SELECT 1` 探活，不可达则整类 `assumeTrue` 跳过）。本 task 的 RED 必须建立在 DB 可达之上。

- [ ] **Step 1: 写失败的测试契约（追加到 `TransactionRepositoryImplTest`）**

`TransactionRepositoryImplTest` 已有私有辅助 `buildTransaction(String externalId, String fpHash)` 与 `import static org.assertj.core.api.Assertions.assertThat;`，无需新增 import。

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

- [ ] **Step 2: 跑测试确认 RED**

Run: `mvn test -Dtest=TransactionRepositoryImplTest`
Expected: DB 可达时，两个新用例因 `save`/`saveBatch` 不回填 id（`tx.getId()` 仍为 null）而 FAILED。若整类被 `assumeTrue` 跳过，说明 DB 不可达，先解决 DB 可达性再继续。

- [ ] **Step 3: 最小实现（改 `TransactionRepositoryImpl`）**

替换 `save` 方法体（原行 `// MVP 决策：不回填 PO 自增 id 到聚合根（Transaction 无 setId）` 一并替换）：

```java
@Override
public void save(Transaction transaction) {
    TransactionPO po = TransactionConverter.toPO(transaction);
    transactionMapper.insert(po);
    transaction.assignPersistedId(po.getId());   // 回填自增 id + 丰富待发事件
}
```

替换 `saveBatch` 方法体（原行尾注释 `// MVP 逐条插入；Day6+ 可切换 ...` 更新为 Day7+）：

```java
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

同时更新类 Javadoc：将 `TransactionRepositoryImpl.java:20` 的
`不回填聚合根 id（MVP 决策，详见 {@link TransactionConverter} 已知缺口）。`
改为
`insert 后回填聚合根自增 id（{@link Transaction#assignPersistedId(Long)}），同时丰富待发领域事件。`

- [ ] **Step 4: 跑测试确认 GREEN**

Run: `mvn test -Dtest=TransactionRepositoryImplTest`
Expected: 2 个新用例 PASS；既有 `shouldSaveAndFindByExternalId`/`shouldSaveBatchAndCount` 等不断言 id，不回归。

- [ ] **Step 5: 全量回归 + Commit**

Run: `mvn test`（期望全绿）

```bash
git add src/main/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImpl.java src/test/java/com/finhub/fundflow/infrastructure/repository/TransactionRepositoryImplTest.java
git commit -m "✨ feat(fundflow): Repository save/saveBatch insert 后回填聚合根 id"
```

**检查点：完成后暂停，等待 review**

---

### Task 3: `TransactionEventListener`（@EventListener 日志消费）

**Files:**
- Create: `src/main/java/com/finhub/fundflow/application/event/TransactionEventListener.java`
- Test: `src/test/java/com/finhub/fundflow/application/event/TransactionEventListenerTest.java`

**Interfaces:**
- Consumes: 事件 record `TransactionClassifiedEvent(Long, Category, String)`、`AnomalyDetectedEvent(Long, AnomalyScore)`；`AnomalyScore.score()` / `reasonCode()` 访问器。
- Produces: `public void onClassified(TransactionClassifiedEvent)` 与 `public void onAnomaly(AnomalyDetectedEvent)`——Task 4 的 E2E 通过 `@SpyBean` verify 这两个方法。

**前置：** 确认远程 MySQL 可达（`@SpringBootTest` 加载 Context 即连库）。

- [ ] **Step 1: 写失败的测试契约（新建 `TransactionEventListenerTest.java`）**

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

- [ ] **Step 2: 跑测试确认 RED**

Run: `mvn test -Dtest=TransactionEventListenerTest`
Expected: 因 `TransactionEventListener` 类不存在而编译失败 / `@SpyBean` 找不到 bean。确认失败原因是 bean/类缺失，而非测试本身写错。

- [ ] **Step 3: 最小实现（新建 `TransactionEventListener.java`）**

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

- [ ] **Step 4: 跑测试确认 GREEN**

Run: `mvn test -Dtest=TransactionEventListenerTest`
Expected: 2 个用例 PASS。

- [ ] **Step 5: 全量回归 + Commit**

Run: `mvn test`（期望全绿）

```bash
git add src/main/java/com/finhub/fundflow/application/event/TransactionEventListener.java src/test/java/com/finhub/fundflow/application/event/TransactionEventListenerTest.java
git commit -m "✨ feat(fundflow): 新增 TransactionEventListener 同步日志消费分类/异常事件"
```

**检查点：完成后暂停，等待 review**

---

### Task 4: IngestionAppService 注释更新 + E2E 事件断言 + 全量回归

**Files:**
- Modify: `src/main/java/com/finhub/fundflow/application/IngestionAppService.java`（仅 step 8 注释，`IngestionAppService.java:126`）
- Test: `src/test/java/com/finhub/fundflow/interfaces/IngestionEndToEndTest.java`（追加 `@SpyBean` 字段 + 1 个用例）

**Interfaces:**
- Consumes: Task 2 的 `saveBatch` 回填、Task 3 的 `TransactionEventListener.onClassified(...)`；既有 `alipayCsv(...)` 辅助、`USERNAME`/`PASSWORD` 常量、`mockMvc`、`httpBasic`、`multipart`、`jsonPath`、`status`。
- Produces: E2E 证明「导入后监听器收到带非 null transactionId 的分类事件」——Day6 的最终验收。

- [ ] **Step 1: 写失败的测试契约（追加到 `IngestionEndToEndTest`）**

在类字段区（`dataSource` 字段之后）追加 `@SpyBean` 字段：

```java
@org.springframework.boot.test.mock.mockito.SpyBean
private com.finhub.fundflow.application.event.TransactionEventListener eventListener;
```

追加测试方法（`assertThat` 已在类内静态导入；为与既有风格一致，event/captor/vo 类型用全限定名）：

```java
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

- [ ] **Step 2: 跑测试确认预期行为**

Run: `mvn test -Dtest=IngestionEndToEndTest`
Expected: 本用例验证的是「端到端事件带真实 id」，它依赖 Task 2 的 saveBatch 回填。Task 2 已 GREEN，故此用例应直接 PASS。若 Task 2 未完成则 `transactionId` 为 null → FAILED。确认红/绿符合预期（本 task 的「RED」体现在 Task 2 未完成时；Task 2 完成后此用例即绿）。

- [ ] **Step 3: 更新 step 8 注释（`IngestionAppService.java:126`）**

将原行
`// 8. 发布领域事件并清空（事件在 save 前注册，transactionId 为 null 属已知缺口，待 Day6+ 监听器）`
整行替换为：

```java
// 8. 发布领域事件并清空（saveBatch 已回填 id 并丰富事件，transactionId 为真实主键）
```

- [ ] **Step 4: 跑测试确认 GREEN**

Run: `mvn test -Dtest=IngestionEndToEndTest`
Expected: 全部用例 PASS（含新增的 `shouldPublishClassifiedEventWithRealTransactionId`）。

- [ ] **Step 5: 全量回归闸门 + Commit**

Run: `mvn test`
Expected: 全绿（退出码 0）。对比 Day5 基线：Day6 新增 9 个测试（assignPersistedId 4 + Repository 回填 2 + 监听器 2 + E2E 事件 1），全部旧测试仍绿。integration 因 DB 不可达跳过需在报告中明示。

```bash
git add src/main/java/com/finhub/fundflow/application/IngestionAppService.java src/test/java/com/finhub/fundflow/interfaces/IngestionEndToEndTest.java
git commit -m "✨ feat(fundflow): Day6 领域事件闭环（id 回填 + 监听器消费）"
```

**检查点：完成后暂停，等待 review**

---

## Day 6 EOD 收尾（Task 4 全绿后执行）

- [ ] 手测：启动应用导入 CSV，控制台看到 `交易分类完成: transactionId=<非null>` / `异常交易标记: ...` 日志
- [ ] README 补 Day 6 进度
- [ ] 已知缺口更新：`anomaly_reason_code` 列未建、通知语义（AFTER_COMMIT）待 Day7+

## Self-Review 记录

- **Spec coverage：** spec 表格 5 行产出 → Task1(assignPersistedId) / Task2(Repository) / Task3(Listener) / Task4(注释+E2E) / 测试分布于各 Task。spec 第 50-58 行"Day 0 开工前自检"已补 Task 0。✅
- **Placeholder scan：** 每个 code step 均含完整代码，无 TBD/TODO/「类似 TaskN」。✅
- **Type consistency：** `assignPersistedId(Long)`、`onClassified(TransactionClassifiedEvent)`、`onAnomaly(AnomalyDetectedEvent)`、事件 record 访问器 `transactionId()/category()/source()/score()` 与 `AnomalyScore.score()/reasonCode()` 全部与源码核对一致；测试辅助 `money/encrypted/fingerprint`(Task1)、`buildTransaction`(Task2)、`alipayCsv`/`USERNAME`/`PASSWORD`(Task4) 均存在于目标测试类。✅
