package com.finhub.ai.acl;

/**
 * 防腐层：自然语言转结构化查询（NL2SQL / NL2Filter）。
 * 输入自然语言，输出校验后的 SQL。
 */
public interface NLTranslator {

    ValidatedSql translate(NaturalLanguageQuery query);

    record NaturalLanguageQuery(String rawText, String userContext) {
    }

    record ValidatedSql(String sql, boolean isValid, String rejectionReason) {
    }
}