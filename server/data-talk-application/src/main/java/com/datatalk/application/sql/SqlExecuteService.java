package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.ResolvedDataContextDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SqlExecuteService {

    public record Result(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        List<ResultItem> results
    ) {}

    public record ResultItem(
        String resultId,
        String kind,
        String title,
        int statementIndex,
        String statementText,
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        long executionMs,
        boolean truncated,
        Integer affectedRows,
        String errorMessage
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
    private final TableContextAutoResolver tableContextAutoResolver;
    private final SqlStatementSplitters sqlStatementSplitters;
    private final Translator translator;
    private final int maxRows;

    public SqlExecuteService(SqlRiskAnalyzer riskAnalyzer,
                             ConnectionRepository connRepo,
                             ConnectionService connSvc,
                             SessionDataContextService sessionDataContextService,
                             TableContextAutoResolver tableContextAutoResolver,
                             SqlStatementSplitters sqlStatementSplitters,
                             Translator translator,
                             @Value("${datatalk.sql.max-rows:5000}") int maxRows) {
        this.riskAnalyzer = riskAnalyzer;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionDataContextService = sessionDataContextService;
        this.tableContextAutoResolver = tableContextAutoResolver;
        this.sqlStatementSplitters = sqlStatementSplitters;
        this.translator = translator;
        this.maxRows = maxRows;
    }

    public Result execute(String connectionId, String sql, String source, String sessionId, String database, String schema) {
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException(translator.get("error.sql.required"));
        String normalizedSource = validateSource(source);

        ResolvedExecutionContext requestedContext = resolveExecutionContext(sessionId, connectionId, database, schema);
        List<String> statements = sqlStatementSplitters.split(requestedContext.connection().kind(), sql);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(translator.get("error.sql.required"));
        }

        ResolvedExecutionContext context = tableContextAutoResolver.resolve(
            requestedContext,
            sql
        );

        if ("user".equals(normalizedSource)) {
            SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
            if (RiskLevel.L3.equals(risk.riskLevel())) {
                throw new SqlRiskBlockedException(new RiskBlocked("HIGH", risk.reason()));
            }
        }

        ConnectionRecord cr = context.connection();
        List<ResultItem> results = new ArrayList<>();
        DmlSummaryAccumulator pendingDmlSummary = null;

        try (Connection c = DriverManager.getConnection(
                 JdbcUrlBuilder.build(withDatabase(cr, context.database())),
                 cr.username(),
                 connSvc.decryptPassword(cr.id()))) {
            applyExecutionContext(c, context);
            c.setAutoCommit(false);
            boolean failed = false;
            try {
                for (int i = 0; i < statements.size(); i++) {
                    String statementText = statements.get(i);
                    int statementIndex = i + 1;
                    long started = System.currentTimeMillis();
                    try (Statement stmt = c.createStatement()) {
                        stmt.setQueryTimeout(30);
                        boolean hasResultSet = stmt.execute(statementText);
                        long executionMs = System.currentTimeMillis() - started;
                        if (hasResultSet) {
                            pendingDmlSummary = flushPendingDmlSummary(results, pendingDmlSummary);
                            try (ResultSet rs = stmt.getResultSet()) {
                                ResultSetData resultSetData = readResultSet(rs);
                                results.add(new ResultItem(
                                    nextResultId(),
                                    "result_set",
                                    translator.get("sql.result_set.title", statementIndex),
                                    statementIndex,
                                    statementText,
                                    resultSetData.columns(),
                                    resultSetData.rows(),
                                    resultSetData.rowCount(),
                                    executionMs,
                                    resultSetData.truncated(),
                                    null,
                                    null
                                ));
                            }
                        } else {
                            int affectedRows = Math.max(stmt.getUpdateCount(), 0);
                            if (pendingDmlSummary == null) {
                                pendingDmlSummary = new DmlSummaryAccumulator(
                                    statementIndex,
                                    statementIndex,
                                    new ArrayList<>(List.of(statementText)),
                                    affectedRows,
                                    executionMs
                                );
                            } else {
                                pendingDmlSummary.statementTexts().add(statementText);
                                pendingDmlSummary = pendingDmlSummary.with(
                                    statementIndex,
                                    pendingDmlSummary.affectedRows() + affectedRows,
                                    pendingDmlSummary.executionMs() + executionMs
                                );
                            }
                        }
                    } catch (SQLException e) {
                        pendingDmlSummary = flushPendingDmlSummary(results, pendingDmlSummary);
                        long executionMs = System.currentTimeMillis() - started;
                        results.add(new ResultItem(
                            nextResultId(),
                            "error",
                            translator.get("sql.result.error.title", statementIndex),
                            statementIndex,
                            statementText,
                            List.of(),
                            List.of(),
                            0,
                            executionMs,
                            false,
                            null,
                            sanitizeSqlErrorMessage(e)
                        ));
                        c.rollback();
                        failed = true;
                        break;
                    }
                }
                if (!failed) {
                    flushPendingDmlSummary(results, pendingDmlSummary);
                    c.commit();
                }
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw new RuntimeException(translator.get("sql.result.execution_failed"), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(translator.get("sql.result.execution_failed"), e);
        }

        return new Result(
            new ResolvedDataContextDto(
                context.connection().id(),
                context.connection().name(),
                context.database(),
                context.schema(),
                context.selectedLevel()
            ),
            context.contextNotice(),
            results
        );
    }

    private ResultSetData readResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }
        return new ResultSetData(columns, rows, rows.size(), truncated);
    }

    private DmlSummaryAccumulator flushPendingDmlSummary(
        List<ResultItem> results,
        DmlSummaryAccumulator pendingDmlSummary
    ) {
        if (pendingDmlSummary == null) {
            return null;
        }
        String title = pendingDmlSummary.startIndex() == pendingDmlSummary.endIndex()
            ? translator.get("sql.dml_summary.title.single", pendingDmlSummary.startIndex())
            : translator.get(
                "sql.dml_summary.title.range",
                pendingDmlSummary.startIndex(),
                pendingDmlSummary.endIndex()
            );
        results.add(new ResultItem(
            nextResultId(),
            "dml_summary",
            title,
            pendingDmlSummary.startIndex(),
            String.join(";\n", pendingDmlSummary.statementTexts()),
            Collections.emptyList(),
            Collections.emptyList(),
            pendingDmlSummary.affectedRows(),
            pendingDmlSummary.executionMs(),
            false,
            pendingDmlSummary.affectedRows(),
            null
        ));
        return null;
    }

    private static String nextResultId() {
        return UUID.randomUUID().toString();
    }

    private String validateSource(String source) {
        if ("user".equals(source) || "ai".equals(source)) {
            return source;
        }
        throw new IllegalArgumentException(translator.get("error.sql.source_invalid"));
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort rollback during error path.
        }
    }

    private String sanitizeSqlErrorMessage(SQLException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return translator.get("sql.result.execution_failed");
        }
        int statementMarker = message.indexOf("; SQL statement:");
        String sanitized = statementMarker >= 0 ? message.substring(0, statementMarker) : message;
        return sanitized.trim();
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
            throw new IllegalArgumentException(translator.get("error.connection.id_required"));
        }

        ConnectionRecord connection = connRepo.findById(resolvedConnectionId)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.connection.unknown", resolvedConnectionId)));
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

    private record ResultSetData(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated
    ) {}

    private record DmlSummaryAccumulator(
        int startIndex,
        int endIndex,
        List<String> statementTexts,
        int affectedRows,
        long executionMs
    ) {
        private DmlSummaryAccumulator with(int endIndex, int affectedRows, long executionMs) {
            return new DmlSummaryAccumulator(startIndex, endIndex, statementTexts, affectedRows, executionMs);
        }
    }
}
