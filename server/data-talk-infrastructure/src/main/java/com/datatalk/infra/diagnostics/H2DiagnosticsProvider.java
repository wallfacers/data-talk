package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class H2DiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final int MAX_TABLES = 200;
    private static final Pattern TABLE_SCAN_PATTERN =
        Pattern.compile("FROM\\s+(\\w+\\.\\w+|\\w+)\\s*/\\*\\s*(\\w+)\\.tableScan");
    private static final Pattern INDEX_PATTERN =
        Pattern.compile("/\\*\\s*(\\w+\\.\\w+|\\w+):");

    public H2DiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("h2");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS, DiagnosticCapability.TABLE_SPACE);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        ConnectionRecord effectiveConn = withDatabaseOverride(conn, database);
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(effectiveConn), effectiveConn.username(), decryptedPassword);
             var stmt = connection.createStatement()) {
            applySchema(connection, schema);
            try (var rs = stmt.executeQuery("EXPLAIN " + sql)) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append("\n");
                }
                String raw = sb.toString().trim();

                List<ExplainNode> nodes = parseH2Text(raw);
                List<String> warnings = collectWarnings(nodes);

                return DiagnosticResult.ok(new ExplainPlan("h2", raw, nodes, null, warnings));
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
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode node : plan.nodes()) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                List<String> cols = SqlColumnExtractor.extract(sql, node.table());
                if (!cols.isEmpty()) {
                    recs.add(new IndexRecommendation(
                        node.table(),
                        cols,
                        "BTREE",
                        Impact.MEDIUM,
                        translator.get("diagnostics.recommendation.table_scan", node.table())
                    ));
                }
            }
        }
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock.unsupported.h2"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool.unsupported.h2_embedded"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        boolean filtered = tables != null && !tables.isEmpty();
        StringBuilder sql = new StringBuilder("""
            SELECT table_schema, table_name, row_count_estimate
            FROM information_schema.tables
            WHERE table_schema NOT IN ('INFORMATION_SCHEMA', 'PUBLIC.PG_CATALOG')
            """);
        List<Object> params = new ArrayList<>();
        if (filtered) {
            sql.append(" AND table_name IN (");
            sql.append("?,".repeat(tables.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(tables.stream().map(String::toUpperCase).toList());
        }
        sql.append(" ORDER BY table_schema, table_name LIMIT 200");
        try {
            List<SpaceReport.TableSpaceEntry> entries = queryForList(withDatabaseOverride(conn, database), decryptedPassword, sql.toString(), params.toArray())
                .stream()
                .limit(MAX_TABLES)
                .map(row -> new SpaceReport.TableSpaceEntry(
                    string(row, "TABLE_NAME", "table_name"),
                    string(row, "TABLE_SCHEMA", "table_schema"),
                    longValue(first(row, "ROW_COUNT_ESTIMATE", "row_count_estimate"), 0L),
                    0L,
                    0L,
                    null
                ))
                .toList();
            return DiagnosticResult.ok(new SpaceReport(entries, List.of()));
        } catch (SQLException e) {
            return DiagnosticResult.error("H2_TABLE_SPACE_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.h2"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.h2"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.h2"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.h2"));
    }

    void applySchema(Connection connection, String schema) throws Exception {
        if (schema == null || schema.isBlank()) {
            return;
        }
        try (var stmt = connection.createStatement()) {
            stmt.execute("SET SCHEMA " + schema);
        }
    }

    List<ExplainNode> parseH2Text(String text) {
        List<ExplainNode> nodes = new ArrayList<>();
        Matcher tableScanMatcher = TABLE_SCAN_PATTERN.matcher(text);
        while (tableScanMatcher.find()) {
            String tableName = tableScanMatcher.group(1);
            String alias = tableScanMatcher.group(2);
            nodes.add(new ExplainNode("TABLE_SCAN", tableName, ScanType.FULL_SCAN, 0, null, "alias=" + alias, List.of()));
        }
        if (nodes.isEmpty()) {
            Matcher indexMatcher = INDEX_PATTERN.matcher(text);
            while (indexMatcher.find()) {
                String tableName = indexMatcher.group(1);
                nodes.add(new ExplainNode("INDEX_SCAN", tableName, ScanType.INDEX_SCAN, 0, null, null, List.of()));
            }
        }
        return nodes;
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                warnings.add(translator.get("diagnostics.warning.table_scan", node.table()));
            }
        }
        return warnings;
    }

    private static Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) return row.get(key);
        }
        return null;
    }

    private static String string(Map<String, Object> row, String... keys) {
        Object value = first(row, keys);
        return value == null ? null : String.valueOf(value);
    }

    private static long longValue(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        String s = String.valueOf(value);
        if (s.isBlank()) return defaultValue;
        return Long.parseLong(s);
    }
}
