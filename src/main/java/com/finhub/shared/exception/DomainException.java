/**
 * 共享异常基类（跨上下文共享）
 */
package com.finhub.shared.exception;

/**
 * 领域异常基类：所有业务异常由此派生。
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}