package com.datatalk.domain.undo;

public record UndoLogEntry(
    String id,
    String sessionId,
    String connectionId,
    String databaseName,
    String schemaName,
    String tableName,
    String operation,
    String originalSql,
    String inverseSql,
    String beforeState,
    int affectedRows,
    boolean undoable,
    String status,
    long expiresAt,
    long createdAt,
    Long undoneAt
) {}
