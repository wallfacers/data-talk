package com.datatalk.application.history;

public record SqlExecutionRecord(
    String sessionId,
    String connectionId,
    String databaseName,
    String schemaName,
    String sqlText,
    Status status,
    String errorCode,
    String errorMessage,
    long executedAt,
    Long durationMs,
    Integer rowCount
) {
    public enum Status { SUCCESS, FAILURE }

    public static SqlExecutionRecord success(
        String sessionId, String connectionId, String databaseName, String schemaName,
        String sqlText, long executedAt, long durationMs, int rowCount
    ) {
        return new SqlExecutionRecord(sessionId, connectionId, databaseName, schemaName,
            sqlText, Status.SUCCESS, null, null, executedAt, durationMs, rowCount);
    }

    public static SqlExecutionRecord failure(
        String sessionId, String connectionId, String databaseName, String schemaName,
        String sqlText, String errorCode, String errorMessage, long executedAt, long durationMs
    ) {
        return new SqlExecutionRecord(sessionId, connectionId, databaseName, schemaName,
            sqlText, Status.FAILURE, errorCode, errorMessage, executedAt, durationMs, null);
    }
}
