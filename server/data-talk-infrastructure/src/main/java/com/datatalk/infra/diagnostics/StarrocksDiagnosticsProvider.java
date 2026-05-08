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
public class StarrocksDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public StarrocksDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("starrocks");
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
            // Reuse Doris grammar and OlapScanType inference - StarRocks is a Doris fork with identical EXPLAIN format
            var nodes = DorisDiagnosticsProvider.applyOlapScanTypes(
                mapTextPlanToNodes(raw.toString(), DorisDiagnosticsProvider.DORIS_GRAMMAR),
                raw.toString()
            );
            return DiagnosticResult.ok(new ExplainPlan("starrocks", raw.toString(), nodes, null, List.of()));
        } catch (SQLException e) {
            return mapPermissionOrDriverError(e, "EXPLAIN", "starrocks");
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints.unsupported.starrocks"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "starrocks"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "starrocks"));
    }
}