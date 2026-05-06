package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlExecuteServiceSplitterSelectionTest {

    private ConnectionRepository connectionRepository;
    private ConnectionService connectionService;
    private SessionDataContextService sessionDataContextService;
    private TableContextAutoResolver tableContextAutoResolver;
    private SqlStatementSplitters sqlStatementSplitters;
    private SqlExecuteService service;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:sql-execute-splitter;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE items(id INT PRIMARY KEY, name VARCHAR(50))");
            statement.execute("INSERT INTO items VALUES (1, 'a'), (2, 'b')");
        }

        connectionRepository = mock(ConnectionRepository.class);
        connectionService = mock(ConnectionService.class);
        sessionDataContextService = mock(SessionDataContextService.class);
        tableContextAutoResolver = mock(TableContextAutoResolver.class);
        sqlStatementSplitters = mock(SqlStatementSplitters.class);

        var record = new ConnectionRecord(
            "conn-1",
            "Primary",
            "h2",
            "localhost",
            0,
            "mem:sql-execute-splitter;DB_CLOSE_DELAY=-1",
            "sa",
            new byte[0],
            null,
            Instant.parse("2026-04-22T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null,
            null, 1, true, null, false);
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(record));
        when(connectionService.decryptPassword("conn-1")).thenReturn("");
        when(tableContextAutoResolver.resolve(new ResolvedExecutionContext(record, record.databaseName(), null), "ignored script"))
            .thenReturn(new ResolvedExecutionContext(record, record.databaseName(), null));
        when(sqlStatementSplitters.split("h2", "ignored script"))
            .thenReturn(List.of(
                "SELECT name FROM items WHERE id = 1",
                "SELECT name FROM items WHERE id = 2"
            ));

        service = new SqlExecuteService(
            new CalciteSqlRiskAnalyzer(sqlStatementSplitters),
            connectionRepository,
            connectionService,
            sessionDataContextService,
            tableContextAutoResolver,
            sqlStatementSplitters,
            translator(),
            100
        );
    }

    @Test
    void delegates_statement_splitting_to_the_injected_splitter_facade() {
        var outcome = service.execute("conn-1", "ignored script", "user", null, null, null, false, null);

        assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
        SqlExecuteService.Executed result = (SqlExecuteService.Executed) outcome;
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).statementText()).isEqualTo("SELECT name FROM items WHERE id = 1");
        assertThat(result.results().get(1).statementText()).isEqualTo("SELECT name FROM items WHERE id = 2");
        verify(sqlStatementSplitters, times(2)).split("h2", "ignored script");
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.sql.required", Locale.ENGLISH, "SQL is required");
        source.addMessage("error.sql.source_invalid", Locale.ENGLISH, "Source must be one of: user, ai");
        source.addMessage("error.connection.id_required", Locale.ENGLISH, "Connection ID is required");
        source.addMessage("error.connection.unknown", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("sql.result_set.title", Locale.ENGLISH, "Result Set {0}");
        source.addMessage("sql.result.error.title", Locale.ENGLISH, "Error {0}");
        source.addMessage("sql.result.execution_failed", Locale.ENGLISH, "SQL execution failed");
        source.addMessage("sql.dml_summary.title.single", Locale.ENGLISH, "DML Summary {0}");
        source.addMessage("sql.dml_summary.title.range", Locale.ENGLISH, "DML Summary {0}-{1}");
        return new Translator(source);
    }
}
