package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PostgreSqlDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final int MAX_TABLES = 200;
    private static final int SQL_PREVIEW_LIMIT = 200;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgreSqlDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("postgresql", "postgres");
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
             var stmt = connection.createStatement()) {
            applySchema(connection, schema);
            try (var rs = stmt.executeQuery("EXPLAIN (FORMAT JSON, ANALYZE false) " + sql)) {
                if (!rs.next()) {
                    return DiagnosticResult.ok(new ExplainPlan("postgresql", "", List.of(), null, List.of()));
                }
                String raw = rs.getString(1);
                JsonNode root = objectMapper.readTree(raw);
                JsonNode planRoot = root.isArray() ? root.get(0) : root;
                JsonNode planNode = planRoot.path("Plan");

                ExplainNode topNode = parsePlanNode(planNode);
                List<ExplainNode> nodes = topNode != null ? List.of(topNode) : List.of();
                List<String> warnings = collectWarnings(nodes);
                Double totalCost = planNode.has("Total Cost") ? planNode.get("Total Cost").asDouble() : null;
                return DiagnosticResult.ok(new ExplainPlan("postgresql", raw, nodes, totalCost, warnings));
            }
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
              blocking.pid AS holder_pid,
              blocked.pid AS waiter_pid,
              blocking.query AS holder_sql,
              blocked.query AS waiter_sql,
              blocked_locks.relation::regclass::text AS table_name,
              blocked_locks.mode AS lock_type,
              EXTRACT(EPOCH FROM (now() - blocked.state_change)) * 1000 AS wait_ms
            FROM pg_catalog.pg_locks blocked_locks
            JOIN pg_catalog.pg_stat_activity blocked
              ON blocked_locks.pid = blocked.pid
            JOIN pg_catalog.pg_stat_activity blocking
              ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
            JOIN pg_catalog.pg_locks blocking_locks
              ON blocking_locks.pid = blocking.pid
            WHERE NOT blocked_locks.granted
            """;
        try {
            List<LockReport.LockEntry> chain = queryForList(withDatabaseOverride(conn, database), decryptedPassword, sql)
                .stream()
                .map(row -> new LockReport.LockEntry(
                    truncate(string(row, "table_name"), 64),
                    normalizeLockType(string(row, "lock_type")),
                    nullableString(row.get("holder_pid")),
                    nullableString(row.get("waiter_pid")),
                    nullableLong(row.get("wait_ms")),
                    truncate(nullableString(row.get("holder_sql")), SQL_PREVIEW_LIMIT),
                    truncate(nullableString(row.get("waiter_sql")), SQL_PREVIEW_LIMIT)
                ))
                .toList();
            return DiagnosticResult.ok(new LockReport(chain, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "LOCK");
        }
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        try {
            Map<String, Object> pool = firstRow(queryForList(conn, decryptedPassword, """
                SELECT
                  count(*) FILTER (WHERE state='active') AS active,
                  count(*) FILTER (WHERE state='idle') AS idle,
                  count(*) FILTER (WHERE wait_event IS NOT NULL AND wait_event_type='Lock') AS waiting
                FROM pg_stat_activity
                WHERE backend_type='client backend'
                """));
            int max = intValue(firstValue(queryForList(conn, decryptedPassword, "SHOW max_connections")), 0);
            int active = intValue(pool.get("active"), 0);
            return DiagnosticResult.ok(new PoolReport(
                "server",
                active,
                intValue(pool.get("idle"), 0),
                max,
                active,
                intValue(pool.get("waiting"), 0),
                conn.host() + ":" + conn.port(),
                List.of()
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "POOL");
        }
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        boolean filtered = tables != null && !tables.isEmpty();
        StringBuilder sql = new StringBuilder("""
            SELECT
              n.nspname AS schema_name,
              c.relname AS table_name,
              COALESCE(s.n_live_tup, 0) AS row_count,
              pg_relation_size(c.oid) AS data_size,
              pg_indexes_size(c.oid) AS index_size,
              GREATEST(pg_total_relation_size(c.oid) - pg_relation_size(c.oid) - pg_indexes_size(c.oid), 0) AS free_size
            FROM pg_class c
            JOIN pg_namespace n ON c.relnamespace = n.oid
            LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
            WHERE c.relkind='r'
              AND n.nspname NOT IN ('pg_catalog','information_schema')
            """);
        List<Object> params = new ArrayList<>();
        if (filtered) {
            sql.append(" AND c.relname IN (");
            sql.append("?,".repeat(tables.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(tables);
        }
        sql.append(" ORDER BY pg_total_relation_size(c.oid) DESC LIMIT 200");
        try {
            List<SpaceReport.TableSpaceEntry> entries = queryForList(withDatabaseOverride(conn, database), decryptedPassword, sql.toString(), params.toArray())
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
                "SELECT pg_backend_pid() AS pid")));
            if (targetSessionId.equals(self)) {
                return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.self"));
            }
            Object currentSql = value(queryForList(withDatabaseOverride(conn, database), decryptedPassword, """
                SELECT query AS current_sql
                FROM pg_stat_activity
                WHERE pid = ?
                """, targetSessionId), "current_sql");
            return DiagnosticResult.ok(new TerminateSessionPreview(
                "postgresql",
                targetSessionId,
                "SELECT pg_terminate_backend(" + targetSessionId + ")",
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
            boolean terminated = booleanValue(value(queryForList(withDatabaseOverride(conn, database), decryptedPassword,
                "SELECT pg_terminate_backend(?) AS terminated", targetSessionId), "terminated"));
            return DiagnosticResult.ok(new TerminateSessionResult(
                terminated,
                targetSessionId,
                terminated ? "Session terminated" : translator.get("diagnostics.terminate.session_not_found")
            ));
        } catch (SQLException e) {
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
        String schema = firstNonBlank(schemaName, "public");
        String relation = quote(schema) + "." + quote(table);
        String willRunSql = "VACUUM (FULL, VERBOSE) " + relation;
        try {
            Long currentTotalSize = pgTotalSize(conn, decryptedPassword, database, relation);
            return DiagnosticResult.ok(new OptimizeTablePreview(
                "postgresql",
                table,
                schema,
                willRunSql,
                null,
                currentTotalSize,
                List.of(optimizeWarning("diagnostics.optimize.preview.lock_warning_pg", willRunSql))
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
        String schema = firstNonBlank(schemaName, "public");
        String relation = quote(schema) + "." + quote(table);
        String sql = "VACUUM (FULL, VERBOSE) " + relation;
        try {
            Long before = pgTotalSize(conn, decryptedPassword, database, relation);
            long start = System.currentTimeMillis();
            executeStatementAutoCommit(withDatabaseOverride(conn, database), decryptedPassword, sql);
            long duration = Math.max(System.currentTimeMillis() - start, 0L);
            Long after = pgTotalSize(conn, decryptedPassword, database, relation);
            Long reclaimed = before != null && after != null ? Math.max(before - after, 0L) : null;
            return DiagnosticResult.ok(new OptimizeTableResult(
                true,
                table,
                schema,
                duration,
                reclaimed,
                "VACUUM FULL completed"
            ));
        } catch (SQLException e) {
            return mapPermissionOrError(e, "OPTIMIZE");
        }
    }

    void applySchema(Connection connection, String schema) throws Exception {
        if (schema == null || schema.isBlank()) {
            return;
        }
        try (var stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + schema);
        }
    }

    ScanType mapNodeType(String nodeType) {
        if (nodeType == null) return ScanType.OTHER;
        return switch (nodeType) {
            case "Seq Scan" -> ScanType.FULL_SCAN;
            case "Index Scan", "Index Only Scan" -> ScanType.INDEX_SCAN;
            case "Bitmap Index Scan", "Bitmap Heap Scan" -> ScanType.INDEX_RANGE;
            default -> ScanType.OTHER;
        };
    }

    private ExplainNode parsePlanNode(JsonNode plan) {
        if (plan == null || plan.isMissingNode() || plan.isNull()) {
            return null;
        }

        String nodeType = plan.path("Node Type").asText("");
        String relationName = plan.path("Relation Name").asText(plan.path("Alias").asText(""));
        long rows = plan.path("Plan Rows").asLong(0);
        Double cost = plan.has("Total Cost") ? plan.get("Total Cost").asDouble() : null;
        String indexName = plan.path("Index Name").asText(null);
        String extra = indexName != null ? "index=" + indexName : null;

        List<ExplainNode> children = new ArrayList<>();
        JsonNode plans = plan.path("Plans");
        if (plans.isArray()) {
            for (JsonNode child : plans) {
                ExplainNode childNode = parsePlanNode(child);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
        }

        return new ExplainNode(nodeType, relationName, mapNodeType(nodeType), rows, cost, extra, children);
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                warnings.add(translator.get("diagnostics.warning.sequential_scan", node.table()));
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
                        translator.get("diagnostics.recommendation.sequential_scan", node.table(), String.valueOf(node.rows()))
                    ));
                }
            }
            recs.addAll(collectRecommendations(node.children(), sql));
        }
        return recs;
    }

    private Long pgTotalSize(ConnectionRecord conn, String decryptedPassword, String database, String relation) throws SQLException {
        return nullableLong(value(queryForList(withDatabaseOverride(conn, database), decryptedPassword,
            "SELECT pg_total_relation_size(?::regclass) AS total_size", relation), "total_size"));
    }

    private DiagnosticRecommendation optimizeWarning(String key, String sql) {
        return new DiagnosticRecommendation("critical", translator.get(key), null, null, Map.of(), sql);
    }

    private <T> DiagnosticResult<T> mapPermissionOrError(SQLException e, String capability) {
        if ("42501".equals(e.getSQLState())) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.error.permission_denied"));
        }
        return DiagnosticResult.error("POSTGRES_" + capability + "_ERROR", e.getMessage());
    }

    private static Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.get(0);
    }

    private static Object value(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0).get(key);
    }

    private static Object firstValue(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) return null;
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

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static String normalizeLockType(String lockType) {
        if (lockType == null || lockType.isBlank()) return "OTHER";
        String normalized = lockType.toUpperCase();
        if (normalized.contains("ACCESS EXCLUSIVE")) return "EXCLUSIVE";
        if (normalized.contains("EXCLUSIVE")) return "EXCLUSIVE";
        if (normalized.contains("SHARE")) return "SHARED";
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
