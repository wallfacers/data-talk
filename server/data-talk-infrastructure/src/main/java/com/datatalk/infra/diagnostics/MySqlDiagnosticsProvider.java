package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MySqlDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final int MAX_TABLES = 200;
    private static final int SQL_PREVIEW_LIMIT = 200;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MySqlDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("mysql");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(
            DiagnosticCapability.EXPLAIN,
            DiagnosticCapability.INDEX_HINTS,
            DiagnosticCapability.LOCK_INFO,
            DiagnosticCapability.POOL_STATUS,
            DiagnosticCapability.TABLE_SPACE,
            DiagnosticCapability.TERMINATE_SESSION,
            DiagnosticCapability.OPTIMIZE_TABLE
        );
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        ConnectionRecord effectiveConn = withDatabaseOverride(conn, database);
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(effectiveConn), effectiveConn.username(), decryptedPassword);
             var stmt = connection.createStatement();
             var rs = stmt.executeQuery("EXPLAIN FORMAT=JSON " + sql)) {

            if (!rs.next()) {
                return DiagnosticResult.ok(new ExplainPlan("mysql", "", List.of(), null, List.of()));
            }
            String raw = rs.getString(1);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode queryBlock = root.path("query_block");

            List<ExplainNode> nodes = parseQueryBlock(queryBlock);
            List<String> warnings = collectWarnings(nodes);

            return DiagnosticResult.ok(new ExplainPlan("mysql", raw, nodes, null, warnings));
        } catch (Exception e) {
            return DiagnosticResult.error("EXPLAIN_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn, String decryptedPassword) {
        if (plan == null || plan.nodes() == null) {
            return DiagnosticResult.ok(List.of());
        }
        List<IndexRecommendation> recs = collectRecommendations(plan.nodes(), sql);
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        String sql = """
            SELECT
              dlw.requesting_engine_lock_id AS waiter_lock,
              dlw.blocking_engine_lock_id AS holder_lock,
              dl_w.OBJECT_NAME AS waiter_table,
              dl_w.LOCK_TYPE AS waiter_lock_type,
              dl_h.OBJECT_NAME AS holder_table,
              dl_h.LOCK_TYPE AS holder_lock_type,
              t_w.PROCESSLIST_ID AS waiter_thread,
              t_h.PROCESSLIST_ID AS holder_thread,
              t_w.PROCESSLIST_INFO AS waiter_sql,
              t_h.PROCESSLIST_INFO AS holder_sql,
              TIMESTAMPDIFF(MICROSECOND, trx.trx_wait_started, NOW(6)) / 1000 AS wait_ms
            FROM performance_schema.data_lock_waits dlw
            JOIN performance_schema.data_locks dl_w ON dlw.requesting_engine_lock_id = dl_w.ENGINE_LOCK_ID
            JOIN performance_schema.data_locks dl_h ON dlw.blocking_engine_lock_id = dl_h.ENGINE_LOCK_ID
            JOIN performance_schema.threads t_w ON dlw.requesting_thread_id = t_w.THREAD_ID
            JOIN performance_schema.threads t_h ON dlw.blocking_thread_id = t_h.THREAD_ID
            LEFT JOIN information_schema.innodb_trx trx ON trx.trx_mysql_thread_id = t_w.PROCESSLIST_ID
            """;
        try {
            List<LockReport.LockEntry> chain = queryForList(withDatabaseOverride(conn, database), decryptedPassword, sql)
                .stream()
                .map(this::toLockEntry)
                .toList();
            return DiagnosticResult.ok(new LockReport(chain, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "LOCK");
        }
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        try {
            int connected = intValue(firstValue(queryForList(conn, decryptedPassword, "SHOW STATUS LIKE 'Threads_connected'")), 0);
            int running = intValue(firstValue(queryForList(conn, decryptedPassword, "SHOW STATUS LIKE 'Threads_running'")), 0);
            int max = intValue(firstValue(queryForList(conn, decryptedPassword, "SHOW VARIABLES LIKE 'max_connections'")), 0);
            int waiting = intValue(value(queryForList(conn, decryptedPassword, """
                SELECT COUNT(*) AS waiting
                FROM performance_schema.threads
                WHERE PROCESSLIST_STATE LIKE 'Waiting%'
                """), "waiting"), 0);
            return DiagnosticResult.ok(new PoolReport(
                "server",
                connected,
                Math.max(connected - running, 0),
                max,
                running,
                waiting,
                conn.host() + ":" + conn.port(),
                List.of()
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "POOL");
        }
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        String schema = firstNonBlank(database, conn.databaseName());
        if (schema == null) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.space.unsupported.no_database"));
        }
        boolean filtered = tables != null && !tables.isEmpty();
        StringBuilder sql = new StringBuilder("""
            SELECT
              table_schema AS schema_name,
              table_name AS table_name,
              table_rows AS row_count,
              data_length AS data_size,
              index_length AS index_size,
              data_free AS free_size
            FROM information_schema.tables
            WHERE table_schema = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(schema);
        if (filtered) {
            sql.append(" AND table_name IN (");
            sql.append("?,".repeat(tables.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(tables);
        }
        sql.append(" ORDER BY data_length + index_length DESC LIMIT 200");
        try {
            List<SpaceReport.TableSpaceEntry> entries = queryForList(withDatabaseOverride(conn, schema), decryptedPassword, sql.toString(), params.toArray())
                .stream()
                .limit(MAX_TABLES)
                .map(row -> new SpaceReport.TableSpaceEntry(
                    string(row, "table_name"),
                    string(row, "schema_name"),
                    longValue(row.get("row_count"), 0L),
                    longValue(row.get("data_size"), 0L),
                    longValue(row.get("index_size"), 0L),
                    nullableLong(row.get("free_size"))
                ))
                .toList();
            return DiagnosticResult.ok(new SpaceReport(entries, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "TABLE_SPACE");
        }
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(
        ConnectionRecord conn,
        String decryptedPassword,
        String targetSessionId,
        String database
    ) {
        try {
            String self = String.valueOf(firstValue(queryForList(withDatabaseOverride(conn, database), decryptedPassword,
                "SELECT CONNECTION_ID() AS connection_id")));
            if (targetSessionId.equals(self)) {
                return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.self"));
            }
            Object currentSql = value(queryForList(withDatabaseOverride(conn, database), decryptedPassword, """
                SELECT PROCESSLIST_INFO AS current_sql
                FROM performance_schema.threads
                WHERE PROCESSLIST_ID = ?
                """, targetSessionId), "current_sql");
            return DiagnosticResult.ok(new TerminateSessionPreview(
                "mysql",
                targetSessionId,
                "KILL " + targetSessionId,
                currentSql == null ? null : String.valueOf(currentSql)
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "TERMINATE_PREVIEW");
        }
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(
        ConnectionRecord conn,
        String decryptedPassword,
        String targetSessionId,
        String database
    ) {
        try {
            executeUpdate(withDatabaseOverride(conn, database), decryptedPassword, "KILL " + targetSessionId);
            return DiagnosticResult.ok(new TerminateSessionResult(true, targetSessionId, "Session terminated"));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1094) {
                return DiagnosticResult.ok(new TerminateSessionResult(
                    false,
                    targetSessionId,
                    translator.get("diagnostics.terminate.session_not_found")
                ));
            }
            return mapPermissionOrError(e, "TERMINATE");
        }
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(
        ConnectionRecord conn,
        String decryptedPassword,
        String table,
        String schemaName,
        String database
    ) {
        String schema = firstNonBlank(schemaName, database, conn.databaseName());
        if (schema == null) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.no_database"));
        }
        String willRunSql = "OPTIMIZE TABLE " + quote(schema) + "." + quote(table);
        try {
            Long currentDataFree = mysqlDataFree(conn, decryptedPassword, schema, table);
            return DiagnosticResult.ok(new OptimizeTablePreview(
                "mysql",
                table,
                schema,
                willRunSql,
                currentDataFree,
                null,
                List.of(optimizeWarning("diagnostics.optimize.preview.lock_warning_mysql", willRunSql))
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "OPTIMIZE_PREVIEW");
        }
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(
        ConnectionRecord conn,
        String decryptedPassword,
        String table,
        String schemaName,
        String database
    ) {
        String schema = firstNonBlank(schemaName, database, conn.databaseName());
        if (schema == null) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.no_database"));
        }
        String sql = "OPTIMIZE TABLE " + quote(schema) + "." + quote(table);
        try {
            Long before = mysqlDataFree(conn, decryptedPassword, schema, table);
            long start = System.currentTimeMillis();
            executeStatement(withDatabaseOverride(conn, schema), decryptedPassword, sql);
            long duration = Math.max(System.currentTimeMillis() - start, 0L);
            Long after = mysqlDataFree(conn, decryptedPassword, schema, table);
            Long reclaimed = before != null && after != null ? Math.max(before - after, 0L) : null;
            return DiagnosticResult.ok(new OptimizeTableResult(
                true,
                table,
                schema,
                duration,
                reclaimed,
                "OPTIMIZE TABLE completed"
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "OPTIMIZE");
        }
    }

    List<ExplainNode> parseQueryBlock(JsonNode queryBlock) {
        List<ExplainNode> nodes = new ArrayList<>();
        if (queryBlock.has("table")) {
            nodes.add(parseTableNode(queryBlock.path("table")));
        }
        JsonNode nl = queryBlock.path("nested_loop");
        if (nl.isArray()) {
            for (JsonNode item : nl) {
                nodes.addAll(parseQueryBlock(item));
            }
        }
        if (queryBlock.has("ordering_operation")) {
            nodes.addAll(parseQueryBlock(queryBlock.path("ordering_operation")));
        }
        if (queryBlock.has("grouping_operation")) {
            nodes.addAll(parseQueryBlock(queryBlock.path("grouping_operation")));
        }
        return nodes;
    }

    ScanType mapAccessType(String accessType) {
        if (accessType == null) return ScanType.OTHER;
        return switch (accessType.toLowerCase()) {
            case "all" -> ScanType.FULL_SCAN;
            case "range" -> ScanType.INDEX_RANGE;
            case "ref", "eq_ref" -> ScanType.REF;
            case "index" -> ScanType.INDEX_SCAN;
            case "const", "system" -> ScanType.CONST;
            default -> ScanType.OTHER;
        };
    }

    private ExplainNode parseTableNode(JsonNode table) {
        String tableName = table.path("table_name").asText("");
        String accessType = table.path("access_type").asText("");
        long rows = table.path("rows_examined_per_scan").asLong(table.path("rows").asLong(0));
        Double cost = table.has("filtered") ? table.path("filtered").asDouble() : null;
        String key = table.path("key").asText(null);
        String extra = key != null ? "key=" + key : null;

        return new ExplainNode(accessType, tableName, mapAccessType(accessType), rows, cost, extra, List.of());
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                warnings.add(translator.get("diagnostics.warning.full_table_scan", node.table()));
            }
            warnings.addAll(collectWarnings(node.children()));
        }
        return warnings;
    }

    private List<IndexRecommendation> collectRecommendations(List<ExplainNode> nodes, String sql) {
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                List<String> cols = SqlColumnExtractor.extract(sql, node.table());
                if (!cols.isEmpty()) {
                    Impact impact = node.rows() > 1000 ? Impact.HIGH : Impact.MEDIUM;
                    recs.add(new IndexRecommendation(
                        node.table(),
                        cols,
                        "BTREE",
                        impact,
                        translator.get("diagnostics.recommendation.full_scan", node.table(), String.valueOf(node.rows()))
                    ));
                }
            }
            recs.addAll(collectRecommendations(node.children(), sql));
        }
        return recs;
    }

    private LockReport.LockEntry toLockEntry(Map<String, Object> row) {
        return new LockReport.LockEntry(
            truncate(firstNonBlank(string(row, "holder_table"), string(row, "waiter_table")), 64),
            normalizeLockType(string(row, "holder_lock_type")),
            nullableString(row.get("holder_thread")),
            nullableString(row.get("waiter_thread")),
            nullableLong(row.get("wait_ms")),
            truncate(nullableString(row.get("holder_sql")), SQL_PREVIEW_LIMIT),
            truncate(nullableString(row.get("waiter_sql")), SQL_PREVIEW_LIMIT)
        );
    }

    private DiagnosticRecommendation optimizeWarning(String key, String sql) {
        return new DiagnosticRecommendation(
            "critical",
            translator.get(key),
            null,
            null,
            Map.of(),
            sql
        );
    }

    private Long mysqlDataFree(ConnectionRecord conn, String decryptedPassword, String schema, String table) throws SQLException {
        return nullableLong(value(queryForList(withDatabaseOverride(conn, schema), decryptedPassword, """
            SELECT data_free AS data_free
            FROM information_schema.tables
            WHERE table_schema = ? AND table_name = ?
            """, schema, table), "data_free"));
    }

    private <T> DiagnosticResult<T> mapPermissionOrError(SQLException e, String capability) {
        if ("42000".equals(e.getSQLState()) && (e.getErrorCode() == 1142 || e.getErrorCode() == 1227)) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.error.permission_denied"));
        }
        return DiagnosticResult.error("MYSQL_" + capability + "_ERROR", e.getMessage());
    }

    private static Object value(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0).get(key);
    }

    private static Object firstValue(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) return null;
        for (var entry : rows.get(0).entrySet()) {
            String key = entry.getKey();
            if ("Value".equalsIgnoreCase(key) || "Variable_value".equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return rows.get(0).values().iterator().next();
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static long longValue(Object value, long defaultValue) {
        Long parsed = nullableLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    private static Long nullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String s = String.valueOf(value);
        if (s.isBlank()) return null;
        return Long.parseLong(s);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static String normalizeLockType(String lockType) {
        if (lockType == null || lockType.isBlank()) return "OTHER";
        String normalized = lockType.toUpperCase();
        if (normalized.contains("EXCLUSIVE")) return "EXCLUSIVE";
        if (normalized.contains("SHARED")) return "SHARED";
        if (normalized.contains("METADATA")) return "METADATA";
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
