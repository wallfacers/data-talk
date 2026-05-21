package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QueryResultRepository {

    private final JdbcTemplate jdbc;

    public QueryResultRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String handle, String sessionId, String columnsJson,
                       String rowsNdjson, int rowCount, long createdAt, long ttlAt) {
        jdbc.update("""
            INSERT INTO query_results(handle, session_id, columns_json, rows_ndjson, row_count, created_at, ttl_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, handle, sessionId, columnsJson, rowsNdjson, rowCount, createdAt, ttlAt);
    }
}
