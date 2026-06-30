package com.finhub.knowledge.domain.repository;

import com.finhub.knowledge.domain.aggregate.Document;
import java.util.Optional;

/**
 * 知识库聚合根仓库接口（领域层定义，基础设施层实现）。
 */
public interface DocumentRepository {

    Optional<Document> findById(Long id);

    Optional<Document> findByFileHash(String fileHash);

    void save(Document document);

    long count();
}