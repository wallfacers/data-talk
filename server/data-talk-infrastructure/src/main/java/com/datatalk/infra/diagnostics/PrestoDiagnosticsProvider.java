package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class PrestoDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public PrestoDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("presto");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN);
    }

    /**
     * Grammar for parsing Presto/Trino EXPLAIN (TYPE LOGICAL) output.
     * Dash-prefixed fragment tree format, indent = 2 spaces per level.
     * Package-private static for reuse by TrinoDiagnosticsProvider.
     */
    static final TextPlanGrammar TRINO_GRAMMAR = new TextPlanGrammar(
        "trino",
        line -> {
            int dash = line.indexOf("- ");
            return dash < 0 ? -1 : dash / 2;
        },
        line -> {
            int dash = line.indexOf("- ");
            if (dash < 0) return null;
            String rest = line.substring(dash + 2);
            int end = -1;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '[' || c == ' ' || c == '(') { end = i; break; }
            }
            return end < 0 ? rest.trim() : rest.substring(0, end);
        },
        line -> {
            if (!line.contains("TableScan")) return Optional.empty();
            int b = line.indexOf('[');
            int e = line.indexOf(']', b);
            if (b < 0 || e < 0) return Optional.empty();
            String inner = line.substring(b + 1, e);
            int colon = inner.indexOf(':');
            return Optional.of(colon >= 0 ? inner.substring(colon + 1) : inner);
        },
        line -> Optional.empty()
    );

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        try {
            List<Map<String, Object>> rows = queryForList(withDatabaseOverride(conn, database), decryptedPassword,
                "EXPLAIN (TYPE LOGICAL) " + sql);
            StringBuilder raw = new StringBuilder();
            for (var row : rows) {
                for (Object v : row.values()) {
                    if (v != null) raw.append(v).append('\n');
                }
            }
            List<ExplainNode> nodes = applyTrinoScanTypes(mapTextPlanToNodes(raw.toString(), TRINO_GRAMMAR));
            List<String> warnings = List.of(translator.get("diagnostics.warning.federated_connector_pushdown"));
            return DiagnosticResult.ok(new ExplainPlan("presto", raw.toString(), nodes, null, warnings));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "presto");
        }
    }

    /**
     * Applies scan type classification to parsed nodes.
     * TableScan -> FULL_SCAN, everything else -> OTHER.
     * Package-private static for reuse by TrinoDiagnosticsProvider.
     */
    static List<ExplainNode> applyTrinoScanTypes(List<ExplainNode> nodes) {
        List<ExplainNode> out = new ArrayList<>();
        for (ExplainNode n : nodes) {
            ScanType st = "TableScan".equals(n.operation()) ? ScanType.FULL_SCAN : ScanType.OTHER;
            out.add(new ExplainNode(n.operation(), n.table(), st, n.rows(), n.cost(), n.extra(),
                applyTrinoScanTypes(n.children())));
        }
        return out;
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.presto"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "presto"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "presto"));
    }
}