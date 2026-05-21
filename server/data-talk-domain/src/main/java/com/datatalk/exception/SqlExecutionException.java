package com.datatalk.exception;

/**
 * SQL 执行异常
 */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
