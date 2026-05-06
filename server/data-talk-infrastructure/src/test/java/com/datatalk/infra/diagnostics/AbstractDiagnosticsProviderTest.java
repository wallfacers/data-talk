package com.datatalk.infra.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class AbstractDiagnosticsProviderTest {

    @Test
    void executeStatementAutoCommit_enablesAutoCommitBeforeCreatingStatement() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        TestProvider provider = new TestProvider(connection);

        provider.executeStatementAutoCommit(testConn(), "pw", "VACUUM (FULL) \"public\".\"users\"");

        InOrder inOrder = inOrder(connection, statement);
        inOrder.verify(connection).setAutoCommit(true);
        inOrder.verify(connection).createStatement();
        inOrder.verify(statement).execute("VACUUM (FULL) \"public\".\"users\"");
    }

    private ConnectionRecord testConn() {
        return new ConnectionRecord(
            "c1", "test", "postgresql", "localhost", 5432,
            "postgres", "user", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null, false);
    }

    private static class TestProvider extends AbstractDiagnosticsProvider {
        private final Connection connection;

        TestProvider(Connection connection) {
            super(null);
            this.connection = connection;
        }

        @Override
        protected Connection openConnection(ConnectionRecord conn, String decryptedPassword) {
            return connection;
        }

        @Override
        public Set<String> supportedDriverTypes() {
            return Set.of("test");
        }

        @Override
        public Set<DiagnosticCapability> supportedCapabilities() {
            return Set.of();
        }

        @Override
        public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword, String database, String schema) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn, String decryptedPassword) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
            return DiagnosticResult.unsupported("unsupported");
        }

        @Override
        public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
            return DiagnosticResult.unsupported("unsupported");
        }
    }
}
