package com.finhub.knowledge.domain.aggregate;

import com.finhub.knowledge.domain.vo.KnowledgeChunk;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档聚合根：知识库中的上传文件。
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>必须至少包含一个 KnowledgeChunk</li>
 *   <li>文件类型在白名单内（PDF/Markdown/TXT）</li>
 *   <li>文件大小 &lt; 50MB</li>
 * </ul>
 */
public class Document {

    private Long id;
    private final String filename;
    private final String contentType;
    private final String fileHash;
    private final LocalDateTime uploadTime;
    private List<KnowledgeChunk> chunks;
    private DocumentStatus status;

    public enum DocumentStatus {
        PENDING, INDEXED, FAILED
    }

    private Document(String filename, String contentType, String fileHash) {
        this.filename = filename;
        this.contentType = contentType;
        this.fileHash = fileHash;
        this.uploadTime = LocalDateTime.now();
        this.status = DocumentStatus.PENDING;
    }

    /** 工厂方法：创建文档聚合根，校验文件类型白名单和大小限制。 */
    public static Document create(String filename, String contentType, String fileHash) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** 按段落/标题切分文档为 KnowledgeChunk 列表。 */
    public List<KnowledgeChunk> splitIntoChunks() {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    // ── Getters ──
    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public String getFileHash() { return fileHash; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public List<KnowledgeChunk> getChunks() { return chunks; }
    public DocumentStatus getStatus() { return status; }
}