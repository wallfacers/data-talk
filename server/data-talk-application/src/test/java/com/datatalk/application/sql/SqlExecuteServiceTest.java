package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.RiskLevel;
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
import static org.mockito.Mockito.when;

class SqlExecuteServiceTest {

    private static final String CONN_ID = "conn-1";

    private ConnectionRepository connectionRepository;
    private ConnectionService connectionService;
    private SessionDataContextService sessionDataContextService;
    private TableContextAutoResolver tableContextAutoResolver;
    private SqlStatementSplitters sqlStatementSplitters;
    private SqlExecuteService service;
    private ConnectionRecord record;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:sql-execute-test;DB_CLOSE_DELAY=-1", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE t(id INT PRIMARY KEY, x INT)");
            statement.execute("CREATE TABLE logs(id INT PRIMARY KEY, msg VARCHAR(100))");
            statement.execute("INSERT INTO t VALUES (1, 0)");
            statement.execute("INSERT INTO logs VALUES (1, 'hello')");
        }

        connectionRepository = mock(ConnectionRepository.class);
        connectionService = mock(ConnectionService.class);
        sessionDataContextService = mock(SessionDataContextService.class);
        tableContextAutoResolver = mock(TableContextAutoResolver.class);
        sqlStatementSplitters = mock(SqlStatementSplitters.class);

        record = new ConnectionRecord(
            CONN_ID,
            "Test H2",
            "h2",
            "localhost",
            0,
            "mem:sql-execute-test;DB_CLOSE_DELAY=-1",
            "sa",
            new byte[0],
            null,
            Instant.parse("2026-04-22T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null
        );
        when(connectionRepository.findById(CONN_ID)).thenReturn(Optional.of(record));
        when(connectionService.decryptPassword(CONN_ID)).thenReturn("");

        service = new SqlExecuteService(
            new CalciteSqlRiskAnalyzer(),
            connectionRepository,
            connectionService,
            sessionDataContextService,
            tableContextAutoResolver,
            sqlStatementSplitters,
            translator(),
            100
        );
    }

    private void setupContextAndSplitter(String sql, List<String> splitStatements) {
        ResolvedExecutionContext ctx = new ResolvedExecutionContext(record, record.databaseName(), null);
        when(tableContextAutoResolver.resolve(
            new ResolvedExecutionContext(record, record.databaseName(), null), sql))
            .thenReturn(ctx);
        when(sqlStatementSplitters.split("h2", sql)).thenReturn(splitStatements);
    }

    @Test
    void l1ExecutesWithoutConfirmation() {
        setupContextAndSplitter("SELECT 1", List.of("SELECT 1"));

        var outcome = service.execute(CONN_ID, "SELECT 1", "user", null, null, null, false, null);

        assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
    }

    @Test
    void l2WithoutConfirmedReturnsRequiresConfirmation() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, false, null);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
            assertThat(r.level()).isEqualTo("L2");
            assertThat(r.reason()).isEqualTo("update_with_where");
            assertThat(r.affectedObjects()).containsExactly("t");
        });
    }

    @Test
    void l2WithMatchingAckExecutes() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, RiskLevel.L2);

        assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
    }

    @Test
    void l2WithLowerAckIsConfirmationInvalid() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, RiskLevel.L1);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.ConfirmationInvalid.class, i -> {
            assertThat(i.reason()).isEqualTo("risk_ack_insufficient");
            assertThat(i.ackedRisk()).isEqualTo("L1");
            assertThat(i.currentRisk()).isEqualTo("L2");
        });
    }

    @Test
    void l2WithNullAckIsConfirmationInvalid() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, null);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.ConfirmationInvalid.class, i -> {
            assertThat(i.reason()).isEqualTo("risk_ack_insufficient");
            assertThat(i.ackedRisk()).isNull();
        });
    }

    @Test
    void l3WithoutConfirmedReturnsRequiresConfirmation() {
        String sql = "DELETE FROM logs";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, false, null);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
            assertThat(r.level()).isEqualTo("L3");
            assertThat(r.reason()).isEqualTo("delete_without_where");
            assertThat(r.affectedObjects()).containsExactly("logs");
        });
    }

    @Test
    void l3WithMatchingAckExecutes() {
        // Use DELETE without WHERE (L3) since we can't DROP and still have the table
        String sql = "DELETE FROM logs";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, RiskLevel.L3);

        assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
    }

    @Test
    void l3WithLowerAckIsConfirmationInvalid() {
        String sql = "DELETE FROM logs";
        setupContextAndSplitter(sql, List.of(sql));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, RiskLevel.L2);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.ConfirmationInvalid.class, i -> {
            assertThat(i.reason()).isEqualTo("risk_ack_insufficient");
            assertThat(i.ackedRisk()).isEqualTo("L2");
            assertThat(i.currentRisk()).isEqualTo("L3");
        });
    }

    @Test
    void multiStatementBatchWithL3UsesHighestRisk() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1; DELETE FROM logs;";
        setupContextAndSplitter(sql, List.of("UPDATE t SET x = 1 WHERE id = 1", "DELETE FROM logs"));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, false, null);

        assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
            assertThat(r.level()).isEqualTo("L3");
            assertThat(r.affectedObjects()).containsExactlyInAnyOrder("t", "logs");
        });
    }

    @Test
    void multiStatementBatchWithHighestAckExecutes() {
        String sql = "UPDATE t SET x = 1 WHERE id = 1; DELETE FROM logs;";
        setupContextAndSplitter(sql, List.of("UPDATE t SET x = 1 WHERE id = 1", "DELETE FROM logs"));

        var outcome = service.execute(CONN_ID, sql, "user", null, null, null, true, RiskLevel.L3);

        assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
        SqlExecuteService.Executed executed = (SqlExecuteService.Executed) outcome;
        // UPDATE + DELETE are both DML without result sets, so they coalesce into a single dml_summary
        assertThat(executed.results()).hasSize(1);
        assertThat(executed.results().get(0).kind()).isEqualTo("dml_summary");
        assertThat(executed.results().get(0).affectedRows()).isGreaterThan(0);
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
        source.addMessage("sql.confirmation.invalid.message", Locale.ENGLISH,
            "Acknowledged risk is lower than the current statement risk — please review and confirm again");
        return new Translator(source);
    }
}
