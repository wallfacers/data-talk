package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQL Server diagnostics provider.
 * Day-2: implements EXPLAIN (SHOWPLAN_XML) and INDEX_HINTS.
 * Other capabilities remain unsupported.
 */
@Component
public class SqlServerDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public SqlServerDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("sqlserver");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    /**
     * EXPLAIN implementation for SQL Server using SET SHOWPLAN_XML ON/OFF.
     * Requires self-managed connection lifecycle because SHOWPLAN_XML ON/OFF
     * must execute on the same connection.
     */
    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                  String database, String schema) {
        String rawXml;
        try (Connection c = openConnection(withDatabaseOverride(conn, database), decryptedPassword);
             Statement stmt = c.createStatement()) {
            // Enable showplan mode (returns XML plan without executing the query)
            stmt.execute("SET SHOWPLAN_XML ON");
            try (ResultSet rs = stmt.executeQuery(sql)) {
                rawXml = rs.next() ? rs.getString(1) : "";
            } finally {
                // Always restore normal execution mode
                try {
                    stmt.execute("SET SHOWPLAN_XML OFF");
                } catch (SQLException ignored) {
                    // Best-effort cleanup; don't fail the whole operation
                }
            }
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "sqlserver");
        }

        // Handle empty plan
        if (rawXml == null || rawXml.isBlank()) {
            return DiagnosticResult.ok(new ExplainPlan("sqlserver", "", List.of(), null, List.of()));
        }

        // Parse XML and build explain nodes
        try {
            List<ExplainNode> rawNodes = mapXmlPlanToNodes(rawXml);
            List<ExplainNode> nodes = applyScanTypes(rawNodes);

            // Collect warnings for full table scans
            List<String> warnings = new ArrayList<>();
            for (ExplainNode n : nodes) {
                collectWarnings(n, warnings);
            }

            return DiagnosticResult.ok(new ExplainPlan("sqlserver", rawXml, nodes, null, warnings));
        } catch (Exception parseError) {
            // Return parse error with preview of raw XML for debugging
            String preview = rawXml.length() > 500 ? rawXml.substring(0, 500) : rawXml;
            return DiagnosticResult.error("EXPLAIN_PARSE_ERROR",
                parseError.getMessage() + " | raw[0..500]: " + preview);
        }
    }

    /**
     * INDEX_HINTS implementation: recommends indexes for tables with full scans.
     * Impact tier based on row count: >1000=HIGH, >100=MEDIUM, <=100=LOW.
     */
    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        if (plan == null || plan.nodes() == null) {
            return DiagnosticResult.ok(List.of());
        }

        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode n : plan.nodes()) {
            collectRecommendations(n, sql, recs);
        }
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.coming_soon"));
    }

    // --- Private helpers for scan type mapping and recommendations ---

    private static final Map<String, ScanType> SQLSERVER_SCAN_OVERRIDES = Map.of(
        "table scan", ScanType.FULL_SCAN,
        "clustered index scan", ScanType.INDEX_SCAN,
        "index scan", ScanType.INDEX_SCAN,
        "constant scan", ScanType.CONST
    );

    /**
     * Apply SQL Server-specific scan type mapping to explain nodes.
     * Index Seek is simplified to INDEX_RANGE (Day-2 simplification: no REF distinction).
     */
    private List<ExplainNode> applyScanTypes(List<ExplainNode> nodes) {
        List<ExplainNode> out = new ArrayList<>();
        for (ExplainNode n : nodes) {
            String op = n.operation() == null ? "" : n.operation();
            ScanType st;
            // Index Seek -> INDEX_RANGE (Day-2 simplified, not distinguishing REF)
            if (op.toLowerCase(Locale.ROOT).contains("index seek")) {
                st = ScanType.INDEX_RANGE;
            } else {
                st = parseScanType(op, SQLSERVER_SCAN_OVERRIDES);
            }
            out.add(new ExplainNode(
                op,
                n.table(),
                st,
                n.rows(),
                n.cost(),
                n.extra(),
                applyScanTypes(n.children())
            ));
        }
        return out;
    }

    /**
     * Collect warnings for nodes with full table scans.
     */
    private void collectWarnings(ExplainNode node, List<String> warnings) {
        if (node.scanType() == ScanType.FULL_SCAN && node.table() != null) {
            warnings.add(translator.get("diagnostics.warning.full_table_scan", node.table()));
        }
        for (ExplainNode child : node.children()) {
            collectWarnings(child, warnings);
        }
    }

    /**
     * Collect index recommendations for tables with full scans.
     * Uses SqlColumnExtractor to find columns referenced in the SQL for that table.
     */
    private void collectRecommendations(ExplainNode node, String sql, List<IndexRecommendation> recs) {
        if (node.scanType() == ScanType.FULL_SCAN && node.table() != null) {
            List<String> cols = SqlColumnExtractor.extract(sql, node.table());
            if (!cols.isEmpty()) {
                // Impact tier: rows > 1000 => HIGH, rows > 100 => MEDIUM, else LOW
                Impact impact = node.rows() > 1000 ? Impact.HIGH
                    : node.rows() > 100 ? Impact.MEDIUM : Impact.LOW;
                recs.add(new IndexRecommendation(
                    node.table(),
                    cols,
                    "BTREE",
                    impact,
                    translator.get("diagnostics.recommendation.full_scan",
                        node.table(), String.valueOf(node.rows()))
                ));
            }
        }
        for (ExplainNode child : node.children()) {
            collectRecommendations(child, sql, recs);
        }
    }
}