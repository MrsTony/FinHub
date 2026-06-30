package com.finhub.knowledge.acl;

/**
 * 防腐层：解析后的原始文档（未分块中间态，非领域实体）。
 */
public record RawDocument(String content, String filename, String contentType, int pageCount) {
}