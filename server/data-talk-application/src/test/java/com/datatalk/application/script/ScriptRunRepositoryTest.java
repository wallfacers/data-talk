package com.datatalk.application.script;

import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptRunRepositoryTest {

    private ScriptRunRepository repo;
    private Connection conn;
    private DataSource ds;

    private static final Instant NOW = Instant.parse("2026-05-18T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-18T10:05:00Z");

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "mem:srr" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        conn = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "");
        conn.createStatement().execute("""
            CREATE TABLE script_run (
                id VARCHAR(255) PRIMARY KEY,
                script_content TEXT NOT NULL,
                language VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL,
                exit_code INT,
                stdout_text TEXT,
                connection_id VARCHAR(255),
                target_table VARCHAR(255),
                rows_written INT NOT NULL DEFAULT 0,
                name VARCHAR(255),
                created_by_kind VARCHAR(50),
                created_by_session_id VARCHAR(255),
                error_message TEXT,
                started_at TIMESTAMP,
                finished_at TIMESTAMP,
                duration_ms BIGINT
            )
            """);
        ds = new SingleConnectionDataSource(conn, true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repo = new ScriptRunRepository(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void save_persistsRunToDatabase() {
        ScriptRun run = new ScriptRun(
            "run-1", "print(1)", ScriptLanguage.PYTHON, ScriptStatus.RUNNING,
            null, null, "conn-1", null, 0,
            "MyScript", "ai", "session-1",
            null, NOW, null, null
        );

        repo.save(run);

        Optional<ScriptRun> found = repo.findById("run-1");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("run-1");
        assertThat(found.get().scriptContent()).isEqualTo("print(1)");
        assertThat(found.get().language()).isEqualTo(ScriptLanguage.PYTHON);
        assertThat(found.get().status()).isEqualTo(ScriptStatus.RUNNING);
        assertThat(found.get().connectionId()).isEqualTo("conn-1");
        assertThat(found.get().targetTable()).isNull();
        assertThat(found.get().rowsWritten()).isEqualTo(0);
        assertThat(found.get().name()).isEqualTo("MyScript");
        assertThat(found.get().createdByKind()).isEqualTo("ai");
        assertThat(found.get().createdBySessionId()).isEqualTo("session-1");
        assertThat(found.get().startedAt()).isEqualTo(NOW);
        assertThat(found.get().finishedAt()).isNull();
        assertThat(found.get().durationMs()).isNull();
    }

    @Test
    void save_withAllFields_persistsCorrectly() {
        ScriptRun run = new ScriptRun(
            "run-2", "SELECT 1", ScriptLanguage.JAVASCRIPT, ScriptStatus.COMPLETED,
            0, "OK", "conn-2", "target_tbl", 42,
            "Query", "user", "session-2",
            null, NOW, LATER, 300_000L
        );

        repo.save(run);

        Optional<ScriptRun> found = repo.findById("run-2");
        assertThat(found).isPresent();
        assertThat(found.get().exitCode()).isEqualTo(0);
        assertThat(found.get().stdoutText()).isEqualTo("OK");
        assertThat(found.get().targetTable()).isEqualTo("target_tbl");
        assertThat(found.get().rowsWritten()).isEqualTo(42);
        assertThat(found.get().finishedAt()).isEqualTo(LATER);
        assertThat(found.get().durationMs()).isEqualTo(300_000L);
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        Optional<ScriptRun> found = repo.findById("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findByConnectionId_returnsFilteredAndOrdered() {
        repo.save(runFixture("run-a", "conn-1", NOW, ScriptStatus.RUNNING));
        repo.save(runFixture("run-b", "conn-2", NOW.plusSeconds(10), ScriptStatus.COMPLETED));
        repo.save(runFixture("run-c", "conn-1", NOW.plusSeconds(5), ScriptStatus.FAILED));

        List<ScriptRun> conn1Runs = repo.list("conn-1", 50);

        assertThat(conn1Runs).hasSize(2);
        // Ordered by started_at DESC
        assertThat(conn1Runs.get(0).id()).isEqualTo("run-c");
        assertThat(conn1Runs.get(1).id()).isEqualTo("run-a");
    }

    @Test
    void list_nullConnectionId_returnsAllOrderedByTimeDesc() {
        repo.save(runFixture("run-1", "conn-1", NOW, ScriptStatus.RUNNING));
        repo.save(runFixture("run-2", "conn-2", NOW.plusSeconds(10), ScriptStatus.COMPLETED));
        repo.save(runFixture("run-3", "conn-1", NOW.plusSeconds(5), ScriptStatus.FAILED));

        List<ScriptRun> all = repo.list(null, 50);

        assertThat(all).hasSize(3);
        assertThat(all.get(0).id()).isEqualTo("run-2"); // latest first
        assertThat(all.get(1).id()).isEqualTo("run-3");
        assertThat(all.get(2).id()).isEqualTo("run-1");
    }

    @Test
    void list_withLimit_returnsLimitedResults() {
        repo.save(runFixture("run-1", "conn-1", NOW, ScriptStatus.RUNNING));
        repo.save(runFixture("run-2", "conn-1", NOW.plusSeconds(1), ScriptStatus.RUNNING));
        repo.save(runFixture("run-3", "conn-1", NOW.plusSeconds(2), ScriptStatus.RUNNING));

        List<ScriptRun> limited = repo.list("conn-1", 2);

        assertThat(limited).hasSize(2);
    }

    @Test
    void updateStatus_updatesAllFields() {
        repo.save(runFixture("run-1", "conn-1", NOW, ScriptStatus.RUNNING));

        repo.updateStatus("run-1", ScriptStatus.COMPLETED, 0, null, "output",
            100, LATER, 300_000L);

        ScriptRun updated = repo.findById("run-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ScriptStatus.COMPLETED);
        assertThat(updated.exitCode()).isEqualTo(0);
        assertThat(updated.errorMessage()).isNull();
        assertThat(updated.stdoutText()).isEqualTo("output");
        assertThat(updated.rowsWritten()).isEqualTo(100);
        assertThat(updated.finishedAt()).isEqualTo(LATER);
        assertThat(updated.durationMs()).isEqualTo(300_000L);
    }

    @Test
    void updateRowsWritten_viaUpdateStatus_updatesOnlyRowsWritten() {
        repo.save(runFixture("run-1", "conn-1", NOW, ScriptStatus.RUNNING));

        repo.updateStatus("run-1", ScriptStatus.RUNNING, null, null, null,
            50, null, null);

        ScriptRun updated = repo.findById("run-1").orElseThrow();
        assertThat(updated.rowsWritten()).isEqualTo(50);
        assertThat(updated.status()).isEqualTo(ScriptStatus.RUNNING);
    }

    @Test
    void updateTargetTable_updatesTableName() {
        repo.save(runFixture("run-1", "conn-1", NOW, ScriptStatus.RUNNING));
        assertThat(repo.findById("run-1").orElseThrow().targetTable()).isNull();

        repo.updateTargetTable("run-1", "my_output_table");

        ScriptRun updated = repo.findById("run-1").orElseThrow();
        assertThat(updated.targetTable()).isEqualTo("my_output_table");
    }

    private static ScriptRun runFixture(String id, String connectionId, Instant startedAt, ScriptStatus status) {
        return new ScriptRun(id, "print(1)", ScriptLanguage.PYTHON, status,
            null, null, connectionId, null, 0,
            "script", "ai", "session-1",
            null, startedAt, null, null);
    }
}
