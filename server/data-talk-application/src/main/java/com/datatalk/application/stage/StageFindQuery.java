package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTabScope;

import java.util.List;

/**
 * Query model for the ui_find action.
 * Supports metadata listing, content search, and full-read modes.
 */
public record StageFindQuery(
    OutputMode outputMode,
    Filter filter,
    ContentQuery contentQuery,
    List<Read> reads
) {
    public enum OutputMode {
        METADATA,   // List tab metadata (no payload)
        COUNT,      // Count matching tabs
        CONTENT,    // Search content via FTS
        TABS_ONLY,  // Return tab IDs only (for bulk operations)
        READ        // Read full payload for specific tab IDs
    }

    public record Filter(
        StageTabScope scope,
        String type,
        String connectionId,
        String originSessionId,
        Boolean includeArchived,
        Boolean pinned,
        Long lastTouchedAfter,
        Long lastTouchedBefore,
        Integer limit
    ) {}

    public record ContentQuery(
        String pattern,
        Boolean includeArchived,
        Integer limit,
        SearchMode mode
    ) {
        public enum SearchMode {
            FTS,        // FTS5 trigram coarse match + substring post-filter
            SUBSTRING,  // Pure substring match (no FTS)
            REGEX       // Java regex with virtual-thread fan-out
        }
    }

    public record Read(
        String tabId,
        boolean includePayload
    ) {}
}
