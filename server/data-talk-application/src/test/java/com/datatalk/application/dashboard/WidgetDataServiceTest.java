package com.datatalk.application.dashboard;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.application.sql.TableContextAutoResolver;
import com.datatalk.entity.DbConnection;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the database/schema resolution priority in WidgetDataService:
 * widget.query.database/schema  >  dashboard.defaultDatabase/Schema  >  connection.databaseName.
 *
 * Pinned by BUG-0012: server-level MySQL connections (databaseName=null) must still
 * execute widget SQL when the dashboard declares a defaultDatabase.
 */
class WidgetDataServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private DashboardArtifactService dashboardService;
    private ConnectionRepository connectionRepository;
    private ConnectionService connectionService;
    private SqlExecutionRepository sqlExecutionRepository;
    private SqlStatementGuard statementGuard;
    private TableContextAutoResolver tableContextAutoResolver;
    private Translator translator;
    private WidgetDataService service;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardArtifactService.class);
        connectionRepository = mock(ConnectionRepository.class);
        connectionService = mock(ConnectionService.class);
        sqlExecutionRepository = mock(SqlExecutionRepository.class);
        statementGuard = mock(SqlStatementGuard.class);
        tableContextAutoResolver = mock(TableContextAutoResolver.class);
        translator = mock(Translator.class);

        // Auto-resolver is a pass-through in these tests: the resolution priority logic
        // lives in WidgetDataService itself, not in the resolver.
        when(tableContextAutoResolver.resolve(any(), anyString()))
            .thenAnswer(inv -> inv.getArgument(0));

        when(connectionService.decryptPassword(anyString())).thenReturn("pw");

        service = new WidgetDataService(
            dashboardService,
            connectionRepository,
            connectionService,
            sqlExecutionRepository,
            statementGuard,
            tableContextAutoResolver,
            translator);
    }

    @Test
    void dashboardDefaultDatabaseOverridesConnectionLevelDatabase() throws Exception {
        // Connection is server-level (databaseName=null), dashboard pins defaultDatabase=test_store.
        givenDashboard("""
            {
              "schemaVersion": 2,
              "id": "dash_x",
              "defaultConnectionId": "conn_1",
              "defaultDatabase": "test_store",
              "widgets": [
                { "id": "chart_w_a", "query": { "sql": "SELECT 1 FROM orders", "paramRefs": {} } }
              ]
            }
            """);
        givenServerLevelMysqlConnection("conn_1");
        givenSqlReturnsEmptyResult();

        service.fetchWidgetData("dash_x", "chart_w_a", Map.of());

        ArgumentCaptor<DbConnection> capt = ArgumentCaptor.forClass(DbConnection.class);
        ArgumentCaptor<String> schemaCapt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(sqlExecutionRepository).execute(capt.capture(), anyString(), schemaCapt.capture());
        assertThat(capt.getValue().databaseName()).isEqualTo("test_store");
        assertThat(schemaCapt.getValue()).isNull();
    }

    @Test
    void widgetLevelOverridesDashboardDefault() throws Exception {
        givenDashboard("""
            {
              "schemaVersion": 2,
              "id": "dash_x",
              "defaultConnectionId": "conn_1",
              "defaultDatabase": "test_store",
              "defaultSchema": "public",
              "widgets": [
                {
                  "id": "chart_w_b",
                  "query": {
                    "sql": "SELECT 1 FROM analytics.orders",
                    "paramRefs": {},
                    "database": "analytics",
                    "schema": "reporting"
                  }
                }
              ]
            }
            """);
        givenServerLevelMysqlConnection("conn_1");
        givenSqlReturnsEmptyResult();

        service.fetchWidgetData("dash_x", "chart_w_b", Map.of());

        ArgumentCaptor<DbConnection> capt = ArgumentCaptor.forClass(DbConnection.class);
        ArgumentCaptor<String> schemaCapt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(sqlExecutionRepository).execute(capt.capture(), anyString(), schemaCapt.capture());
        assertThat(capt.getValue().databaseName()).isEqualTo("analytics");
        assertThat(schemaCapt.getValue()).isEqualTo("reporting");
    }

    @Test
    void fallsBackToConnectionDatabaseWhenDashboardAndWidgetSilent() throws Exception {
        givenDashboard("""
            {
              "schemaVersion": 2,
              "id": "dash_x",
              "defaultConnectionId": "conn_1",
              "widgets": [
                { "id": "chart_w_c", "query": { "sql": "SELECT 1 FROM orders", "paramRefs": {} } }
              ]
            }
            """);
        givenConnectionWithDatabase("conn_1", "test_store");
        givenSqlReturnsEmptyResult();

        service.fetchWidgetData("dash_x", "chart_w_c", Map.of());

        ArgumentCaptor<DbConnection> capt = ArgumentCaptor.forClass(DbConnection.class);
        org.mockito.Mockito.verify(sqlExecutionRepository).execute(capt.capture(), anyString(), any());
        assertThat(capt.getValue().databaseName()).isEqualTo("test_store");
    }

    @Test
    void dashboardDefaultSchemaPropagatesWhenWidgetSilent() throws Exception {
        givenDashboard("""
            {
              "schemaVersion": 2,
              "id": "dash_x",
              "defaultConnectionId": "conn_1",
              "defaultDatabase": "warehouse",
              "defaultSchema": "public",
              "widgets": [
                { "id": "chart_w_d", "query": { "sql": "SELECT 1 FROM orders", "paramRefs": {} } }
              ]
            }
            """);
        givenServerLevelMysqlConnection("conn_1");
        givenSqlReturnsEmptyResult();

        service.fetchWidgetData("dash_x", "chart_w_d", Map.of());

        ArgumentCaptor<String> schemaCapt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(sqlExecutionRepository).execute(any(), anyString(), schemaCapt.capture());
        assertThat(schemaCapt.getValue()).isEqualTo("public");
    }

    @Test
    void blankStringIsTreatedAsAbsentAndFallsThroughToConnection() throws Exception {
        // Both dashboard and widget have empty-string database fields — must NOT overwrite connection default.
        givenDashboard("""
            {
              "schemaVersion": 2,
              "id": "dash_x",
              "defaultConnectionId": "conn_1",
              "defaultDatabase": "",
              "widgets": [
                {
                  "id": "chart_w_e",
                  "query": { "sql": "SELECT 1 FROM orders", "paramRefs": {}, "database": "" }
                }
              ]
            }
            """);
        givenConnectionWithDatabase("conn_1", "fallback_db");
        givenSqlReturnsEmptyResult();

        service.fetchWidgetData("dash_x", "chart_w_e", Map.of());

        ArgumentCaptor<DbConnection> capt = ArgumentCaptor.forClass(DbConnection.class);
        org.mockito.Mockito.verify(sqlExecutionRepository).execute(capt.capture(), anyString(), any());
        assertThat(capt.getValue().databaseName()).isEqualTo("fallback_db");
    }

    // ── helpers ──

    private void givenDashboard(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        when(dashboardService.load(anyString())).thenReturn(node);
    }

    private void givenServerLevelMysqlConnection(String id) {
        when(connectionRepository.findById(id)).thenReturn(Optional.of(connectionRecord(id, null)));
    }

    private void givenConnectionWithDatabase(String id, String databaseName) {
        when(connectionRepository.findById(id)).thenReturn(Optional.of(connectionRecord(id, databaseName)));
    }

    private ConnectionRecord connectionRecord(String id, String databaseName) {
        return new ConnectionRecord(
            id, "test-mysql", "mysql", "127.0.0.1", 3306,
            databaseName,
            "root", new byte[0],
            null, 0L, 3000,
            "ok", 0L,
            null, 1, true, null, false,
            null, null, null);
    }

    private void givenSqlReturnsEmptyResult() {
        when(sqlExecutionRepository.execute(any(DbConnection.class), anyString(), any()))
            .thenReturn(new QueryResult(List.of(), List.of(), 0L));
    }
}
