package com.datatalk.application.fileartifact;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts artifact reconcile and watcher after the Spring container is ready.
 */
@Component
public class FileArtifactWatcherStartup {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactWatcherStartup.class);

    private final ArtifactWatcherService watcherService;
    private final FileArtifactReconciler reconciler;

    public FileArtifactWatcherStartup(ArtifactWatcherService watcherService, FileArtifactReconciler reconciler) {
        this.watcherService = watcherService;
        this.reconciler = reconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            reconciler.runFullReconcile();
        } catch (Exception e) {
            log.warn("file artifact startup reconcile failed: {}", e.toString());
        }
        try {
            watcherService.start();
        } catch (Exception e) {
            log.error("file artifact watcher startup failed: {}", e.toString(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        watcherService.close();
    }
}
