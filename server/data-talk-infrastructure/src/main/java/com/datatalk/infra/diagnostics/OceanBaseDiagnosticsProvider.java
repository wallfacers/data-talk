package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OceanBaseDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public OceanBaseDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("oceanbase");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();  // Day-1: all dialect_unsupported
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
                                                  String decryptedPassword, String database) {
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
        String key = "diagnostics.dialect_unsupported.oceanbase." + capability;
        String msg = translator.get(key);
        return DiagnosticResult.unsupported(msg);
    }
}
