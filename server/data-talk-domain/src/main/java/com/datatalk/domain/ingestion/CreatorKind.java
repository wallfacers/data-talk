package com.datatalk.domain.ingestion;

public enum CreatorKind {
    AI("ai"),
    USER("user");

    private final String dbValue;

    CreatorKind(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static CreatorKind fromDbValue(String value) {
        if (value == null) return AI;
        for (CreatorKind k : values()) {
            if (k.dbValue.equals(value)) return k;
        }
        throw new IllegalArgumentException("unknown creator kind: " + value);
    }
}
