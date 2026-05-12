package com.datatalk.domain.ingestion;

public class IngestionTokenInvalidException extends RuntimeException {
    public enum Reason { NOT_FOUND, EXPIRED, ALREADY_CONSUMED, JOB_MISMATCH, MAPPING_HASH_MISMATCH }
    private final Reason reason;
    public IngestionTokenInvalidException(Reason reason) {
        super("Ingestion confirmation token invalid: " + reason);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
