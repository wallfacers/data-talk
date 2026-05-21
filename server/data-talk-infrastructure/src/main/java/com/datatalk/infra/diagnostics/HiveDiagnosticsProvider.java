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
import java.util.regex.Pattern;

@Component
public class HiveDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final TextPlanGrammar HIVE_GRAMMAR = new TextPlanGrammar(
        "hive",
        line -> {
            if (line.isBlank()) return -1;
            String t = line.stripLeading();
            if (t.startsWith("STAGE DEPENDENCIES") || t.startsWith("STAGE PLANS") ||
                t.startsWith("Stage:") || t.startsWith("Map Reduce") ||
                t.startsWith("Map Operator Tree") || t.startsWith("Reduce Operator Tree")) return -1;
            if (t.contains(": ") && !t.endsWith("Operator")) return -1;
            int spaces = line.length() - line.stripLeading().length();
            return spaces / 2;
        },
        line -> {
            String t = line.stripLeading();
            if (t.endsWith("Operator") || t.equals("TableScan")) {
                return t.endsWith("Operator") ? t.substring(0, t.length() - " Operator".length()) : t;
            }
            return null;
        },
        line -> Optional.empty(),
        line -> {
            var m = Pattern.compile("Num rows:\\s*(\\d+)").matcher(line);
            return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
        }
    );

    public HiveDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("hive");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                  String database, String schema) {
        try {
            List<Map<String, Object>> rows = queryForList(withDatabaseOverride(conn, database), decryptedPassword, "EXPLAIN " + sql);
            StringBuilder raw = new StringBuilder();
            for (var row : rows) {
                for (Object v : row.values()) {
                    if (v != null) raw.append(v).append('\n');
                }
            }
            List<ExplainNode> rawNodes = mapTextPlanToNodes(raw.toString(), HIVE_GRAMMAR);
            List<ExplainNode> nodes = applyHiveScanTypes(rawNodes);
            List<String> warnings = List.of(translator.get("diagnostics.warning.hive_partition_check"));
            return DiagnosticResult.ok(new ExplainPlan("hive", raw.toString(), nodes, null, warnings));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "hive");
        }
    }

    private List<ExplainNode> applyHiveScanTypes(List<ExplainNode> nodes) {
        List<ExplainNode> out = new ArrayList<>();
        for (ExplainNode n : nodes) {
            ScanType st = "TableScan".equals(n.operation()) ? ScanType.FULL_SCAN : ScanType.OTHER;
            out.add(new ExplainNode(n.operation(), n.table(), st, n.rows(), n.cost(), n.extra(),
                applyHiveScanTypes(n.children())));
        }
        return out;
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.space.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.hive"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.hive"));
    }
}