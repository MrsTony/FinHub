以下是 **Day 3 执行计划**。基于 Day 2 完成 Transaction 聚合根，今天核心目标是**领域服务接口实现 + 防腐层（ACL）接口定义 + 应用层编排骨架**。

---

## 📋 Day 3 目标

| 模块                                     | 产出                           | 负责               |
| ---------------------------------------- | ------------------------------ | ------------------ |
| 领域服务接口（4个）                      | 定义完成，Javadoc 描述业务规则 | 你                 |
| 领域服务实现（DeduplicationServiceImpl） | 三重防重逻辑实现               | Claude（模板 B）   |
| 防腐层接口（DataSourceAdapter）          | 定义完成                       | 你                 |
| 应用层编排（IngestionAppService）        | 导入流程骨架（空壳）           | 你                 |
| 领域事件发布机制                         | Spring ApplicationEvent 配置   | Claude（基础设施） |

---

## 第一步：确认 Day 2 基线（5 分钟）

```bash
cd finhub

# 确认 Git 状态
git status

# 确认 Transaction 测试通过
mvn test -Dtest=TransactionTest

# 确认 Money/Category/Fingerprint 测试仍通过
mvn test -Dtest=MoneyTest,CategoryTest,FingerprintTest

# 提交 Day 2 成果（如果还没提交）
git add -p
git commit -m "feat(fundflow/domain): implement Transaction aggregate root with invariant validation

- Factory method createFrom with 6 validation rules
- markClassified / markAnomaly state mutation methods
- Domain event registration (TransactionClassifiedEvent, AnomalyDetectedEvent)
- All value object integration tests pass

Claude-Task: A-002
Reviewed-By: [你]"
```

---

## 第二步：领域服务接口定义（上午，你亲自做）

### 2.1 DeduplicationService（已定义，确认 Javadoc 完整）

```java
// src/main/java/.../fundflow/domain/service/DeduplicationService.java
package com.example.finhub.fundflow.domain.service;

import com.example.finhub.fundflow.domain.aggregate.Transaction;
import java.util.List;

/**
 * 排重领域服务：跨交易记录排重判断
 * 
 * 业务规则（三重防重，优先级递减）：
 * 1. external_id 唯一性：外部系统提供的业务标识（如支付宝 trade_no）
 * 2. fingerprint 唯一性：结构化哈希（金额+时间+对方+备注+盐值），external_id 缺失时的兜底
 * 3. 缓存预检：Caffeine 本地缓存，降低数据库查询压力（非权威，仅加速）
 * 
 * 注意：此服务不修改输入对象，返回去重后的新列表
 */
public interface DeduplicationService {
    
    /**
     * 对候选交易列表进行排重
     * 
     * @param candidates 待排重的交易列表（已构造但未持久化）
     * @return 去重后的交易列表（新列表，不修改输入）
     * @throws IllegalArgumentException 若 candidates 为 null 或包含 null 元素
     */
    List<Transaction> deduplicate(List<Transaction> candidates);
}
```

### 2.2 TransactionClassifier（已定义，确认 Javadoc 完整）

```java
// src/main/java/.../fundflow/domain/service/TransactionClassifier.java
package com.example.finhub.fundflow.domain.service;

import com.example.finhub.fundflow.domain.aggregate.Transaction;
import com.example.finhub.fundflow.domain.vo.CategorySuggestion;

/**
 * 交易分类领域服务：规则引擎 + AI 建议，核心域保留最终决策权
 * 
 * 分类优先级：
 * 1. 规则匹配：商户关键词映射（如"美团"→ FOOD，"滴滴"→ TRANSPORT）
 * 2. 用户历史偏好：该用户通常将某商户标为什么类别
 * 3. AI 兜底：调用 CategorySuggestionEngine（防腐层），返回建议 + 置信度
 * 
 * 注意：返回的是 CategorySuggestion（建议），不是最终决策。
 * 应用层根据 confidence > 0.8 决定是否调用 tx.markClassified()
 */
public interface TransactionClassifier {
    
    /**
     * 为交易生成分类建议
     * 
     * @param transaction 待分类的交易（已构造，可能未持久化）
     * @return 分类建议（含类别、置信度、来源）
     */
    CategorySuggestion classify(Transaction transaction);
}
```

### 2.3 AnomalyDetector（已定义，确认 Javadoc 完整）

```java
// src/main/java/.../fundflow/domain/service/AnomalyDetector.java
package com.example.finhub.fundflow.domain.service;

import com.example.finhub.fundflow.domain.aggregate.Transaction;
import com.example.finhub.fundflow.domain.vo.AnomalyScore;
import java.util.List;
import java.util.Map;

/**
 * 异常侦探领域服务：基于统计规则检测异常消费模式
 * 
 * 检测规则：
 * 1. 金额异常：单笔 > 月均同类消费 3 倍 → HIGH，> 1.5 倍 → MEDIUM
 * 2. 重复扣款：7 天内相同金额 + 相同对方户名 > 2 次 → 疑似重复
 * 3. 订阅陷阱：同一商户小额（<30 元）按月规律扣款 → SUBSCRIPTION 提醒
 * 
 * 注意：此服务读取历史交易数据（跨聚合根查询），不修改输入对象
 */
public interface AnomalyDetector {
    
    /**
     * 检测异常交易
     * 
     * @param transactions 待检测的交易列表（通常是本次导入的批次）
     * @return 异常标识 → 异常评分的映射（key 为临时标识或 external_id）
     */
    Map<String, AnomalyScore> detect(List<Transaction> transactions);
}
```

### 2.4 FingerprintGenerator（已定义，确认 Javadoc 完整）

```java
// src/main/java/.../fundflow/domain/service/FingerprintGenerator.java
package com.example.finhub.fundflow.domain.service;

import com.example.finhub.fundflow.domain.vo.Fingerprint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指纹生成领域服务：复杂算法委托，避免聚合根臃肿
 * 
 * 生成算法（结构化哈希）：
 * 1. 金额：截断精度（BigDecimal setScale(2)）
 * 2. 时间：截断到分钟（LocalDateTime truncatedTo(MINUTES)）
 * 3. 对方户名：标准化（去除空格、特殊字符、转小写）
 * 4. 备注：空值占位（null → "__EMPTY__"）
 * 5. 盐值：每个用户/实例独立（环境变量注入）
 * 
 * 注意：盐值是生成参数，不参与匹配比较（matches() 只比较 hashValue）
 */
public interface FingerprintGenerator {
    
    /**
     * 生成排重指纹
     * 
     * @param counterparty 对方户名（明文，标准化后哈希）
     * @param amount 金额（精度已强制）
     * @param transTime 交易时间（截断到分钟）
     * @param remark 备注（可能为 null）
     * @param salt 盐值（32 字节以上，环境变量注入）
     * @return 指纹值对象（含 hashValue 和 salt）
     * @throws IllegalArgumentException 若参数非法
     */
    Fingerprint generate(String counterparty, BigDecimal amount,
                         LocalDateTime transTime, String remark, String salt);
}
```

---

## 第三步：防腐层接口定义（上午，你亲自做）

### 3.1 DataSourceAdapter（CSV 适配器接口）

```java
// src/main/java/.../fundflow/acl/DataSourceAdapter.java
package com.example.finhub.fundflow.acl;

import java.io.InputStream;
import java.util.List;

/**
 * 数据源适配器：防腐层接口
 * 
 * 将外部格式（CSV/Excel/JSON）转换为领域概念 RawRecord
 * 核心域不认识 CSV，只认识 RawRecord
 * 
 * 实现类：AlipayCSVAdapter（2024 新版）、WechatCSVAdapter（预留）、BankCSVAdapter（预留）
 */
public interface DataSourceAdapter {
    
    /**
     * 解析输入流为原始记录列表
     * 
     * @param inputStream 文件输入流（由应用层打开，适配器不管理资源）
     * @param filename 原始文件名（用于识别格式和编码，如"alipay_20240101.csv"）
     * @return 原始记录列表（可能为空，不会为 null）
     * @throws IllegalArgumentException 格式不支持、编码识别失败、严重格式错误
     */
    List<RawRecord> adapt(InputStream inputStream, String filename);
}
```

### 3.2 RawRecord（防腐层概念，非领域实体）

```java
// src/main/java/.../fundflow/acl/RawRecord.java
package com.example.finhub.fundflow.acl;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原始记录：CSV 解析后的中间态数据
 * 
 * 注意：这是防腐层概念，不是领域实体。字段可能为 null 或格式错误，
 * 需要应用层校验后才能构造 Transaction 聚合根。
 */
public record RawRecord(
    String externalId,      // 可能为 null（支付宝 CSV 某些版本无此字段）
    BigDecimal amount,      // 可能为 null（解析失败）
    String currency,        // 可能为 null，默认 CNY
    String direction,       // "收入"/"支出" 或 "IN"/"OUT"，需转换
    String counterparty,    // 对方户名，可能为 null
    String remark,          // 备注，可能为 null
    LocalDateTime transTime, // 可能为 null（解析失败）
    String sourceSystem     // 来源标识，如 "ALIPAY"
) {
}
```

---

## 第四步：应用层编排骨架（上午，你亲自做）

```java
// src/main/java/.../fundflow/application/IngestionAppService.java
package com.example.finhub.fundflow.application;

import com.example.finhub.fundflow.acl.DataSourceAdapter;
import com.example.finhub.fundflow.acl.RawRecord;
import com.example.finhub.fundflow.domain.aggregate.Transaction;
import com.example.finhub.fundflow.domain.service.*;
import com.example.finhub.fundflow.domain.repository.TransactionRepository;
import com.example.finhub.fundflow.domain.vo.CategorySuggestion;
import com.example.finhub.fundflow.domain.vo.Fingerprint;
import com.example.finhub.fundflow.domain.vo.Money;
import com.example.finhub.fundflow.domain.vo.Direction;
import com.example.finhub.fundflow.domain.vo.Category;
import com.example.finhub.fundflow.domain.vo.EncryptedString;
import com.example.finhub.fundflow.domain.event.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 导入编排服务：应用层用例编排
 * 
 * 职责：
 * 1. 调用防腐层解析 CSV → RawRecord
 * 2. 调用 FingerprintGenerator 生成指纹
 * 3. 构造 Transaction 聚合根（工厂方法校验不变量）
 * 4. 调用 DeduplicationService 排重
 * 5. 调用 TransactionClassifier 分类建议
 * 6. 聚合根决策是否采纳分类（markClassified）
 * 7. 调用 TransactionRepository 持久化
 * 8. 发布领域事件
 * 
 * 注意：此层只编排，不决策。所有业务规则在领域层。
 */
@Service
public class IngestionAppService {
    
    private final DataSourceAdapter dataSourceAdapter;
    private final FingerprintGenerator fingerprintGenerator;
    private final DeduplicationService deduplicationService;
    private final TransactionClassifier transactionClassifier;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // 盐值从环境变量注入（基础设施配置）
    private final String fingerprintSalt;
    
    // TODO: 构造器注入，Spring 自动装配
    
    /**
     * 导入文件主流程
     * 
     * @param inputStream 文件流（由 Controller 提供，应用层不管理资源生命周期）
     * @param filename 原始文件名
     * @return 导入结果（成功数、跳过数、失败数）
     */
    @Transactional
    public ImportResult importFile(InputStream inputStream, String filename) {
        // TODO: 实现编排逻辑
        throw new UnsupportedOperationException("TODO");
    }
    
    /**
     * 导入结果 DTO（应用层技术对象，非领域对象）
     */
    public record ImportResult(int imported, int skipped, int failed) {
    }
}
```

---

## 第五步：给 Claude 的下午任务（DeduplicationServiceImpl）

```markdown
/task: 实现 DeduplicationServiceImpl 的 deduplicate 方法

范围：
- 实现文件：src/main/java/com/example/finhub/fundflow/infrastructure/service/DeduplicationServiceImpl.java
- 接口定义：com.example.finhub.fundflow.domain.service.DeduplicationService（禁止修改）
- 禁止修改：接口文件、测试文件、Transaction 聚合根

要求：
1. 严格按接口 Javadoc 的三重防重顺序实现：
   - 第一重：external_id 查库（调用 TransactionRepository.findByExternalId）
   - 第二重：fingerprint 查库（调用 TransactionRepository.findByFingerprint）
   - 第三重：Caffeine 缓存预检（注入 Cache<String, Boolean>）

2. 依赖注入：
   - TransactionRepository（构造器注入）
   - Cache<String, Boolean>（构造器注入，Caffeine 实现）
   - 禁止 @Autowired 字段注入

3. 业务规则：
   - 输入列表中 external_id 或 fingerprint 重复的，保留第一个，后续跳过
   - 数据库中已存在的，直接跳过
   - 缓存中存在的（本次批次已处理），直接跳过
   - 返回新列表，不修改输入对象

4. 日志：
   - 使用 SLF4J
   - 可打印：external_id、fingerprint.hashValue（前 8 位）、sourceSystem
   - 禁止打印：金额、对方户名、备注

5. 异常：
   - 输入 null 或包含 null 元素 → IllegalArgumentException
   - 数据库查询异常 → 抛 RuntimeException，不吞掉

6. 完成后执行：mvn test -Dtest=DeduplicationServiceTest

检查点：完成后暂停，等待我 review
```

---

## 第六步：你需要准备的 DeduplicationServiceTest（测试契约）

```java
package com.example.finhub.fundflow.domain.service;

import com.example.finhub.fundflow.domain.aggregate.Transaction;
import com.example.finhub.fundflow.domain.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * DeduplicationService 测试契约
 * 
 * 注意：这是接口契约测试，使用 Mock 实现验证行为。
 * 真实实现测试在 DeduplicationServiceImplTest 中。
 */
abstract class DeduplicationServiceTest {

    protected abstract DeduplicationService createService();

    // 辅助方法：创建 Money
    private Money money(String amount) {
        return new Money(new BigDecimal(amount), "CNY");
    }

    // 辅助方法：创建加密字符串
    private EncryptedString encrypted(String plain) {
        return EncryptedString.fromPlain(plain, "12345678901234567890123456789012");
    }

    // 辅助方法：创建指纹
    private Fingerprint fingerprint(String hash) {
        return new Fingerprint(hash, "salt");
    }

    // 辅助方法：创建交易
    private Transaction createTx(String extId, String fpHash) {
        return Transaction.createFrom(
            extId,
            money("100.00"),
            Direction.OUT,
            Category.UNCLASSIFIED,
            LocalDateTime.now(),
            encrypted("测试商户"),
            encrypted("测试备注"),
            fingerprint(fpHash),
            "ALIPAY"
        );
    }

    @Test
    @DisplayName("应去除 external_id 重复的交易")
    void shouldRemoveDuplicatesByExternalId() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-001", "fp-002"); // 相同 externalId

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalId()).isEqualTo("ext-001");
    }

    @Test
    @DisplayName("应去除 fingerprint 重复的交易")
    void shouldRemoveDuplicatesByFingerprint() {
        DeduplicationService service = createService();
        Transaction t1 = createTx(null, "fp-same");
        Transaction t2 = createTx(null, "fp-same"); // 相同 fingerprint

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("应保留 external_id 和 fingerprint 均唯一的交易")
    void shouldPreserveUniqueRecords() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-002", "fp-002");

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("输入 null 应抛异常")
    void shouldRejectNullInput() {
        DeduplicationService service = createService();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.deduplicate(null));
    }

    @Test
    @DisplayName("输入包含 null 元素应抛异常")
    void shouldRejectNullElements() {
        DeduplicationService service = createService();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.deduplicate(List.of(createTx("ext-001", "fp-001"), null)));
    }

    @Test
    @DisplayName("不应修改输入列表")
    void shouldNotModifyInputList() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-001", "fp-002");

        List<Transaction> input = List.of(t1, t2);
        List<Transaction> result = service.deduplicate(input);

        assertThat(input).hasSize(2); // 输入列表未被修改
        assertThat(result).hasSize(1); // 返回新列表
    }
}
```

---

## 今日检查清单（Day 3 EOD）

- [ ] 4 个领域服务接口 Javadoc 完整，已提交
- [ ] DataSourceAdapter + RawRecord 防腐层接口定义完成
- [ ] IngestionAppService 应用层骨架（空壳）完成
- [ ] DeduplicationServiceImpl 实现完成，测试通过
- [ ] DeduplicationServiceTest 契约测试通过（Mock 实现）
- [ ] Git commit：`feat(fundflow/service): implement DeduplicationService with triple-check logic`
- [ ] 无架构边界修改（包/类/接口签名未变）

---

## 下一步预告（Day 4）

| 任务                      | 内容                              |
| ------------------------- | --------------------------------- |
| TransactionClassifierImpl | 规则引擎实现（商户关键词映射）    |
| FingerprintGeneratorImpl  | SHA256 结构化哈希实现             |
| AnomalyDetectorImpl       | 统计规则异常检测（MVP 简化版）    |
| AlipayCSVAdapter          | 防腐层实现（2024 新版支付宝 CSV） |

---

需要我先输出 **DeduplicationServiceImpl 的完整空壳代码**（构造器注入 + 方法签名），方便 Claude 直接填充？