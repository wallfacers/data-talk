package com.datatalk.application.housekeeping;

import com.datatalk.application.fileartifact.FileArtifactReconciler;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Nightly housekeeping — 4 independent tasks, 03:00 UTC.
 * Spec §B.1.
 */
@Component
public class HousekeepingScheduler {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingScheduler.class);

    private final FileArtifactReconciler reconciler;
    private final FileArtifactRepository fileArtifactRepo;
    private final Clock clock;
    private final Path workdir;

    public HousekeepingScheduler(
            FileArtifactReconciler reconciler,
            FileArtifactRepository fileArtifactRepo,
            Clock clock) {
        this.reconciler = reconciler;
        this.fileArtifactRepo = fileArtifactRepo;
        this.clock = clock;
        this.workdir = resolveWorkdir();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runNightly() {
        log.info("[housekeeping] starting nightly run");
        Instant started = clock.instant();
        int failed = 0;
        try { rotateOpencodeBackups(); logHousekeepingTask("rotate-backup", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("rotate-backup", "failed", started); log.warn("[housekeeping] rotate-backup failed: {}", e.toString()); }
        try { rotateOpencodeLogs(); logHousekeepingTask("rotate-log", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("rotate-log", "failed", started); log.warn("[housekeeping] rotate-log failed: {}", e.toString()); }
        try { cleanupTrash(); logHousekeepingTask("cleanupTrash", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("cleanupTrash", "failed", started); log.warn("[housekeeping] cleanupTrash failed: {}", e.toString()); }
        try { reconcileFileArtifacts(); logHousekeepingTask("reconcile", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("reconcile", "failed", started); log.warn("[housekeeping] reconcile failed: {}", e.toString()); }
        writeHousekeepingLog(started, 4 - failed, failed);
        log.info("[housekeeping] completed in {} ms", clock.instant().toEpochMilli() - started.toEpochMilli());
    }

    private void writeHousekeepingLog(Instant started, int tasksRun, int tasksFailed) {
        Path logFile = workdir.resolve("housekeeping.log");
        try {
            String entry = String.format(
                    "{\"ts\":\"%s\",\"tasksRun\":%d,\"tasksFailed\":%d,\"durationMs\":%d}%n",
                    started.toString(), tasksRun, tasksFailed,
                    clock.instant().toEpochMilli() - started.toEpochMilli());
            Files.writeString(logFile, entry, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("[housekeeping] failed to write log: {}", e.toString());
        }
    }

    private void logHousekeepingTask(String task, String result, Instant started) {
        Path logFile = workdir.resolve("housekeeping.log");
        try {
            String entry = String.format(
                    "{\"ts\":\"%s\",\"task\":\"%s\",\"result\":\"%s\"}%n",
                    clock.instant().toString(), task, result);
            Files.writeString(logFile, entry, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // best effort
        }
    }

    void rotateOpencodeBackups() {
        Path dir = workdir.resolve("opencode");
        rotateByPattern(dir, "opencode.json.dt-bak-", 5, 7, "rotate-backup");
    }

    void rotateOpencodeLogs() {
        Path dir = Path.of(System.getProperty("user.home"))
                .resolve(".local/share/opencode/log");
        rotateByPattern(dir, ".log", 5, 7, "rotate-log");
    }

    public void cleanupTrash() {
        Path trashDir = workdir.resolve("_trash");
        if (!Files.isDirectory(trashDir)) return;

        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(7));
        int removed = 0;
        try (Stream<Path> walk = Files.list(trashDir)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;
                BasicFileAttributes attrs;
                try { attrs = Files.readAttributes(file, BasicFileAttributes.class); }
                catch (IOException e) { log.warn("[housekeeping] trash stat failed: {}", file); continue; }
                if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                    String filename = file.getFileName().toString();
                    String fid = extractFidFromTrashName(filename);
                    try { Files.deleteIfExists(file); }
                    catch (IOException e) { log.warn("[housekeeping] trash rm failed: {}", file); continue; }
                    if (fid != null) {
                        try {
                            fileArtifactRepo.deleteDiscardedById(fid);
                        } catch (Exception e) {
                            log.warn("[housekeeping] trash db delete failed for fid={}: {}", fid, e.toString());
                        }
                    }
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("[housekeeping] trash walk failed: {}", e.toString());
        }
        log.info("[housekeeping] cleanupTrash removed {} files", removed);
    }

    void reconcileFileArtifacts() {
        reconciler.runFullReconcile();
    }

    // --- internals ---

    private void rotateByPattern(Path dir, String pattern, int keepRecent, int keepDays, String label) {
        if (!Files.isDirectory(dir)) return;

        Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(keepDays));
        try (Stream<Path> walk = Files.list(dir)) {
            List<Path> matches = walk
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().contains(pattern))
                    .sorted(Comparator.comparingLong(this::mtimeReverse).reversed())
                    .toList();

            int removed = 0;
            for (int i = 0; i < matches.size(); i++) {
                Path p = matches.get(i);
                long mtime = mtimeReverse(p);
                Instant modTime = Instant.ofEpochMilli(mtime);
                boolean inRecent = i < keepRecent;
                boolean inDays = !modTime.isBefore(cutoff);
                if (inRecent || inDays) continue;
                try { Files.deleteIfExists(p); removed++; }
                catch (IOException e) { log.warn("[housekeeping] {} rm failed: {}", label, p); }
            }
            log.info("[housekeeping] {} removed {} files", label, removed);
        } catch (IOException e) {
            log.warn("[housekeeping] {} walk failed: {}", label, e.toString());
        }
    }

    private long mtimeReverse(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException e) { return 0; }
    }

    private String extractFidFromTrashName(String filename) {
        // format: <connId-or-sid>__<fid>__<filename>
        int first = filename.indexOf("__");
        if (first < 0) return null;
        int second = filename.indexOf("__", first + 2);
        if (second < 0) return null;
        return filename.substring(first + 2, second);
    }

    private Path resolveWorkdir() {
        Path defaultPath = Path.of(System.getProperty("user.home")).resolve(".data-talk");
        String env = System.getenv("DATA_TALK_WORKDIR");
        if (env != null) return Path.of(env);
        String prop = System.getProperty("DATA_TALK_WORKDIR");
        return prop != null ? Path.of(prop) : defaultPath;
    }
}
