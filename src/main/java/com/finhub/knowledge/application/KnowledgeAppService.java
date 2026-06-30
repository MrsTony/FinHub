package com.finhub.knowledge.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用层：知识库管理编排服务。
 * 编排：上传 → 解析 → 分块 → Embedding → 索引 → 持久化。
 */
@Service
@Transactional
public class KnowledgeAppService {
    // TODO
}