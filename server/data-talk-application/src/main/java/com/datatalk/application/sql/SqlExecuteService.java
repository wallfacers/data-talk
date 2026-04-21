package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SqlExecuteService {

    public record Result(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        long executionMs,
        boolean truncated
    ) {}

    public record RiskBlocked(String riskLevel, String riskReason) {}

    public static class SqlRiskBlockedException extends RuntimeException {
        private final RiskBlocked risk;
        public SqlRiskBlockedException(RiskBlocked risk) {
            super("SQL risk blocked: " + risk.riskLevel());
            this.risk = risk;
        }
        public RiskBlocked risk() { return risk; }
    }

    private final SqlRiskAnalyzer riskAnalyzer;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SessionDataContextService sessionDataContextService;
    private final int maxRows;

    public SqlExecuteService(SqlRiskAnalyzer riskAnalyzer,
                             ConnectionRepository connRepo,
                             ConnectionService connSvc,
                             SessionDataContextService sessionDataContextService,
                             @Value("${datatalk.sql.max-rows:5000}") int maxRows) {
        this.riskAnalyzer = riskAnalyzer;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionDataContextService = sessionDataContextService;
        this.maxRows = maxRows;
    }

    public Result execute(String connectionId, String sql, String source, String sessionId, String database, String schema) {
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException("sql required");

        ResolvedExecutionContext context = resolveExecutionContext(sessionId, connectionId, database, schema);

        if ("user".equals(source)) {
            SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
            if (RiskLevel.L3.equals(risk.riskLevel())) {
                throw new SqlRiskBlockedException(new RiskBlocked("HIGH", risk.reason()));
            }
        }

        ConnectionRecord cr = context.connection();

        long started = System.currentTimeMillis();
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;

        try (Connection c = DriverManager.getConnection(
                 JdbcUrlBuilder.build(withDatabase(cr, context.database())),
                 cr.username(),
                 connSvc.decryptPassword(cr.id()));
             PreparedStatement ps = c.prepareStatement(sql)) {
            applyExecutionContext(c, context);
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                for (int i = 1; i <= colCount; i++) columns.add(md.getColumnLabel(i));
                while (rs.next()) {
                    if (rows.size() >= maxRows) { truncated = true; break; }
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }

        return new Result(columns, rows, rows.size(), System.currentTimeMillis() - started, truncated);
    }

    private ResolvedExecutionContext resolveExecutionContext(
        String sessionId,
        String connectionId,
        String database,
        String schema
    ) {
        SessionDataContextRecord sessionContext = null;
        if (hasText(sessionId)) {
            sessionContext = sessionDataContextService.get(sessionId);
        }
        String resolvedConnectionId = firstNonBlank(
            connectionId,
            sessionContext == null ? null : sessionContext.connectionId()
        );
        if (!hasText(resolvedConnectionId)) {
            throw new IllegalArgumentException("connectionId required");
        }

        ConnectionRecord connection = connRepo.findById(resolvedConnectionId)
            .orElseThrow(() -> new NoSuchElementException("unknown connection: " + resolvedConnectionId));
        boolean inheritsSessionScope = sessionContext != null
            && resolvedConnectionId.equals(sessionContext.connectionId());
        String resolvedDatabase = firstNonBlank(
            database,
            inheritsSessionScope ? sessionContext.databaseName() : null,
            connection.databaseName()
        );
        String resolvedSchema = firstNonBlank(
            schema,
            inheritsSessionScope ? sessionContext.schemaName() : null
        );
        return new ResolvedExecutionContext(connection, resolvedDatabase, resolvedSchema);
    }

    private void applyExecutionContext(Connection connection, ResolvedExecutionContext context) throws SQLException {
        if ("mysql".equalsIgnoreCase(context.connection().kind())
            && hasText(context.database())) {
            connection.setCatalog(context.database());
        }
        if (("postgres".equalsIgnoreCase(context.connection().kind())
            || "postgresql".equalsIgnoreCase(context.connection().kind())
            || "h2".equalsIgnoreCase(context.connection().kind()))
            && hasText(context.schema())) {
            connection.setSchema(context.schema());
        }
    }

    private ConnectionRecord withDatabase(ConnectionRecord connection, String database) {
        return new ConnectionRecord(
            connection.id(),
            connection.name(),
            connection.kind(),
            connection.host(),
            connection.port(),
            database,
            connection.username(),
            connection.passwordEnc(),
            connection.schemaDigest(),
            connection.createdAt(),
            connection.connectTimeout(),
            connection.lastTestStatus(),
            connection.lastTestAt()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
