-- ============================================================================
-- V2__expand_category.sql
-- 扩展 Category 枚举白名单：新增 INSURANCE(保险)、TRANSFER(转账)
-- 背景：分类器补 INSURANCE/TRANSFER 枚举后，V1 的 chk_category CHECK 约束
--      不含这两值，落库会被 MySQL 8.0 CHECK 拒绝。本迁移重建约束以放行。
-- 对应领域对象：com.finhub.fundflow.domain.vo.Category
-- 注意：V1 已在远程库应用，不可改；本迁移为增量 ALTER。
-- ============================================================================

ALTER TABLE fin_transactions DROP CHECK chk_category;

ALTER TABLE fin_transactions ADD CONSTRAINT chk_category CHECK (category IN (
    'FOOD',          -- 餐饮
    'TRANSPORT',     -- 交通
    'SHOPPING',      -- 购物
    'HOUSING',       -- 住房
    'MEDICAL',       -- 医疗
    'EDUCATION',     -- 教育
    'ENTERTAINMENT', -- 娱乐
    'INCOME',        -- 收入（仅允许 IN 方向）
    'SUBSCRIPTION',  -- 订阅/周期性扣款
    'INSURANCE',     -- 保险（新增）：保险支出 OUT，退款 IN 保留原分类
    'TRANSFER',      -- 转账/个人收款（新增）：双向，转出 OUT / 转入或退回 IN
    'UNCLASSIFIED'   -- 未分类（初始状态）
));
