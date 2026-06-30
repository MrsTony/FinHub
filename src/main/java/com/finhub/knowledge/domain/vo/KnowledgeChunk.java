package com.finhub.knowledge.domain.vo;

import java.util.List;
import java.util.Objects;

/**
 * 知识块值对象：文档分块后的可检索单元。
 * 单块内容不超过 512 字符。
 */
public record KnowledgeChunk(String content, float[] embedding, ChunkMetadata metadata) {

    public KnowledgeChunk {
        Objects.requireNonNull(content, "内容不能为空");
        Objects.requireNonNull(metadata, "元数据不能为空");
        if (content.length() > 512) {
            throw new IllegalArgumentException("单块内容不超过 512 字符");
        }
    }

    /** 是否包含关键词（用于 BM25 粗排） */
    public boolean containsKeywords(List<String> keywords) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    public record ChunkMetadata(String documentId, int pageNumber, int chunkIndex) {
    }
}