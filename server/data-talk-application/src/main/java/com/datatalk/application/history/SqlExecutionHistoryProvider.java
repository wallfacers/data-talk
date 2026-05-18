package com.datatalk.application.history;

import java.util.List;

/**
 * Read-only view of recent SQL executions, consumed by AgentPromptBuilder
 * to render {{RECENT_FAILED_QUERIES_DIGEST}} and by ActiveConnectionSummary
 * to surface recent successful queries.
 */
public interface SqlExecutionHistoryProvider {
    List<SqlExecutionRecord> recentFailures(String sessionId, int limit);

    List<SqlExecutionRecord> recentSuccesses(String sessionId, int limit);
}
