package com.datatalk.service;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.application.sql.TableContextAutoResolver;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryApplicationServiceTest {

    private ConnectionRepository connectionRepository;
    private ConnectionService connectionService;
    private SessionDataContextService sessionDataContextService;
    private SqlExecutionRepository sqlExecutionRepository;
    private SqlStatementGuard statementGuard;
    private TableContextAutoResolver tableContextAutoResolver;
    private Translator translator;
    private QueryApplicationService service;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(ConnectionRepository.class);
        connectionService = mock(ConnectionService.class);
        sessionDataContextService = mock(SessionDataContextService.class);
        sqlExecutionRepository = mock(SqlExecutionRepository.class);
        translator = translator();
        statementGuard = spy(new SqlStatementGuard(translator));
        tableContextAutoResolver = mock(TableContextAutoResolver.class);
        when(tableContextAutoResolver.resolve(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new QueryApplicationService(
            connectionRepository,
            connectionService,
            sessionDataContextService,
            sqlExecutionRepository,
            statementGuard,
            tableContextAutoResolver,
            translator
        );
    }

    @Test
    void rejectsNonSelectStatementsBeforeLookup() {
        var command = new ExecuteSqlCommand("conn-1", "UPDATE users SET name = 'x'");

        assertThatThrownBy(() -> service.executeQuery(command))
                .isInstanceOf(DataTalkException.class)
                .hasMessageContaining("SELECT / WITH");

        verifyNoInteractions(connectionRepository, sqlExecutionRepository);
    }

    @Test
    void validatesSelectBeforeQueryExecution() {
        var record = new ConnectionRecord(
                "conn-1",
                "Primary",
                "h2",
                "localhost",
                3306,
                "demo",
                "user",
                new byte[0],
                null,
                Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
                3000,
                null,
                null,
            null, 1, true, null);
        var connection = new DbConnection(
                "conn-1",
                "Primary",
                DbType.H2,
                "localhost",
                3306,
                "demo",
                "user",
                "secret",
                Instant.parse("2026-04-20T00:00:00Z")
        );
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(record));
        when(connectionService.decryptPassword("conn-1")).thenReturn("secret");
        when(sqlExecutionRepository.execute(connection, "SELECT 1", null))
                .thenReturn(new QueryResult(List.of("c"), List.of(Map.of("c", 1)), 5L));

        var response = service.executeQuery(new ExecuteSqlCommand("conn-1", "SELECT 1"));

        assertThat(response.durationMs()).isEqualTo(5L);
        var order = inOrder(statementGuard, connectionRepository, sqlExecutionRepository);
        order.verify(statementGuard).assertSelectOnly("SELECT 1");
        order.verify(connectionRepository).findById("conn-1");
        verify(connectionService).decryptPassword("conn-1");
        order.verify(sqlExecutionRepository).execute(
                argThat(actual -> actual != null && "secret".equals(actual.password())),
                eq("SELECT 1"),
                eq(null));
    }

    @Test
    void resolves_connection_database_and_schema_from_session_context() {
        var record = new ConnectionRecord(
            "conn-1",
            "Primary",
            "postgres",
            "localhost",
            5432,
            "default_db",
            "user",
            new byte[0],
            null,
            Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null,
            null, 1, true, null);
        when(sessionDataContextService.get("session-1")).thenReturn(new SessionDataContextRecord(
            "session-1",
            "conn-1",
            "Primary",
            "analytics",
            "reporting",
            "schema",
            1L
        ));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(record));
        when(connectionService.decryptPassword("conn-1")).thenReturn("secret");
        when(sqlExecutionRepository.execute(
            argThat(actual -> actual != null
                && "analytics".equals(actual.databaseName())
                && "secret".equals(actual.password())),
            eq("SELECT 1"),
            eq("reporting")
        )).thenReturn(new QueryResult(List.of("c"), List.of(Map.of("c", 1)), 5L));

        var response = service.executeQuery(new ExecuteSqlCommand(null, "SELECT 1", "session-1", null, null));

        assertThat(response.durationMs()).isEqualTo(5L);
        verify(sessionDataContextService).get("session-1");
        verify(connectionRepository).findById("conn-1");
        verify(sqlExecutionRepository).execute(
            argThat(actual -> actual != null
                && "analytics".equals(actual.databaseName())
                && "secret".equals(actual.password())),
            eq("SELECT 1"),
            eq("reporting")
        );
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.connection.id_required", Locale.ENGLISH, "Connection ID is required");
        source.addMessage("error.database.kind.unsupported", Locale.ENGLISH, "Unsupported database kind: {0}");
        source.addMessage("error.sql.empty_sql", Locale.ENGLISH, "empty SQL");
        source.addMessage("error.sql.multiple_statements", Locale.ENGLISH, "multiple statements not allowed");
        source.addMessage("error.sql.cannot_determine_type", Locale.ENGLISH, "cannot determine statement type");
        source.addMessage("error.sql.only_select_allowed", Locale.ENGLISH, "only SELECT / WITH allowed, got: {0}");
        source.addMessage("error.sql.mvp_only_read", Locale.ENGLISH, "MVP only permits read queries: {0}");
        return new Translator(source);
    }
}
