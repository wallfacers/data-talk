package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.DiagnosticCapability;
import com.datatalk.domain.diagnostics.DiagnosticResult;
import com.datatalk.domain.diagnostics.ExplainPlan;
import com.datatalk.domain.diagnostics.IndexRecommendation;
import com.datatalk.domain.diagnostics.LockReport;
import com.datatalk.domain.diagnostics.OptimizeTablePreview;
import com.datatalk.domain.diagnostics.OptimizeTableResult;
import com.datatalk.domain.diagnostics.PoolReport;
import com.datatalk.domain.diagnostics.SpaceReport;
import com.datatalk.domain.diagnostics.TerminateSessionPreview;
import com.datatalk.domain.diagnostics.TerminateSessionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SqliteDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public SqliteDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("sqlite");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.explain_unsupported", "sqlite"));
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                  ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints_unsupported", "sqlite"));
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
}
