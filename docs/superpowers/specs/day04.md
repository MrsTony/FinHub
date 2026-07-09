#### task: 实现 FingerprintGeneratorImpl 的 generate 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/service/FingerprintGeneratorImpl.java
- 接口定义：com.finhub.fundflow.domain.service.FingerprintGenerator（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 算法：SHA-256(金额截断精度 + "|" + 时间截断到分钟 + "|" + 对方户名标准化 + "|" + 备注空值占位 + "|" + 盐值)
2. 金额截断：使用 Money.amount()（已强制精度 2 位小数），toPlainString()
3. 时间截断：transTime.truncatedTo(ChronoUnit.MINUTES)
4. 对方户名标准化：去除前后空格、转小写、去除特殊字符（只保留字母数字汉字）
5. 备注空值占位：null 或 blank → "__EMPTY__"
6. 盐值：直接拼接，不处理
7. 返回：new Fingerprint(hashHexString, salt)
8. 异常：任何步骤 null 输入 → NullPointerException，标准化失败 → IllegalArgumentException
9. 使用 java.security.MessageDigest，不引入外部库
10. 完成后执行：mvn test -Dtest=FingerprintGeneratorImplTest

检查点：完成后暂停，等待我 review

##### task: 实现 TransactionClassifierImpl 的 classify 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/service/TransactionClassifierImpl.java
- 接口定义：com.finhub.fundflow.domain.service.TransactionClassifier（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 规则引擎优先（MVP 阶段，AI 建议预留接口但不实现）：
    - 商户关键词映射：HashMap<String, Category> 硬编码（MVP 简化）
    - "美团" "饿了么" "大众点评" → FOOD
    - "滴滴" "高德" "曹操" → TRANSPORT
    - "淘宝" "京东" "拼多多" "天猫" → SHOPPING
    - "支付宝" "工资" "奖金" → INCOME（但需校验 Direction 为 IN）
    - 其他 → UNCLASSIFIED

2. 方向兼容性校验：
    - INCOME 类别必须 Direction 为 IN，否则返回 UNCLASSIFIED + confidence 0.0
    - 支出类别必须 Direction 为 OUT，否则返回 UNCLASSIFIED + confidence 0.0

3. 返回 CategorySuggestion：
    - 规则命中：confidence 1.0, source "RULE"
    - 未命中：confidence 0.0, source "RULE", category UNCLASSIFIED

4. 预留 AI 接口：
    - 构造函数注入 CategorySuggestionEngine（接口，当前传 null 或 Noop 实现）
    - 规则未命中时，若 engine 非 null，可调用 engine.suggest()
    - MVP 阶段不实现 AI 调用，但代码结构预留

5. 日志：SLF4J，打印商户名、匹配结果、confidence（不打印金额）
6. 完成后执行：mvn test -Dtest=TransactionClassifierImplTest

检查点：完成后暂停，等待我 review


#### task: 实现 AnomalyDetectorImpl 的 detect 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/service/AnomalyDetectorImpl.java
- 接口定义：com.finhub.fundflow.domain.service.AnomalyDetector（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. MVP 简化版，仅实现金额异常检测：
   - 输入列表中，计算每个 Category 的 average amount（绝对值）
   - 单笔 > 3 倍平均 → AnomalyScore("AMOUNT_SPIKE", 0.9)
   - 单笔 > 1.5 倍平均 → AnomalyScore("AMOUNT_HIGH", 0.6)
   - 其他 → 不返回（无异常）

2. 重复扣款检测（预留，MVP 可空实现）：
   - 7 天内相同金额 + 相同对方户名 > 2 次 → 预留注释，不实现
3. 后期可扩展规则，在原有规则不变的前提下。不影响之前的规则。

4. 返回 Map<String, AnomalyScore>：
   - key：transaction.getExternalId() 或 fingerprint.hashValue（若 externalId 为 null）
   - 仅返回有异常的记录

5. 不修改输入对象，不查询数据库（MVP 简化，仅基于输入列表统计）
6. 日志：SLF4J，打印检测到的异常数量和类型（不打印具体金额）
7. 完成后执行：mvn test -Dtest=AnomalyDetectorImplTest

检查点：完成后暂停，等待我 review

#### task: 实现 AlipayCSVAdapter 的 adapt 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/acl/AlipayCSVAdapter.java
- 接口定义：com.finhub.fundflow.acl.DataSourceAdapter（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 仅支持 2024 新版支付宝 CSV 格式（字段顺序）：
   交易时间,交易分类,交易对方,对方账号,商品说明,金额,收/支,交易状态,交易订单号,商家订单号,备注,资金状态

2. 编码自动识别：
   - 优先 UTF-8，失败尝试 GBK
   - 均失败 → IllegalArgumentException("无法识别文件编码: " + filename)

3. 金额解析：
   - 去除 "¥" "￥" 符号
   - 处理 "+/-" 前缀（"+" = 收入，"-" = 支出）
   - 转为 BigDecimal
   - 解析失败 → 记录 WARN，跳过该行

4. 方向映射：
   - "收入" / "IN" → Direction.IN
   - "支出" / "OUT" → Direction.OUT
   - 其他 → 记录 WARN，跳过该行

5. 时间解析：
   - 格式：yyyy-MM-dd HH:mm:ss
   - 解析失败 → 记录 WARN，跳过该行

6. 异常行处理：
   - 字段缺失、格式错误 → 记录 WARN，跳过该行，不阻断整个文件
   - 空文件 → 返回空列表

7. 返回 RawRecord：
   - externalId = 交易订单号（可能为 null）
   - amount = 解析后的金额
   - currency = "CNY"（默认）
   - direction = 映射后的 Direction
   - counterparty = 交易对方
   - remark = 备注 + 商品说明（拼接）
   - transTime = 解析后的时间
   - sourceSystem = "ALIPAY"

8. 禁止直接创建 Transaction 聚合根
9. 使用 Apache Commons CSV（若 pom 未引入，用标准 Java 逐行解析，禁止修改 pom.xml）
10. 完成后执行：mvn test -Dtest=AlipayCSVAdapterTest

检查点：完成后暂停，等待我 review

#### 任务：实现 WechatCSVAdapter

范围：
- 实现文件：src/main/java/.../infrastructure/acl/WechatCSVAdapter.java
- 接口定义：com.finhub.fundflow.acl.DataSourceAdapter（禁止修改）
- 禁止修改：接口文件、测试文件、AlipayCSVAdapter

要求：
1. 支持 2024 新版微信账单 CSV 格式（字段见测试契约）
2. 编码：优先 UTF-8，失败尝试 GBK
3. 金额解析：兼容有/无 ¥ 符号，有 +/- 前缀
4. 方向映射："收入"/"支出" → 透传（应用层转换 Direction）
5. 商品 + 备注拼接 → RawRecord.remark()
6. 交易单号 → RawRecord.externalId()
7. 异常行跳过并记录 WARN
8. 不支持支付宝格式 → UnsupportedOperationException("暂不支持该数据源: ALIPAY")
9. 完成后执行：mvn test -Dtest=WechatCSVAdapterTest

检查点：完成后暂停