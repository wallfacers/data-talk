package com.datatalk.application.persistence;

import com.datatalk.domain.undo.UndoLogEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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

    // ── Inner records for paginated query ──────────────────────────────────

    public record OpLogFilters(
        List<String> status,
        List<String> operation,
        String tableName,
        Long from,
        Long to,
        String q
    ) {}

    public record OpLogListItem(
        String id,
        String sessionId,
        String sessionTitle,
        String connectionId,
        String databaseName,
        String schemaName,
        String tableName,
        String operation,
        int affectedRows,
        boolean undoable,
        String status,
        long expiresAt,
        long createdAt,
        Long undoneAt
    ) {}

    public record PaginatedOpLog(
        List<OpLogListItem> items,
        int total,
        int page,
        int size
    ) {}

    // ── Row mapper for list items (no heavy columns) ───────────────────────

    private static final RowMapper<OpLogListItem> LIST_ITEM_MAPPER = (rs, rowNum) -> new OpLogListItem(
        rs.getString("id"),
        rs.getString("session_id"),
        rs.getString("session_title"),
        rs.getString("connection_id"),
        rs.getString("database_name"),
        rs.getString("schema_name"),
        rs.getString("table_name"),
        rs.getString("operation"),
        rs.getInt("affected_rows"),
        rs.getInt("undoable") == 1,
        rs.getString("status"),
        toLong(rs.getObject("expires_at")),
        toLong(rs.getObject("created_at")),
        toNullableLong(rs.getObject("undone_at"))
    );

    // ── findByConnectionId: paginated + filtered ───────────────────────────

    public PaginatedOpLog findByConnectionId(String connectionId, int page, int size, OpLogFilters filters) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE ul.connection_id = ?");
        params.add(connectionId);

        if (filters.status() != null && !filters.status().isEmpty()) {
            String placeholders = String.join(",", filters.status().stream().map(s -> "?").toList());
            where.append(" AND ul.status IN (").append(placeholders).append(")");
            params.addAll(filters.status());
        }
        if (filters.operation() != null && !filters.operation().isEmpty()) {
            String placeholders = String.join(",", filters.operation().stream().map(s -> "?").toList());
            where.append(" AND ul.operation IN (").append(placeholders).append(")");
            params.addAll(filters.operation());
        }
        if (filters.tableName() != null && !filters.tableName().isBlank()) {
            where.append(" AND ul.table_name LIKE ?");
            params.add("%" + filters.tableName() + "%");
        }
        if (filters.from() != null) {
            where.append(" AND ul.created_at >= ?");
            params.add(filters.from());
        }
        if (filters.to() != null) {
            where.append(" AND ul.created_at <= ?");
            params.add(filters.to());
        }
        if (filters.q() != null && !filters.q().isBlank()) {
            where.append(" AND ul.original_sql LIKE ?");
            params.add("%" + filters.q() + "%");
        }

        // Count query
        String countSql = "SELECT COUNT(*) FROM undo_log ul " + where;
        int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        // Data query with LEFT JOIN sessions for session title
        String dataSql = "SELECT ul.id, ul.session_id, s.title AS session_title, ul.connection_id, "
            + "ul.database_name, ul.schema_name, ul.table_name, ul.operation, "
            + "ul.affected_rows, ul.undoable, ul.status, ul.expires_at, ul.created_at, ul.undone_at "
            + "FROM undo_log ul LEFT JOIN sessions s ON ul.session_id = s.id "
            + where + " ORDER BY ul.created_at DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(page * size);

        List<OpLogListItem> items = jdbc.query(dataSql, LIST_ITEM_MAPPER, dataParams.toArray());
        return new PaginatedOpLog(items, total, page, size);
    }

    // ── findByIdAndConnectionId ────────────────────────────────────────────

    public Optional<UndoLogEntry> findByIdAndConnectionId(String undoLogId, String connectionId) {
        return jdbc.query(
                "SELECT * FROM undo_log WHERE id = ? AND connection_id = ?",
                ROW_MAPPER, undoLogId, connectionId)
            .stream().findFirst();
    }

    // ── findAllById ────────────────────────────────────────────────────────

    public List<UndoLogEntry> findAllById(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        return jdbc.query(
            "SELECT * FROM undo_log WHERE id IN (" + placeholders + ")",
            ROW_MAPPER, ids.toArray());
    }
}
