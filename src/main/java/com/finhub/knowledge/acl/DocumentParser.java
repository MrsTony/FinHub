package com.finhub.knowledge.acl;

import java.io.InputStream;

/**
 * 防腐层：文档解析器（PDF/Markdown/Word → 文本）。
 * 知识域只认识 RawDocument，不知道 PDF 解析库细节。
 */
public interface DocumentParser {

    RawDocument parse(InputStream inputStream, String filename);
}