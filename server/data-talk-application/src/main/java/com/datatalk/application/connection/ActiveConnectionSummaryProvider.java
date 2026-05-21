package com.datatalk.application.connection;

import java.util.List;
import java.util.Optional;

/**
 * Supplies the digest of the active session's bound connection, including
 * kind / database / schema and the most recent successful SQL queries.
 * Consumed by AgentPromptBuilder to render {{ACTIVE_CONNECTION_SUMMARY}}.
 */
public interface ActiveConnectionSummaryProvider {
    Optional<ConnectionSummary> summary();

    record ConnectionSummary(
        String connectionId,
        String kind,
        String database,
        String schema,
        List<String> recentSuccessfulQueries
    ) {}
}
