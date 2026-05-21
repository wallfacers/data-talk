package com.datatalk.application.dashboard;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.application.sql.TableContextAutoResolver;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Fetches query result data for a single dashboard widget.
 * Loads the dashboard JSON, resolves the widget's SQL and connection,
 * validates read-only via {@link SqlStatementGuard}, executes the query,
 * and returns a flat column/rows payload.
 */
public class WidgetDataService {

    private final DashboardArtifactService dashboardService;
    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;
    private final SqlExecutionRepository sqlExecutionRepository;
    private final SqlStatementGuard statementGuard;
    private final TableContextAutoResolver tableContextAutoResolver;
    private final Translator translator;

    public record WidgetData(List<String> columns, List<List<Object>> rows, long executedAt) {}

    public WidgetDataService(DashboardArtifactService dashboardService,
                             ConnectionRepository connectionRepository,
                             ConnectionService connectionService,
                             SqlExecutionRepository sqlExecutionRepository,
                             SqlStatementGuard statementGuard,
                             TableContextAutoResolver tableContextAutoResolver,
                             Translator translator) {
        this.dashboardService = dashboardService;
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.sqlExecutionRepository = sqlExecutionRepository;
        this.statementGuard = statementGuard;
        this.tableContextAutoResolver = tableContextAutoResolver;
        this.translator = translator;
    }

    /**
     * Execute the widget's query and return the result.
     *
     * @param dashboardId the dashboard ID
     * @param widgetId    the widget ID within the dashboard
     * @param params      optional params (reserved for future parameterized queries)
     * @return query result as flat columns + rows
     */
    public WidgetData fetchWidgetData(String dashboardId, String widgetId, Map<String, Object> params) {
        // 1. Load dashboard
        JsonNode dash = dashboardService.load(dashboardId);

        // 2. Find the widget
        JsonNode widgets = dash.path("widgets");
        JsonNode widget = null;
        if (widgets.isArray()) {
            for (JsonNode w : widgets) {
                if (widgetId.equals(w.path("id").asText(null))) {
                    widget = w;
                    break;
                }
            }
        }
        if (widget == null) {
            throw new IllegalArgumentException("Widget not found: " + widgetId);
        }

        // 3. Extract SQL from widget.query.sql
        JsonNode queryNode = widget.path("query");
        String sql = queryNode.path("sql").asText(null);
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Widget has no SQL query: " + widgetId);
        }

        // 4. Validate read-only
        statementGuard.assertSelectOnly(sql);

        // 5. Resolve connection — widget-level override, then dashboard-level default
        String resolvedConnectionId = queryNode.path("connectionId").asText(null);
        if (resolvedConnectionId == null || resolvedConnectionId.isBlank()) {
            resolvedConnectionId = dash.path("defaultConnectionId").asText(null);
        }
        if (resolvedConnectionId == null || resolvedConnectionId.isBlank()) {
            throw new IllegalArgumentException("No connection configured for widget: " + widgetId);
        }

        final String connId = resolvedConnectionId;
        ConnectionRecord connection = connectionRepository.findById(connId)
            .orElseThrow(() -> new NoSuchElementException("Connection not found: " + connId));

        // 6. Resolve execution context (database, schema)
        //    Priority: widget.query.database/schema > dashboard.defaultDatabase/Schema > connection.databaseName
        //    A blank/null at higher priority falls through to the next level.
        String database = firstNonBlank(
            queryNode.path("database").asText(null),
            dash.path("defaultDatabase").asText(null),
            connection.databaseName()
        );
        String schema = firstNonBlank(
            queryNode.path("schema").asText(null),
            dash.path("defaultSchema").asText(null)
        );
        ResolvedExecutionContext context = new ResolvedExecutionContext(connection, database, schema);
        context = tableContextAutoResolver.resolve(context, sql);

        // 7. Execute
        DbConnection dbConn = toDbConnection(context.connection(), context.database());
        QueryResult result = sqlExecutionRepository.execute(dbConn, sql, context.schema());

        // 8. Transform rows from List<Map> to List<List<Object>>
        List<List<Object>> rows = result.rows().stream()
            .map(row -> result.columns().stream()
                .map(col -> row.get(col))
                .toList())
            .toList();

        return new WidgetData(result.columns(), rows, System.currentTimeMillis());
    }

    private DbConnection toDbConnection(ConnectionRecord record, String databaseName) {
        return new DbConnection(
            record.id(),
            record.name(),
            toDbType(record.kind()),
            record.host(),
            record.port(),
            databaseName,
            record.username(),
            connectionService.decryptPassword(record.id()),
            Instant.ofEpochMilli(record.createdAt())
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private DbType toDbType(String kind) {
        return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> DbType.MYSQL;
            case "postgres", "postgresql" -> DbType.POSTGRESQL;
            case "sqlite" -> DbType.SQLITE;
            case "h2" -> DbType.H2;
            case "sqlserver" -> DbType.SQLSERVER;
            case "oracle" -> DbType.ORACLE;
            default -> throw new IllegalArgumentException(
                translator.get("error.database.kind.unsupported", kind));
        };
    }
}
