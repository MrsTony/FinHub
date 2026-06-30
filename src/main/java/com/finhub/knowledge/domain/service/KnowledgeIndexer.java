package com.finhub.knowledge.domain.service;

import com.finhub.knowledge.domain.aggregate.Document;

/**
 * 知识索引领域服务：Document 分块 → Embedding → 写入向量存储。
 */
public interface KnowledgeIndexer {

    void index(Document document);
}