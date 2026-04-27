package com.datatalk.infra.stage;

import com.datatalk.application.stage.StageTabIndexerPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FTS5 query builder for stage tab full-text search.
 * Uses the trigram tokenizer for substring matching.
 */
@Component
@Primary
public class StageTabIndexer implements StageTabIndexerPort {

    private final JdbcTemplate jdbc;

    private static final RowMapper<RowidScore> ROWID_SCORE_MAPPER = (rs, i) -> new RowidScore(
        rs.getLong("rowid"),
        rs.getDouble("score")
    );

    private static final RowMapper<String> ID_MAPPER = (rs, i) -> rs.getString("id");

    public StageTabIndexer(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RowidScore> ftsMatch(String pattern, boolean includeArchived, int limit) {
        String sanitized = sanitizePattern(pattern);
        String query;
        Object[] params;

        if (includeArchived) {
            query = "SELECT rowid, bm25(stage_tab_index) AS score " +
                    "FROM stage_tab_index WHERE stage_tab_index MATCH ? " +
                    "ORDER BY bm25(stage_tab_index) LIMIT ?";
            params = new Object[]{sanitized, limit};
        } else {
            query = "SELECT rowid, bm25(stage_tab_index) AS score " +
                    "FROM stage_tab_index WHERE stage_tab_index MATCH ? AND archived = 0 " +
                    "ORDER BY bm25(stage_tab_index) LIMIT ?";
            params = new Object[]{sanitized, limit};
        }

        return jdbc.query(query, ROWID_SCORE_MAPPER, params);
    }

    @Override
    public List<String> rowidsToIds(List<Long> rowids) {
        if (rowids == null || rowids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", rowids.stream().map(r -> "?").toList());
        return jdbc.query(
            "SELECT id FROM stage_tab_payload p JOIN stage_tabs t ON t.id = p.tab_id" +
            " WHERE p.rowid IN (" + placeholders + ")",
            ID_MAPPER, rowids.toArray());
    }

    /**
     * Sanitize a user-provided pattern for FTS5 trigram tokenizer.
     * Escapes double quotes to prevent injection.
     */
    static String sanitizePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "\"\"";
        }
        // Escape double quotes
        String sanitized = pattern.replace("\"", "\"\"");
        // Wrap in double quotes for exact trigram matching
        return "\"" + sanitized + "\"";
    }
}
