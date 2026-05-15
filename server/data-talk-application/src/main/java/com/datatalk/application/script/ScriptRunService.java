package com.datatalk.application.script;

import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScriptRunService {

    private final ScriptRunRepository repo;
    private final ScriptTokenStore tokenStore;
    private final Clock clock;

    public ScriptRunService(ScriptRunRepository repo, ScriptTokenStore tokenStore, Clock clock) {
        this.repo = repo;
        this.tokenStore = tokenStore;
        this.clock = clock;
    }

    public record RunPrepareResult(String runId, String token) {}

    public RunPrepareResult prepareRun(String scriptContent, ScriptLanguage language, String connectionId,
                                        String name, String createdByKind, String createdBySessionId) {
        String runId = UUID.randomUUID().toString();
        Instant now = clock.instant();

        ScriptRun run = new ScriptRun(
            runId, scriptContent, language, ScriptStatus.RUNNING, null, null,
            connectionId, null, 0, name, createdByKind, createdBySessionId,
            null, now, null, null
        );
        repo.save(run);

        String token = tokenStore.issue(runId, connectionId);
        return new RunPrepareResult(runId, token);
    }

    public void completeRun(String runId, int exitCode, String stdoutText, String errorMessage) {
        ScriptRun run = repo.findById(runId).orElseThrow();
        Instant now = clock.instant();
        long durationMs = Duration.between(run.startedAt(), now).toMillis();
        ScriptStatus status = exitCode == 0 ? ScriptStatus.COMPLETED : ScriptStatus.FAILED;

        repo.updateStatus(runId, status, exitCode, errorMessage, stdoutText, run.rowsWritten(), now, durationMs);
        tokenStore.revokeByRunId(runId);
    }

    public void cancelRun(String runId) {
        ScriptRun run = repo.findById(runId).orElseThrow();
        Instant now = clock.instant();
        long durationMs = Duration.between(run.startedAt(), now).toMillis();

        repo.updateStatus(runId, ScriptStatus.CANCELLED, null, "Cancelled by user",
            run.stdoutText(), run.rowsWritten(), now, durationMs);
        tokenStore.revokeByRunId(runId);
    }

    public void updateRowsWritten(String runId, int rowsWritten) {
        ScriptRun run = repo.findById(runId).orElseThrow();
        repo.updateStatus(runId, run.status(), run.exitCode(), run.errorMessage(),
            run.stdoutText(), rowsWritten,
            run.finishedAt(),
            run.durationMs()
        );
    }

    public Optional<ScriptRun> findById(String id) {
        return repo.findById(id);
    }

    public List<ScriptRun> listRuns(String connectionId, int limit) {
        return repo.list(connectionId, limit);
    }

    public ScriptTokenStore tokenStore() {
        return tokenStore;
    }
}
