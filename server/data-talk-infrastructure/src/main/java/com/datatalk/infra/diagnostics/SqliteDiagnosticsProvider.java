package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.DiagnosticCapability;
import com.datatalk.domain.diagnostics.DiagnosticResult;
import com.datatalk.domain.diagnostics.ExplainNode;
import com.datatalk.domain.diagnostics.ExplainPlan;
import com.datatalk.domain.diagnostics.Impact;
import com.datatalk.domain.diagnostics.IndexRecommendation;
import com.datatalk.domain.diagnostics.LockReport;
import com.datatalk.domain.diagnostics.OptimizeTablePreview;
import com.datatalk.domain.diagnostics.OptimizeTableResult;
import com.datatalk.domain.diagnostics.PoolReport;
import com.datatalk.domain.diagnostics.ScanType;
import com.datatalk.domain.diagnostics.SpaceReport;
import com.datatalk.domain.diagnostics.TerminateSessionPreview;
import com.datatalk.domain.diagnostics.TerminateSessionResult;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqliteDiagnosticsProvider extends AbstractDiagnosticsProvider {

    private static final Pattern TABLE_FROM_DETAIL = Pattern.compile(
        "(?:SCAN|SEARCH)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    public SqliteDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("sqlite");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        try {
            List<Map<String, Object>> rows = queryForList(conn, decryptedPassword, "EXPLAIN QUERY PLAN " + sql);
            List<ExplainNode> nodes = parseSqliteRows(rows);
            List<String> warnings = new ArrayList<>();
            for (ExplainNode n : nodes) collectWarnings(n, warnings);
            return DiagnosticResult.ok(new ExplainPlan("sqlite", String.valueOf(rows), nodes, null, warnings));
        } catch (SQLException e) {
            return DiagnosticResult.error("SQLITE_EXPLAIN_ERROR", e.getMessage());
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
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "sqlite"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "sqlite"));
    }

    // --- SQLite-specific EXPLAIN QUERY PLAN parsing ---

    private record SqliteRow(int id, int parent, String detail, String table, ScanType scanType) {}

    private List<ExplainNode> parseSqliteRows(List<Map<String, Object>> rows) {
        // SQLite provides id/parent as explicit columns. Two-pass construction:
        // First pass: collect metadata, second pass: build from root recursively
        List<SqliteRow> tmps = new ArrayList<>();
        for (var row : rows) {
            int id = ((Number) row.getOrDefault("id", 0)).intValue();
            int parent = ((Number) row.getOrDefault("parent", 0)).intValue();
            String detail = String.valueOf(row.getOrDefault("detail", ""));
            String table = extractTable(detail);
            ScanType st = scanTypeFromDetail(detail);
            tmps.add(new SqliteRow(id, parent, detail, table, st));
        }
        Map<Integer, SqliteRow> tmpById = new HashMap<>();
        Map<Integer, List<Integer>> childIdsByParent = new HashMap<>();
        for (var t : tmps) {
            tmpById.put(t.id(), t);
            childIdsByParent.computeIfAbsent(t.parent(), k -> new ArrayList<>()).add(t.id());
        }
        return childIdsByParent.getOrDefault(0, List.of()).stream()
            .map(rootId -> buildSqliteNode(rootId, tmpById, childIdsByParent))
            .toList();
    }

    private ExplainNode buildSqliteNode(int id, Map<Integer, SqliteRow> tmpById, Map<Integer, List<Integer>> childIdsByParent) {
        SqliteRow t = tmpById.get(id);
        List<ExplainNode> children = childIdsByParent.getOrDefault(id, List.of()).stream()
            .map(cid -> buildSqliteNode(cid, tmpById, childIdsByParent))
            .toList();
        return new ExplainNode(t.detail(), t.table(), t.scanType(), 0L, null, null, children);
    }

    private static String extractTable(String detail) {
        if (detail == null) return null;
        Matcher m = TABLE_FROM_DETAIL.matcher(detail);
        return m.find() ? m.group(1) : null;
    }

    private static ScanType scanTypeFromDetail(String detail) {
        if (detail == null) return ScanType.OTHER;
        String upper = detail.toUpperCase(Locale.ROOT);
        if (upper.contains("USING ROWID") || upper.contains("USING INTEGER PRIMARY KEY")) {
            return ScanType.CONST;
        }
        if (upper.contains("USING COVERING INDEX")) {
            return ScanType.INDEX_SCAN;
        }
        if (upper.contains("SEARCH ") && upper.contains("USING INDEX")) {
            return upper.contains("(=") ? ScanType.REF : ScanType.INDEX_RANGE;
        }
        if (upper.contains("SCAN ") && !upper.contains("USING INDEX")) {
            return ScanType.FULL_SCAN;
        }
        return ScanType.OTHER;
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
                recs.add(new IndexRecommendation(
                    node.table(), cols, "BTREE", Impact.MEDIUM,
                    translator.get("diagnostics.recommendation.full_scan", node.table(), "0")
                ));
            }
        }
        for (ExplainNode child : node.children()) collectRecommendations(child, sql, recs);
    }
}