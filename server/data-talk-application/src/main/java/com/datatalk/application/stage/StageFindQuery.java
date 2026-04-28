package com.datatalk.application.stage;

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
        MATCHES,    // Return matching lines grouped by tab
        COUNT,      // Count matching tabs
        CONTENT,    // Search content via FTS
        TABS_ONLY,  // Return tab IDs only (for bulk operations)
        READ        // Read full payload for specific tab IDs
    }

    public record Filter(
        String type,
        String connectionId,
        String objectId,
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
        SearchMode mode,
        Boolean caseInsensitive,
        Boolean multiline
    ) {
        public ContentQuery(String pattern, Boolean includeArchived, Integer limit, SearchMode mode) {
            this(pattern, includeArchived, limit, mode, true, false);
        }

        public enum SearchMode {
            FTS,        // FTS5 trigram coarse match + substring post-filter
            SUBSTRING,  // Pure substring match (no FTS)
            REGEX       // Java regex with virtual-thread fan-out
        }
    }

    public record Read(
        String tabId,
        boolean includePayload,
        ReadRange range,
        int contextLines
    ) {
        public Read {
            if (range == null) {
                range = ReadRange.FULL;
            }
            contextLines = Math.max(0, Math.min(20, contextLines));
        }

        public Read(String tabId, boolean includePayload, ReadRange range) {
            this(tabId, includePayload, range, 0);
        }

        public Read(String tabId, boolean includePayload) {
            this(tabId, includePayload, ReadRange.FULL, 0);
        }
    }

    /**
     * Range specification for read operations.
     * FULL reads the entire content, LINE_RANGE reads a specific line window.
     */
    public sealed interface ReadRange permits ReadRange.Full, ReadRange.LineRange {
        ReadRange FULL = new Full();

        record Full() implements ReadRange {}
        record LineRange(int startLine, int endLine) implements ReadRange {}
    }
}
