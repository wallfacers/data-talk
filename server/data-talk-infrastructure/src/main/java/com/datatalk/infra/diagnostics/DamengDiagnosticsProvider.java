package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DamengDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public DamengDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("dameng");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        // Day-1: all capabilities are "supported" so DiagnosticsService
        // routes to our methods, which return dialect_unsupported with
        // dameng-specific i18n keys.
        return Set.of(
            DiagnosticCapability.EXPLAIN,
            DiagnosticCapability.INDEX_HINTS,
            DiagnosticCapability.LOCK_INFO,
            DiagnosticCapability.POOL_STATUS,
            DiagnosticCapability.TABLE_SPACE,
            DiagnosticCapability.TERMINATE_SESSION,
            DiagnosticCapability.OPTIMIZE_TABLE
        );
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database, String schema) {
        return dialectUnsupported("explain_real");
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn,
                                                                   String decryptedPassword) {
        return dialectUnsupported("index_hints");
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database) {
        return dialectUnsupported("lock_info");
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn,
                                                    String decryptedPassword) {
        return dialectUnsupported("pool_status");
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn,
                                                         String decryptedPassword,
                                                         String database, List<String> tables) {
        return dialectUnsupported("table_space");
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(
            ConnectionRecord conn, String decryptedPassword,
            String targetSessionId, String database) {
        return dialectUnsupported("terminate_session");
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(
            ConnectionRecord conn, String decryptedPassword,
            String targetSessionId, String database) {
        return dialectUnsupported("terminate_session");
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(
            ConnectionRecord conn, String decryptedPassword,
            String table, String schemaName, String database) {
        return dialectUnsupported("optimize_table");
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(
            ConnectionRecord conn, String decryptedPassword,
            String table, String schemaName, String database) {
        return dialectUnsupported("optimize_table");
    }

    private <T> DiagnosticResult<T> dialectUnsupported(String capability) {
        String key = "diagnostics.dialect_unsupported.dameng." + capability;
        String msg = translator.getOrDefault(key, null);
        if (msg == null) {
            // Key not yet in i18n bundle — use the default DiagnosticService path
            return DiagnosticResult.unsupported(translator.get(
                "diagnostics.no_provider", "dameng"));
        }
        return DiagnosticResult.unsupported(msg);
    }
}
