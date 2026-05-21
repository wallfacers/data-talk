package com.datatalk.application.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.util.List;
import java.util.Set;

public interface DiagnosticsProvider {
    Set<String> supportedDriverTypes();
    Set<DiagnosticCapability> supportedCapabilities();

    DiagnosticResult<ExplainPlan>               explain(String sql, ConnectionRecord conn, String decryptedPassword, String database, String schema);
    DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn, String decryptedPassword);
    DiagnosticResult<LockReport>                lockInfo(ConnectionRecord conn, String decryptedPassword, String database);
    DiagnosticResult<PoolReport>                poolStatus(ConnectionRecord conn, String decryptedPassword);
    DiagnosticResult<SpaceReport>               tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables);
    DiagnosticResult<TerminateSessionPreview>   terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database);
    DiagnosticResult<TerminateSessionResult>    terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database);
    DiagnosticResult<OptimizeTablePreview>      optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database);
    DiagnosticResult<OptimizeTableResult>       optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database);
}
