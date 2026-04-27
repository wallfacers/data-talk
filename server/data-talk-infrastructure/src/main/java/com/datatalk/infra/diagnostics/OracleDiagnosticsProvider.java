package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OracleDiagnosticsProvider implements DiagnosticsProvider {

    private static final String STUB_MESSAGE = "Coming in a future release";

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("oracle");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        return DiagnosticResult.unsupported(STUB_MESSAGE);
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(STUB_MESSAGE);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(STUB_MESSAGE);
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(STUB_MESSAGE);
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(STUB_MESSAGE);
    }
}
