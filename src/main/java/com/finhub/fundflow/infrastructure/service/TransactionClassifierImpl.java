package com.finhub.fundflow.infrastructure.service;

import com.finhub.ai.acl.CategorySuggestionEngine;
import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.service.TransactionClassifier;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.CategorySuggestion;
import com.finhub.fundflow.domain.vo.Direction;
import com.finhub.fundflow.domain.vo.EncryptedString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TransactionClassifier 实现：规则引擎 + AI 兜底。
 *
 * <p>分类优先级：商户关键词规则匹配 -> AI 建议（预留）</p>
 */
@Slf4j
@Service
public class TransactionClassifierImpl implements TransactionClassifier {

    /** 默认加密密钥（MVP 阶段，生产环境应通过构造器注入） */
    private static final String DEFAULT_ENCRYPTION_KEY = "12345678901234567890123456789012";

    /** 规则命中置信度 */
    private static final BigDecimal CONFIDENCE_RULE_HIT = BigDecimal.ONE;

    /** 规则未命中置信度 */
    private static final BigDecimal CONFIDENCE_RULE_MISS = BigDecimal.ZERO;

    /** 规则来源标识 */
    private static final String SOURCE_RULE = "RULE";

    /** 加密密钥 */
    private final String encryptionKey;

    /** AI 建议引擎（MVP 阶段为 null，预留扩展） */
    private final CategorySuggestionEngine aiEngine;

    /** 商户关键词 -> 分类映射（LinkedHashMap 保序，匹配结果可预测） */
    private static final Map<String, Category> MERCHANT_KEYWORDS = new LinkedHashMap<>();

    static {
        // 餐饮
        MERCHANT_KEYWORDS.put("美团", Category.FOOD);
        MERCHANT_KEYWORDS.put("饿了么", Category.FOOD);
        MERCHANT_KEYWORDS.put("大众点评", Category.FOOD);
        MERCHANT_KEYWORDS.put("餐饮", Category.FOOD);
        MERCHANT_KEYWORDS.put("咖啡", Category.FOOD);
        MERCHANT_KEYWORDS.put("coffee", Category.FOOD);
        MERCHANT_KEYWORDS.put("蜜雪", Category.FOOD);
        MERCHANT_KEYWORDS.put("茶姬", Category.FOOD);
        MERCHANT_KEYWORDS.put("羊汤", Category.FOOD);
        MERCHANT_KEYWORDS.put("食间", Category.FOOD);
        MERCHANT_KEYWORDS.put("拉面", Category.FOOD);
        MERCHANT_KEYWORDS.put("面馆", Category.FOOD);
        MERCHANT_KEYWORDS.put("醉面", Category.FOOD);
        // 交通
        MERCHANT_KEYWORDS.put("滴滴", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("高德", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("曹操", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("哈啰", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("12306", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("铁路", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("轨道", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("公交", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("地铁", Category.TRANSPORT);
        // 购物
        MERCHANT_KEYWORDS.put("淘宝", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("京东", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("拼多多", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("天猫", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("便利蜂", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("盒马", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("抖音", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("电商", Category.SHOPPING);
        // 住房
        MERCHANT_KEYWORDS.put("燃气", Category.HOUSING);
        MERCHANT_KEYWORDS.put("物业", Category.HOUSING);
        MERCHANT_KEYWORDS.put("房租", Category.HOUSING);
        // 医疗
        MERCHANT_KEYWORDS.put("药房", Category.MEDICAL);
        MERCHANT_KEYWORDS.put("药店", Category.MEDICAL);
        MERCHANT_KEYWORDS.put("医院", Category.MEDICAL);
        // 订阅/数字服务（AI 服务、话费、软件/视频会员）
        MERCHANT_KEYWORDS.put("WPS", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("网盘", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("爱奇艺", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("联通", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("移动", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("电信", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("话费", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("深度求索", Category.SUBSCRIPTION);
        MERCHANT_KEYWORDS.put("月之暗面", Category.SUBSCRIPTION);
        // 保险
        MERCHANT_KEYWORDS.put("保险", Category.INSURANCE);
        MERCHANT_KEYWORDS.put("众安", Category.INSURANCE);
        MERCHANT_KEYWORDS.put("平安", Category.INSURANCE);
        // 收入
        MERCHANT_KEYWORDS.put("支付宝", Category.INCOME);
        MERCHANT_KEYWORDS.put("工资", Category.INCOME);
        MERCHANT_KEYWORDS.put("奖金", Category.INCOME);
    }

    /** MVP 无参构造器（测试兼容），使用默认密钥；生产环境 Spring 不会使用此构造器 */
    public TransactionClassifierImpl() {
        this(DEFAULT_ENCRYPTION_KEY, null);
    }

    /** 生产环境构造器：加密密钥由配置注入，与 IngestionAppService 加密用的密钥一致（E2E 闭环） */
    @Autowired
    public TransactionClassifierImpl(@Value("${finhub.encryption.key}") String encryptionKey) {
        this(encryptionKey, null);
    }

    /** 完整构造器（密钥 + AI 引擎注入） */
    public TransactionClassifierImpl(String encryptionKey, CategorySuggestionEngine aiEngine) {
        this.encryptionKey = encryptionKey;
        this.aiEngine = aiEngine;
    }

    @Override
    public CategorySuggestion classify(Transaction transaction) {
        // 1. 解密对方户名 + 备注（备注含商品说明，是分类的辅助信号）
        String counterparty = decrypt(transaction.getCounterparty());
        String remark = decrypt(transaction.getRemark());

        log.debug("分类商户: {}", counterparty);

        // 2. 规则引擎：商户名优先匹配，未命中再查备注
        Category matchedCategory = matchKeyword(counterparty, remark);

        if (matchedCategory != null) {
            // 2a. 方向兼容性校验（委托 Category 值对象）
            Category validatedCategory = validateDirection(matchedCategory, transaction.getDirection());
            log.debug("商户: {}, 匹配分类: {}, 最终分类: {}", counterparty, matchedCategory.getDisplayName(),
                    validatedCategory.getDisplayName());
            return new CategorySuggestion(validatedCategory,
                    validatedCategory == Category.UNCLASSIFIED ? CONFIDENCE_RULE_MISS : CONFIDENCE_RULE_HIT,
                    SOURCE_RULE);
        }

        // 3. AI 兜底（预留）
        if (aiEngine != null) {
            log.debug("规则未命中，调用 AI 引擎");
            return aiEngine.suggest(transaction);
        }

        log.debug("商户: {}, 未匹配分类", counterparty);
        return new CategorySuggestion(Category.UNCLASSIFIED, CONFIDENCE_RULE_MISS, SOURCE_RULE);
    }

    /**
     * 解密 EncryptedString 字段，null 安全返回空串。
     */
    private String decrypt(EncryptedString field) {
        return field == null ? "" : field.decrypt(encryptionKey);
    }

    /**
     * 商户关键词匹配（contains 语义）。商户名优先，未命中再查备注（商品说明兜底）。
     *
     * @return 匹配的类别，未命中返回 null
     */
    private Category matchKeyword(String counterparty, String remark) {
        Category hit = matchIn(counterparty);
        if (hit != null) {
            return hit;
        }
        return matchIn(remark);
    }

    /** 单文本关键词匹配 */
    private Category matchIn(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Category> entry : MERCHANT_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 方向兼容性校验：委托 Category 值对象的方向兼容性方法。
     * INCOME 仅允许 IN；支出类允许 OUT(消费) 与 IN(退款保留原分类)；TRANSFER 双向。
     * 不兼容时返回 UNCLASSIFIED（如 INCOME 遇 OUT）。
     */
    private Category validateDirection(Category category, Direction direction) {
        if (direction == Direction.IN && !category.isIncomeCompatible()) {
            return Category.UNCLASSIFIED;
        }
        if (direction == Direction.OUT && !category.isExpenseCompatible()) {
            return Category.UNCLASSIFIED;
        }
        return category;
    }
}
