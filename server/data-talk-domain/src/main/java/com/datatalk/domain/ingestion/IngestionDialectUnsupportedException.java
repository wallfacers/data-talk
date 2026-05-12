package com.datatalk.domain.ingestion;

public class IngestionDialectUnsupportedException extends RuntimeException {
    private final String kind;
    public IngestionDialectUnsupportedException(String kind) {
        super("Ingestion not supported for dialect: " + kind);
        this.kind = kind;
    }
    public String kind() { return kind; }
}
