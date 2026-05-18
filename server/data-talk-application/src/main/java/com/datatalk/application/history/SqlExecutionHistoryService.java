package com.datatalk.application.history;

import java.util.List;

public interface SqlExecutionHistoryService {
    void record(SqlExecutionRecord record);

    List<SqlExecutionRecord> list(SqlExecutionHistoryQuery query);

    record SqlExecutionHistoryQuery(
        String sessionId,
        String connectionId,
        String databaseName,
        StatusFilter statusFilter,
        int limit
    ) {
        public enum StatusFilter { SUCCESS, FAILURE, ALL }
    }
}
