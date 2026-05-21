package com.datatalk.application.connection;

public class ConnectionInUseException extends RuntimeException {
    public ConnectionInUseException(String message) {
        super(message);
    }
}
