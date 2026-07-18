package com.finhub.fundflow.interfaces;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 全局异常处理：将应用层抛出的异常统一映射为 HTTP 响应。
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} -> 400（请求参数/文件非法，或数据源无法识别）</li>
 *   <li>其他未捕获异常 -> 500（不向外暴露堆栈，仅返回通用错误信息）</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 请求的资源不存在（如 springdoc 禁用后 /v3/api-docs 等）：返回 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
        log.warn("请求的资源不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "资源不存在"));
    }

    /** 请求参数非法或数据源无法识别：返回 400 + 原始错误消息 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("请求参数非法: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 其他未预期异常：返回 500，避免泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("处理请求时发生未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器内部错误"));
    }
}
