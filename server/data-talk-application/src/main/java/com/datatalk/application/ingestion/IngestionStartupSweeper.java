package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On every JVM boot, flips any ingestion_job left in a non-terminal in-flight status
 * to {@code failed} with a clear error message. This is the heavy hammer that
 * guarantees no row remains "running" indefinitely across crashes / restarts.
 *
 * <p>The target table is intentionally NOT dropped on this path (see design.md D5)
 * — restart may be unrelated to the ingestion, and silently DROP'ing user data
 * is far worse than leaving partial rows with a clear errorMessage pointer.</p>
 */
@Component
public class IngestionStartupSweeper {

    private static final Logger log = LoggerFactory.getLogger(IngestionStartupSweeper.class);

    static final List<String> NON_TERMINAL_STATUSES = List.of(
        "pending", "fetching", "mapping", "confirmed", "writing");

    static final String REASON = "server restarted while running — manually drop target table if needed";

    private final IngestionJobRepository jobRepo;

    public IngestionStartupSweeper(IngestionJobRepository jobRepo) {
        this.jobRepo = jobRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweepStaleJobs() {
        long now = System.currentTimeMillis();
        int n = jobRepo.batchFailByStatus(NON_TERMINAL_STATUSES, REASON, now);
        if (n > 0) {
            log.info("Startup sweeper: marked {} stale ingestion jobs as failed", n);
        }
    }
}
