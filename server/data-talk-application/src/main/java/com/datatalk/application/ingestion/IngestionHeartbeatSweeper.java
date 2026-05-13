package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically reconciles jobs whose worker thread has died but the JVM survived
 * (e.g. OOM kills a virtual thread, network adapter died, etc.). A job whose
 * {@code heartbeat_at} is older than {@link #HEARTBEAT_TTL_MS} is presumed dead
 * and flipped to {@code failed}.
 *
 * <p>The target table is intentionally NOT dropped — same reasoning as
 * {@link IngestionStartupSweeper} (see design.md D5).</p>
 */
@Component
public class IngestionHeartbeatSweeper {

    private static final Logger log = LoggerFactory.getLogger(IngestionHeartbeatSweeper.class);

    static final long HEARTBEAT_TTL_MS = 5 * 60 * 1000L;

    static final List<String> ACTIVE_STATUSES = List.of("fetching", "writing");

    static final String REASON = "task heartbeat lost — manually drop target table if needed";

    private final IngestionJobRepository jobRepo;

    public IngestionHeartbeatSweeper(IngestionJobRepository jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void sweepDeadHeartbeats() {
        long now = System.currentTimeMillis();
        long deadline = now - HEARTBEAT_TTL_MS;
        int n = jobRepo.batchFailIfHeartbeatBefore(ACTIVE_STATUSES, deadline, REASON, now);
        if (n > 0) {
            log.warn("Heartbeat sweeper: marked {} ingestion jobs as failed (no heartbeat for {}+ ms)",
                n, HEARTBEAT_TTL_MS);
        }
    }
}
