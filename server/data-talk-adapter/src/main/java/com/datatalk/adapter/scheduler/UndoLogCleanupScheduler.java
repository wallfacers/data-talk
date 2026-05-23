package com.datatalk.adapter.scheduler;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.application.sql.UndoLogStatusChangedEvent;
import com.datatalk.domain.undo.UndoLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UndoLogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UndoLogCleanupScheduler.class);
    private static final long OLD_EXPIRED_CUTOFF_MS = 7L * 24 * 60 * 60 * 1000;

    private final UndoLogRepository undoLogRepo;
    private final ApplicationEventPublisher eventPublisher;

    public UndoLogCleanupScheduler(UndoLogRepository undoLogRepo, ApplicationEventPublisher eventPublisher) {
        this.undoLogRepo = undoLogRepo;
        this.eventPublisher = eventPublisher;
    }

    // initialDelay skips the first run during the startup window when the
    // datatalk-sqlite pool is being lazily initialized and the undo_log table
    // may not yet exist (Flyway migration runs at ApplicationReadyEvent).
    // See openspec/changes/backend-startup-fast-path/design.md.
    @Scheduled(fixedDelay = 86_400_000, initialDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = undoLogRepo.findExpiredActive(now);
        if (!expiredIds.isEmpty()) {
            List<UndoLogEntry> entries = undoLogRepo.findAllById(expiredIds);
            undoLogRepo.markExpiredBatch(expiredIds);
            log.info("Marked {} undo_log entries as expired", expiredIds.size());
            for (UndoLogEntry entry : entries) {
                eventPublisher.publishEvent(new UndoLogStatusChangedEvent(this, entry.id(), entry.connectionId(), "expired", null));
            }
        }
        long cutoff = now - OLD_EXPIRED_CUTOFF_MS;
        undoLogRepo.deleteOldExpired(cutoff);
    }
}
