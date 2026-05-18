package com.datatalk.infra.history;

import com.datatalk.application.history.SqlExecutionHistoryProvider;
import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.history.SqlExecutionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Repository
public class SqlExecutionHistoryJdbcRepository
        implements SqlExecutionHistoryService, SqlExecutionHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionHistoryJdbcRepository.class);
    private static final int SQL_TEXT_MAX_BYTES = 4 * 1024;
    private static final int ERROR_MESSAGE_MAX_BYTES = 1024;
    private static final int PER_SESSION_RETAIN = 100;

    private final JdbcTemplate jdbc;
    private final ExecutorService trimExecutor;

    public SqlExecutionHistoryJdbcRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.trimExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("sql-history-trim-", 0).factory()
        );
    }

    private static final RowMapper<SqlExecutionRecord> MAPPER = (rs, i) -> {
        String status = rs.getString("status");
        return new SqlExecutionRecord(
            rs.getString("session_id"),
            rs.getString("connection_id"),
            rs.getString("database_name"),
            rs.getString("schema_name"),
            rs.getString("sql_text"),
            "success".equals(status) ? SqlExecutionRecord.Status.SUCCESS
                                     : SqlExecutionRecord.Status.FAILURE,
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getLong("executed_at"),
            rs.getObject("duration_ms") instanceof Number n ? n.longValue() : null,
            rs.getObject("row_count") instanceof Number n ? n.intValue() : null
        );
    };

    @Override
    public void record(SqlExecutionRecord record) {
        String sqlText = truncate(record.sqlText(), SQL_TEXT_MAX_BYTES);
        String errorMessage = truncate(record.errorMessage(), ERROR_MESSAGE_MAX_BYTES);
        String status = record.status() == SqlExecutionRecord.Status.SUCCESS ? "success" : "failure";
        jdbc.update("""
            INSERT INTO sql_execution_history(
              session_id, connection_id, database_name, schema_name, sql_text, status,
              error_code, error_message, executed_at, duration_ms, row_count
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            record.sessionId(),
            record.connectionId(),
            record.databaseName(),
            record.schemaName(),
            sqlText,
            status,
            record.errorCode(),
            errorMessage,
            record.executedAt(),
            record.durationMs(),
            record.rowCount()
        );
        trimExecutor.submit(() -> trimSession(record.sessionId()));
    }

    @Override
    public List<SqlExecutionRecord> list(SqlExecutionHistoryQuery query) {
        if (query.sessionId() == null || query.sessionId().isBlank()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM sql_execution_history WHERE session_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(query.sessionId());
        switch (query.statusFilter()) {
            case SUCCESS -> { sql.append(" AND status = ?"); args.add("success"); }
            case FAILURE -> { sql.append(" AND status = ?"); args.add("failure"); }
            case ALL -> { /* no filter */ }
        }
        if (query.connectionId() != null && !query.connectionId().isBlank()) {
            sql.append(" AND connection_id = ?");
            args.add(query.connectionId());
        }
        if (query.databaseName() != null && !query.databaseName().isBlank()) {
            sql.append(" AND database_name = ?");
            args.add(query.databaseName());
        }
        int limit = Math.max(1, Math.min(query.limit(), 50));
        sql.append(" ORDER BY executed_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public List<SqlExecutionRecord> recentFailures(String sessionId, int limit) {
        return list(new SqlExecutionHistoryQuery(
            sessionId, null, null, SqlExecutionHistoryQuery.StatusFilter.FAILURE, limit
        ));
    }

    @Override
    public List<SqlExecutionRecord> recentSuccesses(String sessionId, int limit) {
        return list(new SqlExecutionHistoryQuery(
            sessionId, null, null, SqlExecutionHistoryQuery.StatusFilter.SUCCESS, limit
        ));
    }

    public int countBySession(String sessionId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sql_execution_history WHERE session_id = ?",
            Integer.class, sessionId
        );
        return n == null ? 0 : n;
    }

    private void trimSession(String sessionId) {
        try {
            jdbc.update("""
                DELETE FROM sql_execution_history
                 WHERE session_id = ?
                   AND id NOT IN (
                     SELECT id FROM sql_execution_history
                      WHERE session_id = ?
                      ORDER BY executed_at DESC, id DESC
                      LIMIT ?
                   )
                """, sessionId, sessionId, PER_SESSION_RETAIN);
        } catch (Exception e) {
            log.warn("sql_execution_history trim failed for session={}: {}", sessionId, e.toString());
        }
    }

    private static String truncate(String value, int maxBytes) {
        if (value == null) return null;
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        int safeChars = Math.max(0, maxBytes - 3);
        byte[] head = new byte[safeChars];
        System.arraycopy(bytes, 0, head, 0, safeChars);
        return new String(head, java.nio.charset.StandardCharsets.UTF_8)
            .replaceAll("\\p{C}+$", "") + "...";
    }

    @SuppressWarnings("unused")
    private static List<SqlExecutionRecord> immutable(List<SqlExecutionRecord> in) {
        return Collections.unmodifiableList(in);
    }
}
