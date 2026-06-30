# FinHub — 统一语言规范（Ubiquitous Language）

&gt; 版本：v1.0
&gt; 生效日期：2026-06-30
&gt; 关联文件：`CLAUDE_CODE.md`（协作规范）、`docs/adr/`（架构决策）
&gt;
&gt; 规则：任何人在代码、文档、会议、面试中使用以下术语，必须与此规范一致。
&gt; 禁止创造同义词（如"账单"和"交易记录"混用）。

---

## 一、核心域：资金流水上下文（Fund Flow Context）

### 1.1 聚合根

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **交易记录** | Transaction | 一笔资金流水的完整生命周期，聚合根 | 账单、订单、流水、记录 | `Transaction`（aggregate） |
| **交易标识** | Transaction ID | 聚合根代理键，技术标识，不暴露给业务 | 订单号、流水号 | `Transaction.id`（Long） |
| **业务标识** | External ID | 外部系统提供的业务唯一键（如支付宝 trade_no） | 第三方 ID、来源 ID | `Transaction.externalId`（String） |

### 1.2 值对象

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **金额** | Money | 不可变值对象，BigDecimal 封装，强制 2 位小数 | 数值、价格、费用、数字 | `Money`（vo/record） |
| **币种** | Currency | ISO-4217 代码（CNY/USD/EUR），Money 的组成部分 | 货币、钱、人民币 | `Money.currency`（String） |
| **资金流向** | Direction | 收入（IN）或支出（OUT） | 类型、收支、正负 | `Direction`（enum：IN/OUT） |
| **分类** | Category | 交易类别（餐饮/交通/购物等），规则+AI 共同决定 | 标签、类型、科目 | `Category`（enum） |
| **分类建议** | Category Suggestion | AI 或规则引擎返回的建议值+置信度，非最终决策 | 预测结果、AI 分类、推荐 | `CategorySuggestion`（vo/record） |
| **排重指纹** | Fingerprint | 结构化哈希值，用于判断交易是否重复 | 哈希、MD5、唯一键、标识 | `Fingerprint`（vo/record） |
| **盐值** | Salt | 指纹生成用的随机种子，每个实例独立 | 密钥、密码、随机数 | `Fingerprint.salt`（String） |
| **加密字符串** | Encrypted String | 敏感字段的密文形态，入库即加密 | 密文、脱敏数据、安全字段 | `EncryptedString`（vo/record） |
| **异常评分** | Anomaly Score | 0.0~1.0 的异常程度评估，结构化 | 风险分、可疑度、警告级别 | `AnomalyScore`（vo/record） |

### 1.3 领域服务

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **排重服务** | Deduplication Service | 跨交易排重判断，三重防重机制 | 去重工具、重复检查、清洗 | `DeduplicationService`（domain/service） |
| **指纹生成器** | Fingerprint Generator | 生成结构化排重指纹的领域服务 | 哈希生成器、ID 生成器 | `FingerprintGenerator`（domain/service） |
| **交易分类器** | Transaction Classifier | 基于规则+AI 建议确定分类的领域服务 | 分类引擎、标签器、AI 模型 | `TransactionClassifier`（domain/service） |
| **异常侦探** | Anomaly Detector | 基于统计规则检测异常消费模式的领域服务 | 风控引擎、预警系统、监控 | `AnomalyDetector`（domain/service） |

### 1.4 领域事件

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **交易已导入** | Transaction Imported | 交易成功持久化后发布的事件 | 新增事件、创建完成、入库 | `TransactionImportedEvent` |
| **发现重复** | Duplicate Detected | 排重过程中发现重复交易时发布 | 重复警告、冲突事件 | `DuplicateDetectedEvent` |
| **交易已分类** | Transaction Classified | 分类决策完成后发布的事件 | 标签事件、分类完成 | `TransactionClassifiedEvent` |
| **发现异常** | Anomaly Detected | 异常标记完成后发布的事件 | 风险事件、预警事件 | `AnomalyDetectedEvent` |

### 1.5 防腐层概念

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **原始记录** | Raw Record | CSV 解析后的中间态数据，非领域实体 | 原始数据、CSV 行、导入数据 | `RawRecord`（acl/record） |
| **数据源适配器** | Data Source Adapter | 将外部格式（CSV/PDF）转换为原始记录的防腐层 | 解析器、读取器、转换器 | `DataSourceAdapter`（acl/interface） |

---

## 二、支撑域：本地知识库上下文（Local Knowledge Context）

### 2.1 聚合根

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **文档** | Document | 上传的 PDF/Markdown/TXT 文件，知识库聚合根 | 文件、资料、素材、附件 | `Document`（aggregate） |
| **文档状态** | Document Status | 文档生命周期状态（PENDING/INDEXED/FAILED） | 处理状态、上传状态 | `Document.Status`（enum） |

### 2.2 值对象

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **知识块** | Knowledge Chunk | 文档分块后的可检索单元，含文本+向量+元数据 | 片段、段落、切片、块 | `KnowledgeChunk`（vo/record） |
| **块元数据** | Chunk Metadata | 知识块的来源信息（文档 ID、页码、段落序号） | 位置信息、索引信息 | `ChunkMetadata`（vo/record） |
| **嵌入向量** | Embedding Vector | 文本的浮点数组表示，用于语义检索 | 向量、特征、编码 | `float[]`（EmbeddingGenerator 输出） |

### 2.3 领域服务

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **知识索引器** | Knowledge Indexer | 文档分块 → Embedding → 写入向量存储的领域服务 | 索引服务、入库服务 | `KnowledgeIndexer`（domain/service） |
| **混合检索器** | Hybrid Retriever | BM25 关键词粗排 + 向量精排 → RRF 融合的领域服务 | 搜索引擎、查询器、RAG | `HybridRetriever`（domain/service） |

### 2.4 防腐层概念

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **原始文档** | Raw Document | 解析后的纯文本内容，未分块 | 文本内容、解析结果 | `RawDocument`（acl/record） |
| **文档解析器** | Document Parser | 将 PDF/Markdown 转换为原始文档的防腐层 | 阅读器、提取器、OCR | `DocumentParser`（acl/interface） |
| **嵌入生成器** | Embedding Generator | 将文本转换为向量的防腐层 | 编码器、向量化模型、AI 模型 | `EmbeddingGenerator`（acl/interface） |

---

## 三、支撑域：AI 辅助上下文（AI Assistance Context）

### 3.1 防腐层接口（核心域定义，AI 上下文实现）

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **自然语言查询** | Natural Language Query | 用户用自然语言表达的查询请求 | 提问、搜索词、关键词 | `NLTranslator.NaturalLanguageQuery` |
| **校验后 SQL** | Validated SQL | 经 AST 白名单校验后的安全 SQL | 查询语句、数据库语句 | `NLTranslator.ValidatedSql` |
| **分类建议引擎** | Category Suggestion Engine | 为交易生成分类建议的 AI 防腐层 | 分类 AI、推荐引擎 | `CategorySuggestionEngine`（acl/interface） |
| **异常解释器** | Anomaly Explainer | 将异常评分翻译为自然语言的 AI 防腐层 | 解释 AI、文案生成器 | `AnomalyExplainer`（acl/interface） |
| **自然语言翻译器** | NL Translator | 将自然语言转换为结构化查询的 AI 防腐层 | NL2SQL、查询翻译、语义解析 | `NLTranslator`（acl/interface） |

### 3.2 AI 输出值对象

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **异常解释** | Anomaly Explanation | 异常评分的自然语言解释 + 建议操作 | 解释文本、预警文案 | `AnomalyExplainer.AnomalyExplanation` |
| **SQL 校验状态** | SQL Validation Status | AST 校验结果（有效/无效 + 拒绝原因） | 安全检查、过滤结果 | `ValidatedSql.isValid()`（boolean） |

---

## 四、支撑域：查询分析上下文（Query Analysis Context）

### 4.1 应用层服务

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **查询路由器** | Query Router | 责任链：正则 → 规则 → AI → RAG 的查询分发器 | 查询分发器、路由引擎 | `QueryRouter`（application） |
| **聚合视图** | Aggregation View | 读模型，按月/按分类汇总的查询结果 | 统计结果、报表、汇总 | `AggregationView`（domain/view） |

---

## 五、通用域：数据治理上下文（Data Governance Context）

### 5.1 核心概念

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **提示词模板** | Prompt Template | 结构化的大模型输入模板，含占位符 | Prompt、指令、系统提示 | `PromptTemplate`（datagov） |
| **提示词注册表** | Prompt Registry | 管理所有 Prompt Template 版本化的服务 | 模板库、提示词管理 | `PromptRegistry`（datagov） |
| **黄金数据集** | Golden Set | 人工标注的评估基准，用于测试 AI 准确率 | 测试集、基准数据、标准答案 | `GoldenSet`（datagov） |
| **评估用例** | Eval Case | 单个评估样本（输入 + 预期输出 + 实际输出 + 评分） | 测试用例、样本 | `EvalCase`（datagov） |
| **数据版本控制** | Data Version Control (DVC) | 管理 Prompt/Golden Set 版本化的工具 | 数据管理、版本工具 | `DVC`（工具，非代码） |

---

## 六、通用域：基础设施上下文（Infra Context）

### 6.1 部署与协议

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **模型上下文协议** | Model Context Protocol (MCP) | 将 FinHub 能力暴露给 AI 客户端的标准协议 | API、接口、工具调用 | `McpToolDispatcher`（infra/mcp） |
| **MCP 工具** | MCP Tool | 原子化的能力单元（如 query_transactions） | 函数、接口、方法 | `McpToolDispatcher.TOOL_*`（常量） |
| **健康检查** | Health Check | 容器编排中的服务可用性探测 | 心跳、状态检查、探活 | `/actuator/health`（端点） |

### 6.2 安全

| 术语 | 英文 | 定义 | 禁止的替代说法 | 代码映射 |
|------|------|------|------------|---------|
| **字段级加密** | Field-Level Encryption | 敏感字段（对方户名、备注）的应用层 AES 加密 | 数据库加密、TDE、脱敏 | `EncryptedString`（domain/vo） |
| **日志脱敏** | Log Masking | 日志中敏感字段替换为掩码（***） | 日志过滤、安全日志 | `LogMaskingFilter`（infra/config） |
| **基础认证** | Basic Authentication | MVP 阶段的 HTTP Basic Auth 认证 | 简单认证、用户名密码 | `SecurityConfig`（infra/config） |

---

## 七、跨上下文协作术语

### 7.1 上下文映射关系

| 术语 | 定义 | 使用场景 |
|------|------|---------|
| **上游** | 被依赖的上下文（如资金流水是查询分析的上游） | 讨论依赖方向 |
| **下游** | 依赖上游的上下文（如查询分析依赖资金流水） | 讨论依赖方向 |
| **客户-供应商** | 下游请求，上游提供（如查询分析请求资金流水的读模型） | 描述协作模式 |
| **防腐层** | 下游定义的接口，上游实现的翻译层 | 描述技术隔离 |
| **发布-订阅** | 上游发布事件，下游异步订阅 | 描述事件协作 |
| **合作伙伴** | 两个上下文相互协作，无明确上下游 | 描述对等协作 |

### 7.2 禁止的模糊说法

| 模糊说法 | 问题 | 正确说法 |
|---------|------|---------|
| "账单" | 可能指交易记录、原始 CSV、报销单 | **交易记录**（Transaction）或 **原始记录**（Raw Record） |
| "分类" | 可能指动词（动作）或名词（结果） | **分类**（Category，名词）/ **分类器**（Classifier，服务）/ **分类建议**（Suggestion，值对象） |
| "去重" | 技术操作，无业务含义 | **排重**（Deduplication，业务动作）/ **排重服务**（DeduplicationService） |
| "AI 分类" | 模糊指代 AI 输出或最终分类 | **分类建议**（Suggestion，AI 输出）/ **已分类**（Classified，聚合根状态） |
| "上传文件" | 可能指 CSV 导入或 PDF 知识库 | **导入**（Ingestion，资金流水）/ **上传文档**（Upload Document，知识库） |
| "查询" | 可能指数据库查询或用户提问 | **聚合查询**（Aggregation Query，数据库）/ **自然语言查询**（NL Query，用户提问） |

---

## 八、术语命名规范（代码级）

### 8.1 包名 → 上下文映射

| 包路径 | 所属上下文 | 允许的内容 |
|--------|----------|-----------|
| `fundflow.domain` | 资金流水（核心域） | 聚合根、值对象、领域服务接口、领域事件、仓库接口 |
| `fundflow.application` | 资金流水（应用层） | AppService、事务边界、事件发布 |
| `fundflow.infrastructure` | 资金流水（基础设施） | 仓库实现、缓存实现、MyBatis Mapper |
| `fundflow.acl` | 资金流水（防腐层） | DataSourceAdapter 接口、RawRecord |
| `knowledge.domain` | 知识库（支撑域） | Document、KnowledgeChunk、领域服务接口 |
| `knowledge.acl` | 知识库（防腐层） | DocumentParser、EmbeddingGenerator 接口 |
| `ai.acl` | AI 辅助（支撑域） | CategorySuggestionEngine、AnomalyExplainer、NLTranslator 接口 |
| `query.application` | 查询分析（支撑域） | QueryRouter、读模型 |
| `infra.mcp` | 基础设施（通用域） | MCP 协议适配、Tool 分发 |
| `datagov` | 数据治理（通用域） | PromptRegistry、GoldenSet 管理 |

### 8.2 类名 → 类型映射

| 后缀 | 含义 | 示例 |
|------|------|------|
| `*Test` | 单元测试 | `MoneyTest` |
| `*AppService` | 应用层编排服务 | `IngestionAppService` |
| `*Service` | 领域服务接口 | `DeduplicationService` |
| `*ServiceImpl` | 领域服务实现 | `DeduplicationServiceImpl` |
| `*Repository` | 仓库接口（领域层） | `TransactionRepository` |
| `*RepositoryImpl` | 仓库实现（基础设施） | `TransactionRepositoryImpl` |
| `*Mapper` | MyBatis-Plus 映射器 | `TransactionMapper` |
| `*Adapter` | 防腐层适配器 | `AlipayCSVAdapter` |
| `*Engine` | AI/算法引擎（防腐层） | `CategorySuggestionEngine` |
| `*Generator` | 生成器（领域服务） | `FingerprintGenerator` |
| `*Detector` | 检测器（领域服务） | `AnomalyDetector` |
| `*Router` | 路由器（应用层） | `QueryRouter` |
| `*Dispatcher` | 分发器（基础设施） | `McpToolDispatcher` |
| `*Event` | 领域事件 | `TransactionImportedEvent` |
| `*Config` | 配置类（基础设施） | `SecurityConfig` |
| `*Filter` | 过滤器（基础设施） | `LogMaskingFilter` |

---

## 九、面试话术（术语一致性展示）

&gt; "FinHub 的统一语言规范定义了 40+ 个核心术语，跨团队强制一致。比如'交易记录'只能叫 `Transaction`，禁止用'账单'或'订单'；'排重'是业务动作 `Deduplication`，不是技术操作'去重'；AI 输出的是'分类建议'（`CategorySuggestion`），最终采纳的是'分类'（`Category`），决策权在聚合根。这套规范直接映射到代码包结构和类命名，比如 `fundflow.domain.vo.Money`、`fundflow.domain.service.DeduplicationService`，术语、设计、代码三者一致，新人看到类名就能理解业务含义。"

---

## 十、演进记录

| 版本 | 日期 | 新增术语 | 变更原因 |
|------|------|---------|---------|
| v1.0 | 2026-06-30 | 全部初始术语 | 项目启动，基于上下文战略设计 |
| | | `Category Suggestion` 区分 `Category` | 明确 AI 建议 vs 聚合根决策 |
| | | `Anomaly Score` + `Anomaly Explanation` | 区分结构化评分 vs 自然语言解释 |
| | | `Raw Record` vs `Raw Document` | 区分资金流水防腐层 vs 知识库防腐层 |

---

## 关联文件

- `CLAUDE_CODE.md`：协作规范，指令模板中使用本规范术语
- `docs/claude-code/task-templates.md`：任务模板，必须引用本规范中的类名
- `docs/adr/`：架构决策，解释术语背后的技术选型