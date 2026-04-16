package com.datatalk.domain.error;

public class DataTalkException extends RuntimeException {
    private final String code;
    private final boolean retriable;
    private final Object details;

    public DataTalkException(String code, String message, boolean retriable, Object details) {
        super(message);
        this.code = code;
        this.retriable = retriable;
        this.details = details;
    }

    public DataTalkException(String code, String message, boolean retriable) {
        this(code, message, retriable, null);
    }

    public String code() { return code; }
    public boolean retriable() { return retriable; }
    public Object details() { return details; }
}
