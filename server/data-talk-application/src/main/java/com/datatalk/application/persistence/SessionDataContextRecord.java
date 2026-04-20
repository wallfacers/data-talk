package com.datatalk.application.persistence;

public record SessionDataContextRecord(
    String sessionId,
    String connectionId,
    String connectionNameSnapshot,
    String databaseName,
    String schemaName,
    String selectedLevel,
    long updatedAt
) {}
