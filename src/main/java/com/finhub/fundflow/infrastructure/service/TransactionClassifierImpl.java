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
import java.util.HashMap;
import java.util.Map;

/**
 * TransactionClassifier 实现：规则引擎 + AI 兜底。
 *
 * <p>分类优先级：商户关键词规则匹配 → AI 建议（预留）</p>
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

    /** 商户关键词 → 分类映射 */
    private static final Map<String, Category> MERCHANT_KEYWORDS = new HashMap<>();

    static {
        MERCHANT_KEYWORDS.put("美团", Category.FOOD);
        MERCHANT_KEYWORDS.put("饿了么", Category.FOOD);
        MERCHANT_KEYWORDS.put("大众点评", Category.FOOD);
        MERCHANT_KEYWORDS.put("滴滴", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("高德", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("曹操", Category.TRANSPORT);
        MERCHANT_KEYWORDS.put("淘宝", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("京东", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("拼多多", Category.SHOPPING);
        MERCHANT_KEYWORDS.put("天猫", Category.SHOPPING);
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
        // 1. 解密对方户名
        String counterparty = decryptCounterparty(transaction.getCounterparty());

        log.debug("分类商户: {}", counterparty);

        // 2. 规则引擎：关键词匹配
        Category matchedCategory = matchKeyword(counterparty);

        if (matchedCategory != null) {
            // 2a. 方向兼容性校验
            Category validatedCategory = validateDirection(matchedCategory, transaction.getDirection());
            log.info("商户: {}, 匹配分类: {}, 最终分类: {}", counterparty, matchedCategory.getDisplayName(),
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

        log.info("商户: {}, 未匹配分类", counterparty);
        return new CategorySuggestion(Category.UNCLASSIFIED, CONFIDENCE_RULE_MISS, SOURCE_RULE);
    }

    /**
     * 解密对方户名。
     */
    private String decryptCounterparty(EncryptedString counterparty) {
        return counterparty.decrypt(encryptionKey);
    }

    /**
     * 商户关键词匹配（contains 语义）。
     *
     * @return 匹配的类别，未命中返回 null
     */
    private Category matchKeyword(String counterparty) {
        for (Map.Entry<String, Category> entry : MERCHANT_KEYWORDS.entrySet()) {
            if (counterparty.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 方向兼容性校验：
     * INCOME 类别必须 Direction 为 IN，支出类别必须 Direction 为 OUT。
     * 不兼容时返回 UNCLASSIFIED。
     */
    private Category validateDirection(Category category, Direction direction) {
        if (category == Category.INCOME && direction != Direction.IN) {
            return Category.UNCLASSIFIED;
        }
        if (category != Category.INCOME && direction != Direction.OUT) {
            return Category.UNCLASSIFIED;
        }
        return category;
    }
}
