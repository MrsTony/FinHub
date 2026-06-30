-- ============================================================================
-- V1__create_fin_transactions_table.sql
-- 资金流水上下文：Transaction 聚合根持久化投影
-- 对应领域对象：com.finhub.fundflow.domain.aggregate.Transaction
-- ============================================================================

CREATE TABLE IF NOT EXISTS fin_transactions (
    -- ------------------------------------------------------------------------
    -- 聚合根标识（代理键）
    -- 领域层不暴露给业务代码，仅用于基础设施持久化
    -- 对应：Transaction.id（Long，技术标识，非业务属性）
    -- ------------------------------------------------------------------------
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '聚合根代理键，技术标识，领域层不依赖',
    -- ------------------------------------------------------------------------
    -- 业务标识（外部系统提供）
    -- 支付宝 trade_no / 微信商户单号 / 银行流水号
    -- 对应：Transaction.externalId（String，业务键）
    -- 业务规则：导入时优先用于排重（第一重防重）
    -- ------------------------------------------------------------------------
    external_id         VARCHAR(128) UNIQUE COMMENT '业务唯一标识（支付宝 trade_no 等），第一重排重键',

    -- ------------------------------------------------------------------------
    -- Money 值对象（平铺存储）
    -- 对应：Transaction.money（Money 值对象）
    -- 值对象特性：无独立生命周期，随聚合根创建而生，不可变
    -- 业务规则：金额精度绝对化，强制 2 位小数，禁止 FLOAT/DOUBLE
    -- ADR-001: 金额用 BigDecimal + DECIMAL(18,2)
    -- ------------------------------------------------------------------------
    amount              DECIMAL(18, 2) NOT NULL
    COMMENT 'Money.amount：交易金额，精度强制 2 位小数，不可为负',
    currency            VARCHAR(3) DEFAULT 'CNY' NOT NULL
    COMMENT 'Money.currency：ISO-4217 币种代码（CNY/USD/EUR），默认人民币',

    -- ------------------------------------------------------------------------
    -- Direction 值对象（枚举）
    -- 对应：Transaction.direction（Direction 枚举：IN/OUT）
    -- 业务规则：与 Category 值对象做一致性校验
    --   - INCOME 类别只能对应 IN 方向
    --   - FOOD/TRANSPORT 等支出类别只能对应 OUT 方向
    -- 校验位置：Transaction.createFrom() 工厂方法 / markClassified() 方法
    -- ------------------------------------------------------------------------
    direction           VARCHAR(10) NOT NULL
    COMMENT 'Direction 值对象：IN（收入）/ OUT（支出），与 Category 做一致性校验',

    -- ------------------------------------------------------------------------
    -- Category 值对象（枚举，可变状态）
    -- 对应：Transaction.category（Category 值对象）
    -- 初始状态：UNCLASSIFIED（导入时未分类）
    -- 修改入口：Transaction.markClassified(Category, String source) 聚合根方法
    -- 业务规则：规则引擎优先，AI 建议补位，用户纠正为最终权威
    -- 领域服务参与：TransactionClassifier.classify() 给出 CategorySuggestion
    --   - 置信度 > 0.8 且来源为 RULE/AI 时，应用编排调用 markClassified()
    --   - 否则保持 UNCLASSIFIED，等待用户纠正
    -- ------------------------------------------------------------------------
    category            VARCHAR(32) NOT NULL DEFAULT 'UNCLASSIFIED'
    COMMENT 'Category 值对象：初始 UNCLASSIFIED，经 TransactionClassifier 领域服务建议后，由聚合根 markClassified() 方法决策采纳',

    -- ------------------------------------------------------------------------
    -- 业务约束：Category 枚举值白名单
    -- 对应：Category 枚举定义（10 个值）
    -- 注意：MySQL 8.0.16+ CHECK 约束生效，低版本由应用层校验兜底
    -- ------------------------------------------------------------------------
    CONSTRAINT chk_category CHECK (category IN (
                                   'FOOD',         -- 餐饮
                                   'TRANSPORT',    -- 交通
                                   'SHOPPING',     -- 购物
                                   'HOUSING',      -- 住房
                                   'MEDICAL',      -- 医疗
                                   'EDUCATION',    -- 教育
                                   'ENTERTAINMENT', -- 娱乐
                                   'INCOME',        -- 收入（仅允许 IN 方向）
                                   'SUBSCRIPTION',  -- 订阅/周期性扣款
                                   'UNCLASSIFIED'   -- 未分类（初始状态）
                                               )),

    -- ------------------------------------------------------------------------
    -- 交易时间（业务时间，非系统审计时间）
    -- 对应：Transaction.transTime（LocalDateTime）
    -- 业务规则：导入时从 CSV 解析，用于排重指纹生成（截断到分钟）
    -- 与 created_at 区别：trans_time 是银行/支付宝记录的业务发生时间
    -- ------------------------------------------------------------------------
    trans_time          DATETIME(3) NOT NULL
    COMMENT '业务交易时间（来自 CSV），排重指纹生成时截断到分钟',

    -- ------------------------------------------------------------------------
    -- EncryptedString 值对象（敏感字段：对方户名）
    -- 对应：Transaction.counterparty（EncryptedString 值对象）
    -- 安全规则：入库即密文，AES-256-CBC 加密，密钥由 Docker 环境变量注入
    -- 应用层解密入口：EncryptedString.decrypt(String key) → 明文
    -- 日志规则：任何日志禁止打印解密后的明文
    -- ADR-005: MVP 字段加密 + 日志脱敏
    -- ------------------------------------------------------------------------
    counterparty_cipher TEXT
    COMMENT 'EncryptedString：对方户名密文，AES-256 加密，禁止日志明文打印',

    -- ------------------------------------------------------------------------
    -- EncryptedString 值对象（敏感字段：交易备注）
    -- 对应：Transaction.remark（EncryptedString 值对象）
    -- 安全规则：同 counterparty_cipher
    -- 业务场景：用户纠正分类时，备注内容可能影响分类建议（AI 辅助上下文读取）
    -- ------------------------------------------------------------------------
    remark_cipher       TEXT
    COMMENT 'EncryptedString：交易备注密文，AES-256 加密，禁止日志明文打印',

    -- ------------------------------------------------------------------------
    -- Fingerprint 值对象（排重指纹）
    -- 对应：Transaction.fingerprint（Fingerprint 值对象）
    -- 生成规则：FingerprintGenerator 领域服务
    --   SHA256(金额截断精度 + 时间截断到分钟 + 对方户名标准化 + 备注空值占位 + 盐值)
    -- 业务规则：external_id 缺失时的第二重排重键
    -- 盐值来源：每个用户/实例独立，通过环境变量 FINHUB_FINGERPRINT_SALT 注入
    -- 边界处理：同一分钟多笔相同金额消费（如自动售货机连刷）可能误判，需业务容忍
    -- ------------------------------------------------------------------------
    fingerprint         VARCHAR(64) UNIQUE
    COMMENT 'Fingerprint 值对象：结构化哈希+盐，external_id 缺失时的第二重排重键',

    -- ------------------------------------------------------------------------
    -- SourceSystem 值对象（枚举）
    -- 对应：Transaction.sourceSystem（String，来源系统标识）
    -- 业务规则：决定调用哪个 DataSourceAdapter 防腐层实现
    --   ALIPAY → AlipayCSVAdapter
    --   WECHAT → WechatCSVAdapter（MVP 预留）
    --   BANK → BankCSVAdapter（MVP 预留）
    --   MANUAL → 手动录入
    -- ------------------------------------------------------------------------
    source_system       VARCHAR(32) NOT NULL
    COMMENT 'SourceSystem 值对象：ALIPAY/WECHAT/BANK/MANUAL，决定防腐层适配器路由',

    -- ------------------------------------------------------------------------
    -- 异常标记（AnomalyDetector 领域服务写入）
    -- 对应：Transaction.anomalyFlag（boolean）
    -- 修改入口：Transaction.markAnomaly(AnomalyScore score) 聚合根方法
    -- 业务规则：AnomalyDetector 检测 → 输出 AnomalyScore → 应用编排调用 markAnomaly()
    -- 与 anomaly_score 联动：markAnomaly() 同时设置 flag=true 和 score 值
    -- ------------------------------------------------------------------------
    anomaly_flag        BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '异常标记：AnomalyDetector 领域服务检测后，由聚合根 markAnomaly() 方法设置',

    -- ------------------------------------------------------------------------
    -- 异常评分（AnomalyScore 值对象）
    -- 对应：Transaction.anomalyScore（AnomalyScore 值对象）
    -- 业务规则：0.0 ~ 1.0，> 0.7 视为 HIGH 异常，> 0.5 视为 MEDIUM 异常
    -- 生成者：AnomalyDetector 领域服务（统计规则：金额 > 月均 3 倍 = HIGH，> 1.5 倍 = MEDIUM）
    -- 消费者：AnomalyExplainer 防腐层（AI 辅助上下文）将结构化评分翻译为自然语言解释
    -- ------------------------------------------------------------------------
    anomaly_score       DECIMAL(5, 4) DEFAULT NULL
    COMMENT 'AnomalyScore 值对象：0.0~1.0，AnomalyDetector 生成，AnomalyExplainer 消费解释',

    -- ------------------------------------------------------------------------
    -- 预留企业字段（MVP 阶段为空，未来企业版直接复用）
    -- 对应：Transaction.costCenter / Transaction.projectCode（String）
    -- 演进路径：Phase 3 企业版迁移时，增加 tenant_id 字段，所有查询强制带租户过滤
    -- 当前处理：应用层忽略，数据库允许 NULL，避免未来 schema 变更冲突
    -- ------------------------------------------------------------------------
    cost_center         VARCHAR(64) DEFAULT NULL
    COMMENT '预留企业字段：成本中心（MVP 为空，Phase 3 企业版启用）',
    project_code        VARCHAR(64) DEFAULT NULL
    COMMENT '预留企业字段：项目代码（MVP 为空，Phase 3 企业版启用）',

    -- ------------------------------------------------------------------------
    -- 技术审计字段（基础设施自动填充）
    -- 对应：Transaction.createdAt / Transaction.updatedAt / Transaction.version
    -- 注意：非领域属性，由 MyBatis-Plus 或数据库自动维护
    -- version：乐观锁，聚合根并发保护（防止同一笔交易重复导入竞态）
    -- ------------------------------------------------------------------------
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    COMMENT '技术审计：记录创建时间，非业务属性',
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
    COMMENT '技术审计：记录更新时间，非业务属性',
    version             INT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '乐观锁版本号：聚合根并发保护，防止重复导入竞态条件',

    -- ------------------------------------------------------------------------
    -- 索引设计（支撑查询分析上下文读模型需求）
    -- 所有索引基于"查询分析上下文"的 QueryRouter 责任链统计得出
    -- 面试话术："索引不是 DBA 的事，是领域知识——我知道用户最频繁的查询是按分类+时间汇总"
    -- ------------------------------------------------------------------------
    INDEX idx_trans_time (trans_time)
    COMMENT '支撑查询：时间范围筛选（"查上月账单"）',
    INDEX idx_category_time (category, trans_time)
    COMMENT '支撑查询：分类统计（"按月汇总餐饮支出"）—— 最频繁查询，联合索引避免全表扫描',
    INDEX idx_direction_time (direction, trans_time)
    COMMENT '支撑查询：收支筛选（"只看收入"）',
    INDEX idx_anomaly (anomaly_flag, trans_time)
    COMMENT '支撑查询：异常审查（"待处理异常交易"）',
    INDEX idx_source_system (source_system, trans_time)
    COMMENT '支撑查询：来源过滤（"只看支付宝"）'

    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='资金流水聚合根：Transaction 的持久化投影。所有字段对应领域层值对象或聚合根属性，禁止直接绕过聚合根修改。';

-- ============================================================================
-- 索引使用验证脚本（开发环境执行，生产环境由 DBA 审核）
-- ============================================================================
-- EXPLAIN SELECT * FROM transactions WHERE category = 'FOOD' AND trans_time BETWEEN '2024-01-01' AND '2024-01-31';
-- 预期结果：type=range, key=idx_category_time, Extra=Using index condition