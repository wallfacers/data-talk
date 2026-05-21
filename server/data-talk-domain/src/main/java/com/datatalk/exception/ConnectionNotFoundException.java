package com.datatalk.exception;

/**
 * 连接不存在异常
 */
public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException(String id) {
        super("Database connection not found: " + id);
    }
}
