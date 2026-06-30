package com.finhub.knowledge.acl;

import java.util.List;

/**
 * 防腐层：Embedding 生成器（文本 → 向量）。
 * 技术实现：Ollama / OpenAI，核心域不感知。
 */
public interface EmbeddingGenerator {

    float[] embed(String text);

    /** 批量生成（一次最多 10 个，防止超时） */
    List<float[]> embedBatch(List<String> texts);
}