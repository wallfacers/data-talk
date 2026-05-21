package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DuckDbDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public DuckDbDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("duckdb");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        try {
            // DuckDB EXPLAIN returns single-column multi-row text output
            List<Map<String, Object>> rows = queryForList(withDatabaseOverride(conn, database), decryptedPassword, "EXPLAIN " + sql);
            if (rows.isEmpty()) {
                return DiagnosticResult.ok(new ExplainPlan("duckdb", "", List.of(), null, List.of()));
            }
            StringBuilder raw = new StringBuilder();
            for (var row : rows) {
                for (Object v : row.values()) {
                    if (v != null) raw.append(v).append('\n');
                }
            }
            var grammar = duckDbGrammar();
            var nodes = mapTextPlanToNodes(raw.toString(), grammar);
            return DiagnosticResult.ok(new ExplainPlan("duckdb", raw.toString(), nodes, null, List.of()));
        } catch (SQLException e) {
            return DiagnosticResult.error("DUCKDB_EXPLAIN_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.duckdb"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "duckdb"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "duckdb"));
    }

    /**
     * DuckDB EXPLAIN grammar - simplified single-layer node sequence.
     * DuckDB EXPLAIN output uses box-drawing characters (┌└├│) for visual tree,
     * Day-2 grammar extracts flat node sequence without strict parent-child hierarchy.
     */
    private static TextPlanGrammar duckDbGrammar() {
        return new TextPlanGrammar(
            "duckdb",
            // indentFn: skip box-drawing lines, treat all valid lines as depth 0
            line -> {
                String t = line.trim();
                if (t.isEmpty()) return -1;
                if (t.startsWith("┌") || t.startsWith("└") || t.startsWith("├") || t.startsWith("│")) return -1;
                return 0;
            },
            // operatorFn: extract operator name (first word before space)
            line -> {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("┌") || t.startsWith("└") || t.startsWith("├") || t.startsWith("│")) return null;
                int sp = t.indexOf(' ');
                return sp < 0 ? t : t.substring(0, sp);
            },
            // tableFn: DuckDB text plan does not expose table name in operator line (Day-2 simplification)
            line -> Optional.empty(),
            // rowsFn: extract "N Rows" or "~N Rows" pattern
            line -> {
                var m = Pattern.compile("~?(\\d+)\\s+Rows").matcher(line);
                return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
            }
        );
    }
}
