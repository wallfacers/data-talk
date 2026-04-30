package com.datatalk.application.fileartifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filters, debounces, and dispatches filesystem watcher events into artifact use cases.
 */
@Service
public class ArtifactWatcherService implements AutoCloseable {

    static final long DEBOUNCE_MS = 200L;

    private static final Logger log = LoggerFactory.getLogger(ArtifactWatcherService.class);
    private static final Set<String> IGNORED_SUFFIXES = Set.of(".tmp", ".swp", ".partial", ".swo");

    private final ArtifactWatcher watcher;
    private final FileArtifactService artifactService;
    private final SessionWorkdirService workdir;
    private final FileArtifactReconciler reconciler;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<DebounceKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private volatile boolean started;

    public ArtifactWatcherService(
            ArtifactWatcher watcher,
            FileArtifactService artifactService,
            SessionWorkdirService workdir,
            FileArtifactReconciler reconciler) {
        this.watcher = watcher;
        this.artifactService = artifactService;
        this.workdir = workdir;
        this.reconciler = reconciler;
        AtomicInteger seq = new AtomicInteger();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "file-artifact-watcher-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        Path sessionsRoot = workdir.root().sessionsRoot();
        try {
            Files.createDirectories(sessionsRoot);
        } catch (IOException e) {
            throw new RuntimeException("failed to create sessions root: " + sessionsRoot, e);
        }
        watcher.start(sessionsRoot, this::onEvent);
        started = true;
    }

    void onEvent(FileWatchEvent event) {
        switch (event) {
            case FileWatchEvent.Create create -> debounce(create.path(), DispatchKind.CREATE);
            case FileWatchEvent.Modify modify -> debounce(modify.path(), DispatchKind.MODIFY);
            case FileWatchEvent.Delete delete -> debounce(delete.path(), DispatchKind.DELETE);
            case FileWatchEvent.Rename rename -> {
                debounce(rename.previousPath(), DispatchKind.DELETE);
                debounce(rename.path(), DispatchKind.CREATE);
            }
            case FileWatchEvent.Overflow ignored -> scheduler.execute(reconciler::runFullReconcile);
        }
    }

    @Override
    public synchronized void close() {
        if (!started) {
            return;
        }
        started = false;
        pending.values().forEach(future -> future.cancel(false));
        pending.clear();
        watcher.close();
        scheduler.shutdownNow();
    }

    private void debounce(Path path, DispatchKind kind) {
        DebounceKey key = new DebounceKey(path.toAbsolutePath().normalize(), kind);
        ScheduledFuture<?> previous = pending.put(
                key,
                scheduler.schedule(() -> dispatch(key), DEBOUNCE_MS, TimeUnit.MILLISECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void dispatch(DebounceKey key) {
        pending.remove(key);
        Path path = key.path();
        if (!isPathRelevant(path)) {
            return;
        }
        String sessionId = inferSessionId(path);
        if (sessionId == null) {
            return;
        }
        try {
            switch (key.kind()) {
                case CREATE -> {
                    if (!Files.exists(path) || !isStable(path)) {
                        if (Files.exists(path)) {
                            debounce(path, DispatchKind.CREATE);
                        }
                        return;
                    }
                    Map<String, String> frontmatter = FrontmatterParser.parse(path);
                    artifactService.recordDetected(sessionId, path, frontmatter);
                }
                case MODIFY -> {
                    if (!Files.exists(path) || !isStable(path)) {
                        if (Files.exists(path)) {
                            debounce(path, DispatchKind.MODIFY);
                        }
                        return;
                    }
                    Map<String, String> frontmatter = FrontmatterParser.parse(path);
                    artifactService.recordModified(path, frontmatter);
                }
                case DELETE -> artifactService.recordDeleted(path);
            }
        } catch (Exception e) {
            log.warn("file artifact watcher dispatch failed for {} {}: {}", key.kind(), path, e.toString());
        }
    }

    private boolean isPathRelevant(Path path) {
        Path sessionsRoot = workdir.root().sessionsRoot().toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        if (!abs.startsWith(sessionsRoot)) {
            return false;
        }
        Path fileName = abs.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (name.isBlank() || name.startsWith(".") || name.endsWith(".meta.json")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : IGNORED_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    String inferSessionId(Path path) {
        Path sessionsRoot = workdir.root().sessionsRoot().toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        if (!abs.startsWith(sessionsRoot)) {
            return null;
        }
        Path relative = sessionsRoot.relativize(abs);
        if (relative.getNameCount() < 2) {
            return null;
        }
        String sessionId = relative.getName(0).toString();
        return sessionId.startsWith("_") || sessionId.startsWith(".") ? null : sessionId;
    }

    private static boolean isStable(Path path) {
        try {
            long ageMillis = System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis();
            return ageMillis >= DEBOUNCE_MS;
        } catch (IOException e) {
            return false;
        }
    }

    private enum DispatchKind {
        CREATE,
        MODIFY,
        DELETE
    }

    private record DebounceKey(Path path, DispatchKind kind) {
    }
}
