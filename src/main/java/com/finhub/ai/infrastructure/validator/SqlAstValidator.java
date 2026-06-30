package com.finhub.ai.infrastructure.validator;

import com.finhub.ai.acl.NLTranslator.ValidatedSql;
import org.springframework.stereotype.Component;

/**
 * SQL AST 校验器：使用 JSqlParser 解析 AST，白名单校验。
 *
 * <ul>
 *   <li>仅允许 SELECT 语句</li>
 *   <li>仅允许查询白名单表（transactions + 聚合视图）</li>
 *   <li>禁止子查询、UNION、JOIN（MVP 阶段）</li>
 *   <li>禁止所有 DML（INSERT/UPDATE/DELETE/DROP）</li>
 * </ul>
 */
@Component
public class SqlAstValidator {

    public ValidatedSql validate(String candidateSql) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}