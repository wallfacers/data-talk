package com.datatalk.application.script;

import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScriptRunServiceTest {

    private ScriptRunRepository repo;
    private ScriptTokenStore tokenStore;
    private Clock clock;
    private ScriptRunService service;

    private static final Instant NOW = Instant.parse("2026-05-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        repo = mock(ScriptRunRepository.class);
        tokenStore = mock(ScriptTokenStore.class);
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ScriptRunService(repo, tokenStore, clock);
    }

    @Test
    void prepareRun_createsRunRecordAndIssuesToken() {
        when(tokenStore.issue(anyString(), eq("conn-1"))).thenReturn("token-abc");

        var result = service.prepareRun("print(1)", ScriptLanguage.PYTHON, "conn-1",
            "MyScript", "ai", "session-1");

        assertThat(result.runId()).isNotNull();
        assertThat(result.token()).isEqualTo("token-abc");

        verify(repo).save(argThat(run ->
            run.scriptContent().equals("print(1)")
                && run.language() == ScriptLanguage.PYTHON
                && run.connectionId().equals("conn-1")
                && run.name().equals("MyScript")
                && run.createdByKind().equals("ai")
                && run.createdBySessionId().equals("session-1")
                && run.status() == ScriptStatus.RUNNING
                && run.rowsWritten() == 0
                && run.startedAt().equals(NOW)
        ));
        verify(tokenStore).issue(result.runId(), "conn-1");
    }

    @Test
    void completeRun_successStatusWhenExitCodeZero() {
        ScriptRun run = runFixture("run-1", ScriptStatus.RUNNING, NOW);
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        service.completeRun("run-1", 0, "stdout", null);

        verify(repo).updateStatus(eq("run-1"), eq(ScriptStatus.COMPLETED), eq(0), isNull(),
            eq("stdout"), eq(0), eq(NOW), eq(0L));
        verify(tokenStore).revokeByRunId("run-1");
    }

    @Test
    void completeRun_failedStatusWhenExitCodeNonZero() {
        ScriptRun run = runFixture("run-1", ScriptStatus.RUNNING, NOW);
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        service.completeRun("run-1", 1, "stdout", "Error occurred");

        verify(repo).updateStatus(eq("run-1"), eq(ScriptStatus.FAILED), eq(1),
            eq("Error occurred"), eq("stdout"), eq(0), eq(NOW), eq(0L));
        verify(tokenStore).revokeByRunId("run-1");
    }

    @Test
    void cancelRun_setsCancelledStatus() {
        ScriptRun run = runFixture("run-1", ScriptStatus.RUNNING, NOW);
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        service.cancelRun("run-1");

        verify(repo).updateStatus(eq("run-1"), eq(ScriptStatus.CANCELLED), isNull(),
            eq("Cancelled by user"), isNull(), eq(0), eq(NOW), eq(0L));
        verify(tokenStore).revokeByRunId("run-1");
    }

    @Test
    void cancelRun_preservesExistingRowsWritten() {
        ScriptRun run = new ScriptRun(
            "run-1", "print(1)", ScriptLanguage.PYTHON, ScriptStatus.RUNNING, null, "some stdout",
            "conn-1", null, 42, "script", "ai", "session-1",
            null, NOW, null, null
        );
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        service.cancelRun("run-1");

        verify(repo).updateStatus(eq("run-1"), eq(ScriptStatus.CANCELLED), isNull(),
            eq("Cancelled by user"), eq("some stdout"), eq(42), eq(NOW), eq(0L));
    }

    @Test
    void updateRowsWritten_replacesRowsWrittenValue() {
        ScriptRun run = runFixture("run-1", ScriptStatus.RUNNING, NOW);
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        service.updateRowsWritten("run-1", 100);

        verify(repo).updateStatus(eq("run-1"), eq(ScriptStatus.RUNNING), isNull(), isNull(),
            isNull(), eq(100), isNull(), isNull());
    }

    @Test
    void findById_returnsRun() {
        ScriptRun run = runFixture("run-1", ScriptStatus.RUNNING, NOW);
        when(repo.findById("run-1")).thenReturn(Optional.of(run));

        Optional<ScriptRun> result = service.findById("run-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("run-1");
    }

    @Test
    void findById_returnsEmptyForNonExistent() {
        when(repo.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<ScriptRun> result = service.findById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void listRuns_filtersByConnectionId() {
        ScriptRun run1 = runFixture("run-1", ScriptStatus.COMPLETED, NOW);
        ScriptRun run2 = runFixture("run-2", ScriptStatus.FAILED, NOW.plusSeconds(10));
        when(repo.list("conn-1", 50)).thenReturn(List.of(run2, run1));

        List<ScriptRun> result = service.listRuns("conn-1", 50);

        assertThat(result).hasSize(2);
        verify(repo).list("conn-1", 50);
    }

    @Test
    void listRuns_nullConnectionId_returnsAll() {
        when(repo.list(null, 10)).thenReturn(List.of());

        List<ScriptRun> result = service.listRuns(null, 10);

        assertThat(result).isEmpty();
        verify(repo).list(null, 10);
    }

    @Test
    void tokenStore_returnsTokenStoreInstance() {
        assertThat(service.tokenStore()).isSameAs(tokenStore);
    }

    private static ScriptRun runFixture(String id, ScriptStatus status, Instant startedAt) {
        return new ScriptRun(id, "print(1)", ScriptLanguage.PYTHON, status,
            null, null, "conn-1", null, 0,
            "script", "ai", "session-1",
            null, startedAt, null, null);
    }
}
