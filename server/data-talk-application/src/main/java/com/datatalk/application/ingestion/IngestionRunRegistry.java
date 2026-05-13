package com.datatalk.application.ingestion;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks in-process active ingestion jobs so {@link IngestionStopService} can
 * cooperatively interrupt their worker threads.
 *
 * <p>Single-process scope only — multi-node deployments would need a distributed
 * variant (see design.md D4 / D8). Restart-time consistency is handled by
 * {@code IngestionStartupSweeper}, not this registry.</p>
 */
@Service
public class IngestionRunRegistry {

    private final ConcurrentHashMap<String, RunHandle> active = new ConcurrentHashMap<>();

    public record RunHandle(Thread worker, AtomicBoolean cancelled) {}

    /**
     * Auto-closeable scope handle. Use with try-with-resources around the synchronous
     * fetch / write call so the registry entry is removed on every exit path.
     */
    public final class RegistryEntry implements AutoCloseable {
        private final String jobId;
        RegistryEntry(String jobId) { this.jobId = jobId; }
        @Override public void close() { active.remove(jobId); }
    }

    /**
     * Registers the current thread as the worker for {@code jobId}.
     * Returns an {@link RegistryEntry} the caller MUST close (try-with-resources).
     */
    public RegistryEntry register(String jobId) {
        active.put(jobId, new RunHandle(Thread.currentThread(), new AtomicBoolean(false)));
        return new RegistryEntry(jobId);
    }

    /**
     * Signals the cancellation flag and interrupts the worker thread.
     * No-op if the job is not currently active (caller should fall back to a
     * forced DB status flip in that case — handled by {@link IngestionStopService}).
     *
     * @return true if a worker was signalled
     */
    public boolean requestStop(String jobId) {
        RunHandle h = active.get(jobId);
        if (h == null) return false;
        h.cancelled.set(true);
        h.worker.interrupt();
        return true;
    }

    public boolean isActive(String jobId) {
        return active.containsKey(jobId);
    }

    /**
     * Returns the cancel flag for the given job, or {@code null} if the job is not
     * currently registered. Workers should poll {@code .get()} at safe boundaries.
     */
    public AtomicBoolean getCancelled(String jobId) {
        RunHandle h = active.get(jobId);
        return h != null ? h.cancelled : null;
    }
}
