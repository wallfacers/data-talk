package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TiDbDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final TabularLayout LAYOUT = new TabularLayout(
        "id", null, "(\\w+)_\\d+", "estRows", "access object", "operator info"
    );

    private static final Map<String, ScanType> SCAN_OVERRIDES = Map.of(
        "tablefullscan", ScanType.FULL_SCAN,
        "indexfullscan", ScanType.INDEX_SCAN,
        "indexrangescan", ScanType.INDEX_RANGE,
        "indexlookup", ScanType.INDEX_RANGE,
        "tablerowidscan", ScanType.OTHER,
        "pointget", ScanType.CONST,
        "batchpointget", ScanType.CONST
    );

    public TiDbDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("tidb");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                  String database, String schema) {
        try {
            List<Map<String, Object>> rows = queryForList(withDatabaseOverride(conn, database), decryptedPassword, "EXPLAIN " + sql);
            List<ExplainNode> nodesRaw = mapTabularPlanToNodes(rows, LAYOUT);
            List<ExplainNode> nodes = applyScanTypes(nodesRaw);
            List<String> warnings = new ArrayList<>();
            for (ExplainNode n : nodes) collectWarnings(n, warnings);
            return DiagnosticResult.ok(new ExplainPlan("tidb", String.valueOf(rows), nodes, null, warnings));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "tidb");
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        if (plan == null || plan.nodes() == null) return DiagnosticResult.ok(List.of());
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode n : plan.nodes()) collectRecommendations(n, sql, recs);
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "tidb"));
    }

    // --- Private helpers ---

    private List<ExplainNode> applyScanTypes(List<ExplainNode> nodes) {
        List<ExplainNode> out = new ArrayList<>(nodes.size());
        for (ExplainNode n : nodes) {
            ScanType st = parseScanType(n.operation(), SCAN_OVERRIDES);
            out.add(new ExplainNode(
                n.operation(),
                normalizeTable(n.table()),
                st,
                n.rows(),
                n.cost(),
                n.extra(),
                applyScanTypes(n.children())
            ));
        }
        return out;
    }

    private static String normalizeTable(String accessObject) {
        if (accessObject == null || accessObject.isBlank()) return null;
        // Format: "table:t" or "table:t, index:idx"
        int colon = accessObject.indexOf(':');
        if (colon < 0) return accessObject;
        String afterColon = accessObject.substring(colon + 1).trim();
        int comma = afterColon.indexOf(',');
        return comma < 0 ? afterColon : afterColon.substring(0, comma).trim();
    }

    private void collectWarnings(ExplainNode node, List<String> warnings) {
        if (node.scanType() == ScanType.FULL_SCAN && node.table() != null) {
            warnings.add(translator.get("diagnostics.warning.full_table_scan", node.table()));
        }
        for (ExplainNode child : node.children()) collectWarnings(child, warnings);
    }

    private void collectRecommendations(ExplainNode node, String sql, List<IndexRecommendation> recs) {
        if (node.scanType() == ScanType.FULL_SCAN && node.table() != null) {
            List<String> cols = SqlColumnExtractor.extract(sql, node.table());
            if (!cols.isEmpty()) {
                Impact impact = node.rows() > 1000 ? Impact.HIGH
                    : node.rows() > 100 ? Impact.MEDIUM : Impact.LOW;
                recs.add(new IndexRecommendation(
                    node.table(),
                    cols,
                    "BTREE",
                    impact,
                    translator.get("diagnostics.recommendation.full_scan", node.table(), String.valueOf(node.rows()))
                ));
            }
        }
        for (ExplainNode child : node.children()) collectRecommendations(child, sql, recs);
    }
}