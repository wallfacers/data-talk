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
public class ClickHouseDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final TextPlanGrammar CLICKHOUSE_GRAMMAR = new TextPlanGrammar(
        "clickhouse",
        line -> {
            // 2 spaces = one level; skip Indexes / PrimaryKey / Keys / Granules sub-lines
            if (line.isBlank()) return -1;
            String t = line.stripLeading();
            if (t.startsWith("Indexes:") || t.startsWith("PrimaryKey") || t.startsWith("Keys:") ||
                t.startsWith("Condition:") || t.startsWith("Parts:") || t.startsWith("Granules:")) return -1;
            int spaces = line.length() - line.stripLeading().length();
            return spaces / 2;
        },
        line -> {
            String t = line.stripLeading();
            // take first token until space or parenthesis
            int end = -1;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == ' ' || c == '(') { end = i; break; }
            }
            return end < 0 ? t : t.substring(0, end);
        },
        line -> {
            // ReadFromMergeTree (default.orders)
            var m = Pattern.compile("ReadFromMergeTree\\s*\\(([^)]+)\\)").matcher(line);
            return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
        },
        line -> Optional.empty()
    );

    public ClickHouseDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("clickhouse");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        try {
            List<Map<String, Object>> rows = queryForList(withDatabaseOverride(conn, database), decryptedPassword, "EXPLAIN PLAN " + sql);
            StringBuilder raw = new StringBuilder();
            for (var row : rows) for (Object v : row.values()) if (v != null) raw.append(v).append('\n');
            List<ExplainNode> nodes = applyClickHouseScanTypes(
                mapTextPlanToNodes(raw.toString(), CLICKHOUSE_GRAMMAR),
                raw.toString()
            );
            return DiagnosticResult.ok(new ExplainPlan("clickhouse", raw.toString(), nodes, null, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "clickhouse");
        }
    }

    private List<ExplainNode> applyClickHouseScanTypes(List<ExplainNode> nodes, String raw) {
        List<ExplainNode> out = new ArrayList<>();
        for (ExplainNode n : nodes) {
            ScanType st = ScanType.OTHER;
            long rowsEst = n.rows();
            if ("ReadFromMergeTree".equals(n.operation()) && n.table() != null) {
                // search for Granules: N/M after the ReadFromMergeTree node in raw
                int idx = raw.indexOf("ReadFromMergeTree (" + n.table() + ")");
                if (idx >= 0) {
                    String segment = raw.substring(idx, Math.min(raw.length(), idx + 1024));
                    var m = Pattern.compile("Granules:\\s+(\\d+)/(\\d+)").matcher(segment);
                    if (m.find()) {
                        long picked = Long.parseLong(m.group(1));
                        long total = Long.parseLong(m.group(2));
                        rowsEst = picked * 8192L;
                        st = picked == total ? ScanType.FULL_SCAN : ScanType.INDEX_RANGE;
                    } else {
                        st = ScanType.FULL_SCAN;
                    }
                }
            }
            out.add(new ExplainNode(n.operation(), n.table(), st, rowsEst, n.cost(), n.extra(),
                applyClickHouseScanTypes(n.children(), raw)));
        }
        return out;
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.space.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.clickhouse"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize.unsupported.clickhouse"));
    }
}