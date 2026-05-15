package com.datatalk.application.persistence;

import com.datatalk.domain.undo.UndoLogEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UndoLogRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<UndoLogEntry> ROW_MAPPER = (rs, rowNum) -> new UndoLogEntry(
        rs.getString("id"),
        rs.getString("session_id"),
        rs.getString("connection_id"),
        rs.getString("database_name"),
        rs.getString("schema_name"),
        rs.getString("table_name"),
        rs.getString("operation"),
        rs.getString("original_sql"),
        rs.getString("inverse_sql"),
        rs.getString("before_state"),
        rs.getInt("affected_rows"),
        rs.getInt("undoable") == 1,
        rs.getString("status"),
        toLong(rs.getObject("expires_at")),
        toLong(rs.getObject("created_at")),
        toNullableLong(rs.getObject("undone_at"))
    );

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s.trim());
        throw new IllegalArgumentException("Cannot convert to long: " + value);
    }

    private static Long toNullableLong(Object value) {
        if (value == null) return null;
        return toLong(value);
    }

    public UndoLogRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UndoLogEntry entry) {
        jdbc.update("""
            INSERT INTO undo_log(id, session_id, connection_id, database_name, schema_name,
                table_name, operation, original_sql, inverse_sql, before_state,
                affected_rows, undoable, status, expires_at, created_at, undone_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            entry.id(), entry.sessionId(), entry.connectionId(), entry.databaseName(),
            entry.schemaName(), entry.tableName(), entry.operation(), entry.originalSql(),
            entry.inverseSql(), entry.beforeState(), entry.affectedRows(), entry.undoable(),
            entry.status(), entry.expiresAt(), entry.createdAt(), entry.undoneAt());
    }

    public void activate(String id) {
        jdbc.update("UPDATE undo_log SET status = 'active' WHERE id = ? AND status = 'pending'", id);
    }

    public void deletePending(String id) {
        jdbc.update("DELETE FROM undo_log WHERE id = ? AND status = 'pending'", id);
    }

    public Optional<UndoLogEntry> findById(String id) {
        return jdbc.query("SELECT * FROM undo_log WHERE id = ?", ROW_MAPPER, id)
            .stream().findFirst();
    }

    public void markUndone(String id, long undoneAt) {
        jdbc.update("UPDATE undo_log SET status = 'undone', undone_at = ? WHERE id = ?", undoneAt, id);
    }

    public List<String> findExpiredActive(long now) {
        return jdbc.queryForList(
            "SELECT id FROM undo_log WHERE status = 'active' AND expires_at < ?",
            String.class, now);
    }

    public void markExpiredBatch(List<String> ids) {
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        jdbc.update("UPDATE undo_log SET status = 'expired' WHERE id IN (" + placeholders + ") AND status = 'active'",
            ids.toArray());
    }

    public void deleteOldExpired(long cutoff) {
        jdbc.update("DELETE FROM undo_log WHERE status = 'expired' AND expires_at < ?", cutoff);
    }
}
