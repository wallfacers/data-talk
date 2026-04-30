package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.ArtifactWatcher;
import com.datatalk.application.fileartifact.FileWatchEvent;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Production filesystem watcher backed by io.methvin DirectoryWatcher.
 */
public class MethvinArtifactWatcher implements ArtifactWatcher {

    private static final Logger log = LoggerFactory.getLogger(MethvinArtifactWatcher.class);

    private DirectoryWatcher watcher;
    private CompletableFuture<Void> watchFuture;
    private ExecutorService executor;
    private boolean started;

    @Override
    public synchronized void start(Path rootPath, Consumer<FileWatchEvent> listener) {
        if (started) {
            throw new IllegalStateException("artifact watcher already started");
        }
        try {
            Files.createDirectories(rootPath);
            AtomicInteger seq = new AtomicInteger();
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "artifact-directory-watcher-" + seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            watcher = DirectoryWatcher.builder()
                    .path(rootPath)
                    .fileHashing(false)
                    .listener(event -> dispatch(rootPath, event, listener))
                    .build();
            watchFuture = watcher.watchAsync(executor);
            started = true;
        } catch (IOException e) {
            close();
            throw new RuntimeException("failed to start artifact watcher on " + rootPath, e);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    private static void dispatch(Path rootPath, DirectoryChangeEvent event, Consumer<FileWatchEvent> listener) {
        Path path = event.path() == null ? rootPath : event.path();
        if (event.isDirectory() || Files.isSymbolicLink(path)) {
            return;
        }
        Instant observedAt = Instant.now();
        switch (event.eventType()) {
            case CREATE -> listener.accept(new FileWatchEvent.Create(path, observedAt));
            case MODIFY -> listener.accept(new FileWatchEvent.Modify(path, observedAt));
            case DELETE -> listener.accept(new FileWatchEvent.Delete(path, observedAt));
            case OVERFLOW -> listener.accept(new FileWatchEvent.Overflow(rootPath, observedAt));
        }
    }

    @Override
    public synchronized void close() {
        if (!started && watcher == null && executor == null && watchFuture == null) {
            return;
        }
        started = false;
        try {
            if (watcher != null) {
                watcher.close();
            }
        } catch (IOException e) {
            log.warn("failed to close artifact watcher: {}", e.toString());
        } finally {
            watcher = null;
        }
        if (watchFuture != null) {
            watchFuture.cancel(true);
            watchFuture = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
