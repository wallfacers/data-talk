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
    DiagnosticResult<PoolReport>                connectionPoolInfo(ConnectionRecord conn, String decryptedPassword);
    DiagnosticResult<SpaceReport>               tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database);
}
