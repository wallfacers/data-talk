package com.datatalk.adapter.scheduler;

import com.datatalk.application.persistence.UndoLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UndoLogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UndoLogCleanupScheduler.class);
    private static final long OLD_EXPIRED_CUTOFF_MS = 7L * 24 * 60 * 60 * 1000;

    private final UndoLogRepository undoLogRepo;

    public UndoLogCleanupScheduler(UndoLogRepository undoLogRepo) {
        this.undoLogRepo = undoLogRepo;
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = undoLogRepo.findExpiredActive(now);
        if (!expiredIds.isEmpty()) {
            undoLogRepo.markExpiredBatch(expiredIds);
            log.info("Marked {} undo_log entries as expired", expiredIds.size());
        }
        long cutoff = now - OLD_EXPIRED_CUTOFF_MS;
        undoLogRepo.deleteOldExpired(cutoff);
    }
}
