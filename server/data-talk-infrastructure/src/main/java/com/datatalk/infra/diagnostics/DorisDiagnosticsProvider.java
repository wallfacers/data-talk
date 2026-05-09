package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DorisDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public DorisDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("apache_doris");
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
            var nodes = applyOlapScanTypes(enrichFromRawText(mapTextPlanToNodes(raw.toString(), DORIS_GRAMMAR), raw.toString()), raw.toString());
            return DiagnosticResult.ok(new ExplainPlan("apache_doris", raw.toString(), nodes, null, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "apache_doris");
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.apache_doris"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "apache_doris"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "apache_doris"));
    }

    // package-private static for Starrocks reuse
    static final TextPlanGrammar DORIS_GRAMMAR = new TextPlanGrammar(
        "doris",
        line -> {
            String t = line.stripLeading();
            if (t.startsWith("PLAN FRAGMENT")) return -1;
            if (t.matches("\\d+:[A-Z][A-Z_a-z\\s]+.*")) {
                int spaces = line.length() - line.stripLeading().length();
                return spaces / 2;
            }
            return -1;
        },
        line -> {
            String t = line.stripLeading();
            if (t.matches("\\d+:EXCHANGE\\b.*")) return null;
            var m = Pattern.compile("^\\d+:([A-Z][A-Z_a-z\\s]+)").matcher(t);
            return m.find() ? m.group(1).trim() : null;
        },
        line -> {
            var m = Pattern.compile("TABLE:\\s+(\\S+)").matcher(line);
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        },
        line -> {
            var m = Pattern.compile("cardinality=(\\d+)").matcher(line);
            return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
        }
    );

    private static final Pattern OPERATOR_LINE = Pattern.compile("(?m)^(\\s*)(\\d+:\\S+)");
    private static final Pattern TABLE_PAT = Pattern.compile("TABLE:\\s+(\\S+)");
    private static final Pattern CARD_PAT = Pattern.compile("cardinality=(\\d+)");

    // package-private static for Starrocks reuse
    static List<ExplainNode> enrichFromRawText(List<ExplainNode> nodes, String raw) {
        // mapTextPlanToNodes processes each line independently. In Doris/StarRocks EXPLAIN,
        // TABLE and cardinality appear on continuation lines after the operator line, which
        // indentFn skips (returns -1). Extract them here by scanning raw text per-node.
        String[] blocks = raw.split("\\n\\n");
        List<String> tables = new ArrayList<>();
        List<Long> cardinalities = new ArrayList<>();
        for (String block : blocks) {
            if (!OPERATOR_LINE.matcher(block).find()) continue;
            var tm = TABLE_PAT.matcher(block);
            if (tm.find()) tables.add(tm.group(1));
            var cm = CARD_PAT.matcher(block);
            if (cm.find()) cardinalities.add(Long.parseLong(cm.group(1)));
        }
        var tablesIt = tables.iterator();
        var cardIt = cardinalities.iterator();
        return enrichFromRawTextRecursive(nodes, tablesIt, cardIt);
    }

    private static List<ExplainNode> enrichFromRawTextRecursive(List<ExplainNode> nodes,
                                                                 Iterator<String> tables,
                                                                 Iterator<Long> cardinalities) {
        List<ExplainNode> result = new ArrayList<>();
        for (ExplainNode n : nodes) {
            String table = n.table();
            if (table == null && tables.hasNext()) {
                table = tables.next();
            }
            long rows = n.rows();
            if (rows == 0L && cardinalities.hasNext()) {
                rows = cardinalities.next();
            }
            result.add(new ExplainNode(n.operation(), table, n.scanType(), rows, n.cost(), n.extra(),
                enrichFromRawTextRecursive(n.children(), tables, cardinalities)));
        }
        return result;
    }

    // package-private static for Starrocks reuse
    static List<ExplainNode> applyOlapScanTypes(List<ExplainNode> nodes, String raw) {
        List<ExplainNode> out = new ArrayList<>();
        for (ExplainNode n : nodes) {
            ScanType st = ScanType.OTHER;
            if ("OlapScanNode".equals(n.operation()) && n.table() != null) {
                String segment = extractNodeSegment(raw, n.table());
                boolean hasPredicates = segment.contains("PREDICATES:");
                boolean preaggOn = segment.contains("PREAGGREGATION: ON");
                String rollupName = extractAfter(segment, "rollup:");
                if (rollupName == null) rollupName = extractAfter(segment, "ROLLUP:");
                boolean rollupHit = rollupName != null && !rollupName.equals(n.table());
                if (preaggOn && !hasPredicates) st = ScanType.FULL_SCAN;
                else if (rollupHit && hasPredicates) st = ScanType.INDEX_SCAN;
                else if (hasPredicates) st = ScanType.INDEX_RANGE;
                else st = ScanType.FULL_SCAN;
            }
            out.add(new ExplainNode(n.operation(), n.table(), st, n.rows(), n.cost(), n.extra(),
                applyOlapScanTypes(n.children(), raw)));
        }
        return out;
    }

    private static String extractNodeSegment(String raw, String tableHint) {
        int idx = raw.indexOf("TABLE: " + tableHint);
        if (idx < 0) return "";
        int next = raw.indexOf("\n\n", idx);
        return next < 0 ? raw.substring(idx) : raw.substring(idx, next);
    }

    private static String extractAfter(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        int end = s.indexOf('\n', i);
        return s.substring(i + key.length(), end < 0 ? s.length() : end).trim();
    }
}