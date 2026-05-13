package com.datatalk.application.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.ddl.IngestionDdlAdapter;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.IngestionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates user-initiated stop of an ingestion job:
 * <ol>
 *   <li>Reject if already terminal.</li>
 *   <li>Signal the in-process worker via {@link IngestionRunRegistry} (if any).</li>
 *   <li>Best-effort cleanup: DROP TABLE (if {@code status ≥ confirmed}) and delete payload artifact.</li>
 *   <li>Flip the DB status to {@code cancelled} with an informative errorMessage.</li>
 * </ol>
 *
 * <p>{@code force=true} bypasses the registry presence check so cross-process zombies
 * (DB still says {@code writing}, but no worker is here) can be reclaimed.</p>
 */
@Service
public class IngestionStopService {

    private static final Logger log = LoggerFactory.getLogger(IngestionStopService.class);

    private static final Set<String> TERMINAL = Set.of("completed", "failed", "cancelled");
    private static final Set<String> STATUSES_WITH_TABLE = Set.of("confirmed", "writing");

    private final IngestionJobRepository jobRepo;
    private final IngestionRunRegistry runRegistry;
    private final FileArtifactRepository artifactRepo;
    private final ConnectionRepository connRepo;
    private final ConnectionService connService;
    private final List<IngestionDdlAdapter> adapters;

    public IngestionStopService(IngestionJobRepository jobRepo,
                                IngestionRunRegistry runRegistry,
                                FileArtifactRepository artifactRepo,
                                ConnectionRepository connRepo,
                                ConnectionService connService,
                                List<IngestionDdlAdapter> adapters) {
        this.jobRepo = jobRepo;
        this.runRegistry = runRegistry;
        this.artifactRepo = artifactRepo;
        this.connRepo = connRepo;
        this.connService = connService;
        this.adapters = adapters;
    }

    public enum StopOutcome {
        NOT_FOUND,         // 404
        ALREADY_TERMINAL,  // 409
        SIGNALLED,         // 202 — async, worker will exit at the next batch boundary
        APPLIED            // 204 — DB + cleanup completed synchronously
    }

    public StopOutcome stop(String jobId, boolean force) {
        Optional<IngestionJob> opt = jobRepo.findById(jobId);
        if (opt.isEmpty()) return StopOutcome.NOT_FOUND;
        IngestionJob job = opt.get();
        if (TERMINAL.contains(job.status())) return StopOutcome.ALREADY_TERMINAL;

        boolean signalled = runRegistry.requestStop(jobId);
        if (signalled && !force) {
            // The active worker will rollback its batch, throw IngestionCancelledException,
            // and exit. We finalize the row + cleanup so the UI does not have to wait.
            finalizeCancellation(job, "stopped by user (active worker signalled)");
            return StopOutcome.SIGNALLED;
        }

        finalizeCancellation(job, force ? "stopped by user (force)" : "stopped by user");
        return StopOutcome.APPLIED;
    }

    private void finalizeCancellation(IngestionJob job, String reasonPrefix) {
        long now = System.currentTimeMillis();
        StringBuilder errorMessage = new StringBuilder(reasonPrefix);

        // 1. DROP target table if it has been created
        if (STATUSES_WITH_TABLE.contains(job.status())
            && job.connectionId() != null && job.targetTable() != null) {
            try {
                dropTargetTable(job);
            } catch (Exception e) {
                log.warn("Failed to DROP target table for cancelled job {}: {}",
                    job.id(), e.getMessage());
                errorMessage.append(" — table cleanup failed: ").append(e.getMessage());
            }
        }

        // 2. Delete payload artifact (best-effort)
        if (job.payloadArtifactId() != null) {
            try {
                deletePayloadArtifact(job.payloadArtifactId());
            } catch (Exception e) {
                log.warn("Failed to delete payload artifact for cancelled job {}: {}",
                    job.id(), e.getMessage());
            }
        }

        // 3. Flip DB status — last, so partial cleanup is reflected in errorMessage
        jobRepo.updateStatus(job.id(), "cancelled", errorMessage.toString(), now);
    }

    private void dropTargetTable(IngestionJob job) {
        ConnectionRecord cr = connRepo.findById(job.connectionId())
            .orElseThrow(() -> new IllegalStateException(
                "target connection not found: " + job.connectionId()));
        IngestionDdlAdapter adapter = adapters.stream()
            .filter(a -> a.supports(cr.kind()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "no DDL adapter for connection kind: " + cr.kind()));
        String dropSql = adapter.generateDropTable(job.targetSchema(), job.targetTable());
        String url = JdbcUrlBuilder.build(cr);
        String password = connService.decryptPassword(cr.id());
        try (Connection conn = DriverManager.getConnection(url, cr.username(), password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(dropSql);
            log.info("DROP TABLE executed for cancelled job {}: {}", job.id(), dropSql);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void deletePayloadArtifact(String artifactId) {
        Optional<FileArtifact> opt = artifactRepo.findById(artifactId);
        if (opt.isEmpty()) return;
        try {
            Path p = Path.of(opt.get().physicalPath());
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
            // physical file may already be gone — DB row cleanup below is the source of truth
        }
        artifactRepo.deleteById(artifactId);
    }
}
