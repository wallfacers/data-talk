package com.datatalk.application.sql;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.domain.undo.UndoCapture;
import com.datatalk.domain.undo.UndoLogEntry;
import com.datatalk.domain.undo.UndoOutcome;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class UndoLogCapture {

    private static final int MAX_UNDO_ROWS = 100;

    private final UndoLogRepository undoLogRepo;

    public UndoLogCapture(UndoLogRepository undoLogRepo) {
        this.undoLogRepo = undoLogRepo;
    }

    public UndoOutcome capture(
        Connection userConn,
        String dmlSql,
        String sessionId,
        String connectionId,
        String database,
        String schema
    ) {
        SqlNode node;
        try {
            node = SqlParser.create(dmlSql).parseStmt();
        } catch (SqlParseException e) {
            return new UndoOutcome.NotUndoable("parse_failed");
        }

        if (!(node instanceof SqlInsert) && !(node instanceof SqlUpdate) && !(node instanceof SqlDelete)) {
            return new UndoOutcome.Skipped("non_dml");
        }

        String tableName = extractTableName(node);
        if (tableName == null) {
            return new UndoOutcome.NotUndoable("table_not_found");
        }

        String operation = extractOperation(node);

        Set<String> pkColumns;
        try {
            pkColumns = detectPrimaryKeys(userConn, schema, tableName);
        } catch (SQLException e) {
            return new UndoOutcome.NotUndoable("pk_detection_failed");
        }

        if (pkColumns.isEmpty()) {
            UndoLogEntry entry = buildEntry(
                sessionId, connectionId, database, schema, tableName,
                operation, dmlSql, null, null, 0, false
            );
            undoLogRepo.insert(entry);
            return new UndoOutcome.NotUndoable("no_primary_key");
        }

        if (node instanceof SqlInsert) {
            UndoLogEntry entry = buildEntry(
                sessionId, connectionId, database, schema, tableName,
                operation, dmlSql, null, null, 0, true
            );
            undoLogRepo.insert(entry);
            return new UndoOutcome.Captured(new UndoCapture(
                true, entry.id(), null, null, tableName, operation, 0
            ));
        }

        String whereSql;
        try {
            whereSql = buildBeforeStateSelectSql(node, tableName);
        } catch (Exception e) {
            return new UndoOutcome.NotUndoable("where_extraction_failed");
        }

        int count;
        try (Statement stmt = userConn.createStatement()) {
            stmt.setQueryTimeout(5);
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + quoteId(tableName) + " WHERE " + whereSql)) {
                rs.next();
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            return new UndoOutcome.NotUndoable("count_query_failed");
        }

        if (count > MAX_UNDO_ROWS) {
            UndoLogEntry entry = buildEntry(
                sessionId, connectionId, database, schema, tableName,
                operation, dmlSql, null, null, count, false
            );
            undoLogRepo.insert(entry);
            return new UndoOutcome.NotUndoable("too_many_rows:" + count);
        }

        List<Map<String, Object>> beforeState;
        try (Statement stmt = userConn.createStatement()) {
            stmt.setQueryTimeout(5);
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + quoteId(tableName) + " WHERE " + whereSql)) {
                beforeState = readResultSet(rs);
            }
        } catch (SQLException e) {
            return new UndoOutcome.NotUndoable("before_state_failed");
        }

        String inverseSql = InverseSqlGenerator.generate(operation, tableName, pkColumns, beforeState, null);
        UndoLogEntry entry = buildEntry(
            sessionId, connectionId, database, schema, tableName,
            operation, dmlSql, inverseSql, toJson(beforeState), count, true
        );
        undoLogRepo.insert(entry);

        return new UndoOutcome.Captured(new UndoCapture(
            true, entry.id(), beforeState, inverseSql, tableName, operation, count
        ));
    }

    public void completeInsertCapture(String undoLogId, int affectedRows, List<Map<String, Object>> generatedKeys) {
        undoLogRepo.findById(undoLogId).ifPresent(entry -> {
            String inverseSql = InverseSqlGenerator.generate("INSERT", entry.tableName(), Set.of("id"), null, generatedKeys);
            UndoLogEntry updated = new UndoLogEntry(
                entry.id(), entry.sessionId(), entry.connectionId(), entry.databaseName(),
                entry.schemaName(), entry.tableName(), entry.operation(), entry.originalSql(),
                inverseSql, null, affectedRows, true, "pending", entry.expiresAt(), entry.createdAt(), null
            );
            undoLogRepo.deletePending(undoLogId);
            undoLogRepo.insert(updated);
        });
    }

    public void activateCapture(String undoLogId) {
        undoLogRepo.activate(undoLogId);
    }

    public void discardPendingCapture(String undoLogId) {
        undoLogRepo.deletePending(undoLogId);
    }

    private String extractTableName(SqlNode node) {
        SqlNode targetNode = switch (node) {
            case SqlInsert insert -> insert.getTargetTable();
            case SqlUpdate update -> update.getTargetTable();
            case SqlDelete delete -> delete.getTargetTable();
            default -> null;
        };
        if (targetNode instanceof SqlIdentifier id && !id.names.isEmpty()) {
            return id.names.get(id.names.size() - 1);
        }
        return null;
    }

    private String extractOperation(SqlNode node) {
        if (node instanceof SqlInsert) return "INSERT";
        if (node instanceof SqlUpdate) return "UPDATE";
        if (node instanceof SqlDelete) return "DELETE";
        return "UNKNOWN";
    }

    private Set<String> detectPrimaryKeys(Connection conn, String schema, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        Set<String> pks = new LinkedHashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, schema, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    private String buildBeforeStateSelectSql(SqlNode node, String tableName) {
        return switch (node) {
            case SqlUpdate update when update.getCondition() != null -> update.getCondition().toString();
            case SqlDelete delete when delete.getCondition() != null -> delete.getCondition().toString();
            default -> "1=1";
        };
    }

    private List<Map<String, Object>> readResultSet(ResultSet rs) throws SQLException {
        int colCount = rs.getMetaData().getColumnCount();
        List<String> colNames = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            colNames.add(rs.getMetaData().getColumnLabel(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < colCount; i++) {
                row.put(colNames.get(i), rs.getObject(i + 1));
            }
            rows.add(row);
        }
        return rows;
    }

    private UndoLogEntry buildEntry(
        String sessionId, String connectionId, String database, String schema,
        String tableName, String operation, String originalSql,
        String inverseSql, String beforeState, int affectedRows, boolean undoable
    ) {
        long now = System.currentTimeMillis();
        long expiresAt = now + 3L * 24 * 60 * 60 * 1000;
        return new UndoLogEntry(
            UUID.randomUUID().toString(), sessionId, connectionId, database, schema,
            tableName, operation, originalSql, inverseSql, beforeState,
            affectedRows, undoable, "pending", expiresAt, now, null
        );
    }

    private String toJson(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(data.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String quoteId(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
