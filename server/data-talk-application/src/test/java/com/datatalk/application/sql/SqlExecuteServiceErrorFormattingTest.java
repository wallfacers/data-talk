package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.UndoLogCapture;
import com.datatalk.domain.preference.UserPreferences;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlExecuteServiceErrorFormattingTest {

    @Test
    void formats_connection_failures_as_markdown_diagnostics() {
        ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
        ConnectionService connectionService = mock(ConnectionService.class);
        SessionDataContextService sessionDataContextService = mock(SessionDataContextService.class);
        TableContextAutoResolver tableContextAutoResolver = mock(TableContextAutoResolver.class);
        SqlStatementSplitters sqlStatementSplitters = mock(SqlStatementSplitters.class);

        var record = new ConnectionRecord(
            "conn-broken",
            "Broken MySQL",
            "mysql",
            "127.0.0.1",
            1,
            null,
            "root",
            new byte[0],
            null,
            Instant.parse("2026-04-22T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null,
            null, 1, true, null, false, null, null, null);
        when(connectionRepository.findById("conn-broken")).thenReturn(Optional.of(record));
        when(connectionService.decryptPassword("conn-broken")).thenReturn("bad-password");
        when(tableContextAutoResolver.resolve(new ResolvedExecutionContext(record, null, null), "SELECT 1"))
            .thenReturn(new ResolvedExecutionContext(record, null, null));
        when(sqlStatementSplitters.split("mysql", "SELECT 1")).thenReturn(List.of("SELECT 1"));

        UserPreferencesService userPrefsService = mock(UserPreferencesService.class);
        when(userPrefsService.getPreferences()).thenReturn(UserPreferences.DEFAULT);

        UndoLogCapture undoLogCapture = mock(UndoLogCapture.class);

        SqlExecuteService service = new SqlExecuteService(
            new CalciteSqlRiskAnalyzer(sqlStatementSplitters),
            connectionRepository,
            connectionService,
            sessionDataContextService,
            tableContextAutoResolver,
            sqlStatementSplitters,
            userPrefsService,
            translator(),
            undoLogCapture,
            100
        );

        assertThatThrownBy(() -> service.execute("conn-broken", "SELECT 1", "user", null, null, null, false, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("## SQL execution failed")
            .hasMessageContaining("127.0.0.1")
            .hasMessageContaining("```text")
            .hasMessageContaining("Suggested checks");
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.sql.required", Locale.ENGLISH, "SQL is required");
        source.addMessage("error.sql.source_invalid", Locale.ENGLISH, "Source must be one of: user, ai");
        source.addMessage("error.connection.id_required", Locale.ENGLISH, "Connection ID is required");
        source.addMessage("error.connection.unknown", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("sql.result.execution_failed", Locale.ENGLISH, "SQL execution failed");
        source.addMessage("sql.result.execution_failed.markdown.summary", Locale.ENGLISH, "DataTalk failed before the SQL batch could complete.");
        source.addMessage("sql.result.execution_failed.markdown.connection", Locale.ENGLISH, "Connection context");
        source.addMessage("sql.result.execution_failed.markdown.stage", Locale.ENGLISH, "Failure stage");
        source.addMessage("sql.result.execution_failed.markdown.stage.connect", Locale.ENGLISH, "Open JDBC connection");
        source.addMessage("sql.result.execution_failed.markdown.stage.batch", Locale.ENGLISH, "Run SQL batch");
        source.addMessage("sql.result.execution_failed.markdown.name", Locale.ENGLISH, "Data source");
        source.addMessage("sql.result.execution_failed.markdown.kind", Locale.ENGLISH, "Database kind");
        source.addMessage("sql.result.execution_failed.markdown.host", Locale.ENGLISH, "Host");
        source.addMessage("sql.result.execution_failed.markdown.port", Locale.ENGLISH, "Port");
        source.addMessage("sql.result.execution_failed.markdown.database", Locale.ENGLISH, "Database");
        source.addMessage("sql.result.execution_failed.markdown.database.unset", Locale.ENGLISH, "(not selected)");
        source.addMessage("sql.result.execution_failed.markdown.username", Locale.ENGLISH, "Username");
        source.addMessage("sql.result.execution_failed.markdown.exception", Locale.ENGLISH, "Exception");
        source.addMessage("sql.result.execution_failed.markdown.raw", Locale.ENGLISH, "Driver message");
        source.addMessage("sql.result.execution_failed.markdown.hints", Locale.ENGLISH, "Suggested checks");
        source.addMessage("sql.result.execution_failed.markdown.hint.mysql", Locale.ENGLISH, "Confirm the MySQL service is running.");
        source.addMessage("sql.result.execution_failed.markdown.hint.reachability", Locale.ENGLISH, "Confirm the backend process can reach {0}:{1}.");
        source.addMessage("sql.result.execution_failed.markdown.hint.localhost_container", Locale.ENGLISH, "If the backend runs in Docker or another container, localhost points to the container itself.");
        return new Translator(source);
    }
}
