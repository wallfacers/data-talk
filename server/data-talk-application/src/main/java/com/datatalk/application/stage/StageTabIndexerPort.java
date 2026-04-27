package com.datatalk.application.stage;

import java.util.List;

/**
 * Application-layer port interface for FTS operations.
 * Implementation lives in the infrastructure layer (StageTabIndexer).
 */
public interface StageTabIndexerPort {

    /**
     * Execute an FTS5 match query with optional BM25 ranking.
     *
     * @param pattern         the search pattern (will be sanitized)
     * @param includeArchived whether to include archived tabs
     * @param limit           maximum results
     * @return list of rowid-score pairs
     */
    List<RowidScore> ftsMatch(String pattern, boolean includeArchived, int limit);

    /**
     * Convert FTS rowids to stage tab IDs.
     */
    List<String> rowidsToIds(List<Long> rowids);

    /**
     * Represents an FTS match result.
     */
    record RowidScore(long rowid, double score) {}
}
