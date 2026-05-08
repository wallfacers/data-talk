package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TrinoDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public TrinoDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("trino");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN);
    }

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
            // Reuse PrestoDiagnosticsProvider's TRINO_GRAMMAR and applyTrinoScanTypes
            List<ExplainNode> nodes = PrestoDiagnosticsProvider.applyTrinoScanTypes(
                mapTextPlanToNodes(raw.toString(), PrestoDiagnosticsProvider.TRINO_GRAMMAR)
            );
            List<String> warnings = List.of(translator.get("diagnostics.warning.federated_connector_pushdown"));
            return DiagnosticResult.ok(new ExplainPlan("trino", raw.toString(), nodes, null, warnings));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "trino");
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.trino"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "trino"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "trino"));
    }
}