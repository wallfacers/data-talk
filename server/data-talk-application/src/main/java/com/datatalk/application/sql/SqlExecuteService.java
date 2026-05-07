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

    public sealed interface Outcome permits Executed, RequiresConfirmation, ConfirmationInvalid {
        ResolvedDataContextDto resolvedContext();
        String contextNotice();
    }

    public record Executed(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        List<ResultItem> results
    ) implements Outcome {}

    public record RequiresConfirmation(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        String level,
        String reason,
        List<String> affectedObjects,
        String sqlPreview
    ) implements Outcome {}

    public record ConfirmationInvalid(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        String reason,
        String ackedRisk,
        String currentRisk,
        String message
    ) implements Outcome {}

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

    private final SqlRiskAnalyzer riskAnalyzer;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SessionDataContextService sessionDataContextService;
    private final TableContextAutoResolver tableContextAutoResolver;
    private final SqlStatementSplitters sqlStatementSplitters;
    private final SqlExecutionPlanner sqlExecutionPlanner = new SqlExecutionPlanner();
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

    public Outcome execute(
        String connectionId, String sql, String source, String sessionId,
        String database, String schema, boolean confirmed, RiskLevel riskAck
    ) {
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException(translator.get("error.sql.required"));
        validateSource(source);

        ResolvedExecutionContext requestedContext = resolveExecutionContext(sessionId, connectionId, database, schema);
        List<String> statements = sqlStatementSplitters.split(requestedContext.connection().kind(), sql);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(translator.get("error.sql.required"));
        }

        ResolvedExecutionContext context = tableContextAutoResolver.resolve(requestedContext, sql);
        ResolvedDataContextDto resolvedDto = toDto(context);

        // Risk gate applies to BOTH user-typed and AI-prefilled SQL. The
        // Workbench tab is the single trusted execution surface for L2 / L3
        // statements; the source label never confers a trust bypass.
        SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY, context.connection().kind());
        if (risk.riskLevel() != null
            && (risk.riskLevel() == RiskLevel.L2 || risk.riskLevel() == RiskLevel.L3)) {
            if (!confirmed) {
                return new RequiresConfirmation(
                    resolvedDto, context.contextNotice(),
                    risk.riskLevel().name(), risk.reason(), risk.affectedObjects(), sql);
            }
            if (riskAck == null || riskAck.ordinal() < risk.riskLevel().ordinal()) {
                return new ConfirmationInvalid(
                    resolvedDto, context.contextNotice(),
                    "risk_ack_insufficient",
                    riskAck == null ? null : riskAck.name(),
                    risk.riskLevel().name(),
                    translator.get("sql.confirmation.invalid.message"));
            }
        }

        List<ResultItem> items = runStatements(context, statements);
        return new Executed(resolvedDto, context.contextNotice(), items);
    }

    private List<ResultItem> runStatements(ResolvedExecutionContext context, List<String> statements) {
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
                List<SqlExecutionPlanner.ExecutionUnit> executionUnits = sqlExecutionPlanner.plan(statements);
                for (SqlExecutionPlanner.ExecutionUnit executionUnit : executionUnits) {
                    if (executionUnit instanceof SqlExecutionPlanner.DmlBatch dmlBatch) {
                        long started = System.currentTimeMillis();
                        try (Statement stmt = c.createStatement()) {
                            stmt.setQueryTimeout(30);
                            int affectedRows;
                            if (dmlBatch.rewrittenStatement().isPresent()) {
                                affectedRows = normalizeAffectedRows(stmt.executeUpdate(dmlBatch.rewrittenStatement().get()));
                            } else {
                                for (String statementText : dmlBatch.statementTexts()) {
                                    stmt.addBatch(statementText);
                                }
                                affectedRows = sumAffectedRows(stmt.executeBatch());
                            }
                            long executionMs = System.currentTimeMillis() - started;
                            pendingDmlSummary = appendPendingDmlSummary(
                                pendingDmlSummary,
                                dmlBatch.startIndex(),
                                dmlBatch.endIndex(),
                                dmlBatch.statementTexts(),
                                affectedRows,
                                executionMs
                            );
                        } catch (SQLException e) {
                            pendingDmlSummary = flushPendingDmlSummary(results, pendingDmlSummary);
                            long executionMs = System.currentTimeMillis() - started;
                            results.add(new ResultItem(
                                nextResultId(),
                                "error",
                                translator.get("sql.result.error.title", dmlBatch.startIndex()),
                                dmlBatch.startIndex(),
                                joinStatementTexts(dmlBatch.statementTexts()),
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
                        continue;
                    }

                    SqlExecutionPlanner.SingleStatement single = (SqlExecutionPlanner.SingleStatement) executionUnit;
                    String statementText = single.statementText();
                    int statementIndex = single.statementIndex();
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
                            int affectedRows = normalizeAffectedRows(stmt.getUpdateCount());
                            pendingDmlSummary = appendPendingDmlSummary(
                                pendingDmlSummary,
                                statementIndex,
                                statementIndex,
                                List.of(statementText),
                                affectedRows,
                                executionMs
                            );
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
                throw executionFailure(
                    e,
                    context.connection(),
                    context.database(),
                    "sql.result.execution_failed.markdown.stage.batch"
                );
            }
        } catch (SQLException e) {
            throw executionFailure(
                e,
                context.connection(),
                context.database(),
                "sql.result.execution_failed.markdown.stage.connect"
            );
        }

        return results;
    }

    private DmlSummaryAccumulator appendPendingDmlSummary(
        DmlSummaryAccumulator pendingDmlSummary,
        int startIndex,
        int endIndex,
        List<String> statementTexts,
        int affectedRows,
        long executionMs
    ) {
        if (pendingDmlSummary == null) {
            return new DmlSummaryAccumulator(
                startIndex,
                endIndex,
                new ArrayList<>(statementTexts),
                affectedRows,
                executionMs
            );
        }
        pendingDmlSummary.statementTexts().addAll(statementTexts);
        return pendingDmlSummary.with(
            endIndex,
            pendingDmlSummary.affectedRows() + affectedRows,
            pendingDmlSummary.executionMs() + executionMs
        );
    }

    private static int sumAffectedRows(int[] updateCounts) {
        int total = 0;
        for (int updateCount : updateCounts) {
            total += normalizeAffectedRows(updateCount);
        }
        return total;
    }

    private static int normalizeAffectedRows(int updateCount) {
        return updateCount > 0 ? updateCount : 0;
    }

    private static String joinStatementTexts(List<String> statementTexts) {
        return String.join(";\n", statementTexts);
    }

    private ResolvedDataContextDto toDto(ResolvedExecutionContext context) {
        return new ResolvedDataContextDto(
            context.connection().id(), context.connection().name(),
            context.database(), context.schema(), context.selectedLevel()
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
                row.add(JdbcResultValueNormalizer.normalize(rs.getObject(i)));
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

    private RuntimeException executionFailure(
        SQLException error,
        ConnectionRecord connection,
        String database,
        String stageKey
    ) {
        return new RuntimeException(
            formatExecutionFailureMarkdown(error, connection, database, stageKey),
            error
        );
    }

    private String formatExecutionFailureMarkdown(
        SQLException error,
        ConnectionRecord connection,
        String database,
        String stageKey
    ) {
        Throwable rootCause = mostSpecificCause(error);
        String driverMessage = sanitizeDriverMessage(rootCause.getMessage());

        StringBuilder markdown = new StringBuilder();
        markdown
            .append("## ")
            .append(translator.get("sql.result.execution_failed"))
            .append("\n\n")
            .append(translator.get("sql.result.execution_failed.markdown.summary"))
            .append("\n\n")
            .append("### ")
            .append(translator.get("sql.result.execution_failed.markdown.connection"))
            .append("\n\n");

        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.stage"), translator.get(stageKey));
        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.name"), connection.name());
        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.kind"), connection.kind());
        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.host"), connection.host());
        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.port"), String.valueOf(connection.port()));
        appendMarkdownBullet(
            markdown,
            translator.get("sql.result.execution_failed.markdown.database"),
            hasText(database) ? database : translator.get("sql.result.execution_failed.markdown.database.unset")
        );
        appendMarkdownBullet(markdown, translator.get("sql.result.execution_failed.markdown.username"), connection.username());
        appendMarkdownBullet(
            markdown,
            translator.get("sql.result.execution_failed.markdown.exception"),
            rootCause.getClass().getSimpleName()
        );

        markdown
            .append("\n### ")
            .append(translator.get("sql.result.execution_failed.markdown.raw"))
            .append("\n\n```text\n")
            .append(driverMessage)
            .append("\n```");

        List<String> hints = executionFailureHints(connection);
        if (!hints.isEmpty()) {
            markdown
                .append("\n\n### ")
                .append(translator.get("sql.result.execution_failed.markdown.hints"))
                .append("\n\n");
            for (String hint : hints) {
                markdown.append("- ").append(hint).append('\n');
            }
        }

        return markdown.toString().trim();
    }

    private List<String> executionFailureHints(ConnectionRecord connection) {
        List<String> hints = new ArrayList<>();
        if ("mysql".equalsIgnoreCase(connection.kind()) || "mariadb".equalsIgnoreCase(connection.kind()) || "apache_doris".equalsIgnoreCase(connection.kind()) || "starrocks".equalsIgnoreCase(connection.kind())) {
            hints.add(translator.get("sql.result.execution_failed.markdown.hint.mysql"));
        }
        hints.add(translator.get(
            "sql.result.execution_failed.markdown.hint.reachability",
            connection.host(),
            connection.port()
        ));
        if ("localhost".equalsIgnoreCase(connection.host()) || "127.0.0.1".equals(connection.host())) {
            hints.add(translator.get("sql.result.execution_failed.markdown.hint.localhost_container"));
        }
        return hints;
    }

    private void appendMarkdownBullet(StringBuilder markdown, String label, String value) {
        markdown
            .append("- **")
            .append(label)
            .append(":** `")
            .append(escapeMarkdownInline(value))
            .append("`\n");
    }

    private static Throwable mostSpecificCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String sanitizeDriverMessage(String message) {
        if (!hasText(message)) {
            return translator.get("sql.result.execution_failed");
        }
        return message.replace("```", "'''").trim();
    }

    private static String escapeMarkdownInline(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("`", "\\`").replace("\r", " ").replace("\n", " ");
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
        if (("mysql".equalsIgnoreCase(context.connection().kind())
            || "mariadb".equalsIgnoreCase(context.connection().kind())
            || "apache_doris".equalsIgnoreCase(context.connection().kind())
            || "starrocks".equalsIgnoreCase(context.connection().kind()))
            && hasText(context.database())) {
            connection.setCatalog(context.database());
        }
        if (("trino".equalsIgnoreCase(context.connection().kind())
            || "presto".equalsIgnoreCase(context.connection().kind()))
            && hasText(context.database())) {
            connection.setCatalog(context.database());
        }
        if (("sqlserver".equalsIgnoreCase(context.connection().kind()))
            && hasText(context.database())) {
            connection.setCatalog(context.database());
        }
        if ("clickhouse".equalsIgnoreCase(context.connection().kind())
            && hasText(context.database())) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("USE " + context.database());
            }
        }
        if ("hive".equalsIgnoreCase(context.connection().kind())
            && hasText(context.database())) {
            try (var stmt = connection.createStatement()) {
                stmt.execute("USE `" + context.database() + "`");
            }
        }
        if (("postgres".equalsIgnoreCase(context.connection().kind())
            || "postgresql".equalsIgnoreCase(context.connection().kind())
            || "h2".equalsIgnoreCase(context.connection().kind())
            || "oracle".equalsIgnoreCase(context.connection().kind())
            || "sqlserver".equalsIgnoreCase(context.connection().kind())
            || "duckdb".equalsIgnoreCase(context.connection().kind())
            || "trino".equalsIgnoreCase(context.connection().kind())
            || "presto".equalsIgnoreCase(context.connection().kind()))
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
            connection.lastTestAt(),
            connection.oracleServiceType(),
            connection.sqlserverEncrypt(),
            connection.sqlserverTrustServerCertificate(),
            connection.sqlserverInstanceName(),
            connection.readOnly()
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
