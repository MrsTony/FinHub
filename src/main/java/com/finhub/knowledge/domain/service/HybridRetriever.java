package com.finhub.knowledge.domain.service;

import com.finhub.knowledge.domain.vo.KnowledgeChunk;
import java.util.List;

/**
 * 混合检索领域服务：BM25 粗排 + 向量精排 → RRF 融合。
 */
public interface HybridRetriever {

    List<KnowledgeChunk> retrieve(String query, int topK);
}