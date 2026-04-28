package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class H2DiagnosticsProvider implements DiagnosticsProvider {

    private final Translator translator;

    public H2DiagnosticsProvider(Translator translator) {
        this.translator = translator;
    }

    private static final Pattern TABLE_SCAN_PATTERN =
        Pattern.compile("FROM\\s+(\\w+\\.\\w+|\\w+)\\s*/\\*\\s*(\\w+)\\.tableScan");
    private static final Pattern INDEX_PATTERN =
        Pattern.compile("/\\*\\s*(\\w+\\.\\w+|\\w+):");

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("h2");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        ConnectionRecord effectiveConn = withDatabaseOverride(conn, database);
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(effectiveConn), effectiveConn.username(), decryptedPassword);
             var stmt = connection.createStatement();
             ) {
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
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "H2"));
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "H2"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "H2"));
    }

    // -- internal parsing --

    ConnectionRecord withDatabaseOverride(ConnectionRecord conn, String database) {
        if (database == null || database.isBlank()) {
            return conn;
        }
        return new ConnectionRecord(
            conn.id(),
            conn.name(),
            conn.kind(),
            conn.host(),
            conn.port(),
            database,
            conn.username(),
            conn.passwordEnc(),
            conn.schemaDigest(),
            conn.createdAt(),
            conn.connectTimeout(),
            conn.lastTestStatus(),
            conn.lastTestAt()
        );
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

        // First look for table scan matches
        Matcher tableScanMatcher = TABLE_SCAN_PATTERN.matcher(text);
        while (tableScanMatcher.find()) {
            String tableName = tableScanMatcher.group(1);
            String alias = tableScanMatcher.group(2);
            nodes.add(new ExplainNode(
                "TABLE_SCAN",
                tableName,
                ScanType.FULL_SCAN,
                0,
                null,
                "alias=" + alias,
                List.of()
            ));
        }

        // If no table scans found, look for index scans
        if (nodes.isEmpty()) {
            Matcher indexMatcher = INDEX_PATTERN.matcher(text);
            while (indexMatcher.find()) {
                String tableName = indexMatcher.group(1);
                nodes.add(new ExplainNode(
                    "INDEX_SCAN",
                    tableName,
                    ScanType.INDEX_SCAN,
                    0,
                    null,
                    null,
                    List.of()
                ));
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
}
