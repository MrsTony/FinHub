package com.finhub.ai.infrastructure.translator;

import com.finhub.ai.acl.NLTranslator;
import org.springframework.stereotype.Component;

/**
 * NLTranslator 的 Spring AI 实现。
 * 从 PromptRegistry 加载模板，调用 LLM，返回 ValidatedSql。
 */
@Component
public class SpringAiNLTranslator implements NLTranslator {

    @Override
    public ValidatedSql translate(NaturalLanguageQuery query) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }
}