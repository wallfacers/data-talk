package com.datatalk.application.housekeeping;

import com.datatalk.application.fileartifact.FileArtifactReconciler;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.semantic.SemanticModelLoader;
import com.datatalk.application.semantic.SemanticModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Nightly housekeeping — 7 independent tasks, 03:00 UTC.
 * Spec §B.1 + semantic model compaction/pending/trash.
 */
@Component
public class HousekeepingScheduler {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingScheduler.class);

    private final FileArtifactReconciler reconciler;
    private final FileArtifactRepository fileArtifactRepo;
    private final SemanticModelRepository semanticRepo;
    private final SemanticModelLoader semanticLoader;
    private final Clock clock;
    private final Path workdir;

    public HousekeepingScheduler(
            FileArtifactReconciler reconciler,
            FileArtifactRepository fileArtifactRepo,
            SemanticModelRepository semanticRepo,
            SemanticModelLoader semanticLoader,
            Clock clock) {
        this.reconciler = reconciler;
        this.fileArtifactRepo = fileArtifactRepo;
        this.semanticRepo = semanticRepo;
        this.semanticLoader = semanticLoader;
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
        try { compactPatches(); logHousekeepingTask("compactPatches", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("compactPatches", "failed", started); log.warn("[housekeeping] compactPatches failed: {}", e.toString()); }
        try { cleanupExpiredPending(); logHousekeepingTask("cleanupExpiredPending", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("cleanupExpiredPending", "failed", started); log.warn("[housekeeping] cleanupExpiredPending failed: {}", e.toString()); }
        try { cleanupSemanticTrash(); logHousekeepingTask("cleanupSemanticTrash", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("cleanupSemanticTrash", "failed", started); log.warn("[housekeeping] cleanupSemanticTrash failed: {}", e.toString()); }
        writeHousekeepingLog(started, 7 - failed, failed);
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

    public synchronized int cleanupTrash() {
        return cleanupTrashOlderThan(java.time.Duration.ofDays(7));
    }

    /** User-triggered immediate cleanup — ignores the 7-day retention window. */
    public synchronized int cleanupTrashNow() {
        return cleanupTrashOlderThan(java.time.Duration.ZERO);
    }

    private int cleanupTrashOlderThan(java.time.Duration minAge) {
        Path trashDir = workdir.resolve("_trash");
        if (!Files.isDirectory(trashDir)) return 0;

        Instant cutoff = clock.instant().minus(minAge);
        int removed = 0;
        try (Stream<Path> walk = Files.list(trashDir)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;
                BasicFileAttributes attrs;
                try { attrs = Files.readAttributes(file, BasicFileAttributes.class); }
                catch (IOException e) { log.warn("[housekeeping] trash stat failed: {}", file); continue; }
                if (!attrs.lastModifiedTime().toInstant().isAfter(cutoff)) {
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
        log.info("[housekeeping] cleanupTrash (minAge={}) removed {} files", minAge, removed);
        return removed;
    }

    void reconcileFileArtifacts() {
        reconciler.runFullReconcile();
    }

    /** User-triggered immediate cleanup of the _legacy archive directory (recursive). */
    public synchronized int cleanupLegacyNow() {
        Path legacyDir = workdir.resolve("_legacy");
        if (!Files.isDirectory(legacyDir)) return 0;
        int removed = 0;
        try (Stream<Path> walk = Files.walk(legacyDir)) {
            List<Path> entries = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : entries) {
                if (p.equals(legacyDir)) continue;
                boolean isFile = Files.isRegularFile(p);
                try {
                    Files.deleteIfExists(p);
                    if (isFile) removed++;
                } catch (IOException e) {
                    log.warn("[housekeeping] legacy rm failed: {}", p);
                }
            }
        } catch (IOException e) {
            log.warn("[housekeeping] legacy walk failed: {}", e.toString());
        }
        log.info("[housekeeping] cleanupLegacyNow removed {} files", removed);
        return removed;
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

    /** Compact patches.jsonl files with > 200 lines back into the model yaml. */
    synchronized int compactPatches() {
        Path semanticDir = workdir.resolve("semantic");
        if (!Files.isDirectory(semanticDir)) return 0;
        int compacted = 0;
        try (Stream<Path> connDirs = Files.list(semanticDir)) {
            for (Path connDir : (Iterable<Path>) connDirs::iterator) {
                if (!Files.isDirectory(connDir)) continue;
                String connectionId = connDir.getFileName().toString();
                try (Stream<Path> files = Files.list(connDir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".patches.jsonl")) continue;
                        String domain = name.substring(0, name.length() - ".patches.jsonl".length());
                        List<String> lines = Files.readAllLines(file);
                        if (lines.size() > 200) {
                            var model = semanticRepo.loadDomain(connectionId, domain);
                            if (model.isPresent()) {
                                semanticLoader.compact(connectionId, domain, model.get());
                                compacted++;
                                log.info("[housekeeping] compacted patches for {}/{} ({} lines)", connectionId, domain, lines.size());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[housekeeping] compactPatches failed: {}", e.toString());
        }
        return compacted;
    }

    /** Move pending proposals older than 30 days to _trash. */
    synchronized int cleanupExpiredPending() {
        Path semanticDir = workdir.resolve("semantic");
        if (!Files.isDirectory(semanticDir)) return 0;
        Instant cutoff = clock.instant().minus(Duration.ofDays(30));
        int cleaned = 0;
        try (Stream<Path> connDirs = Files.list(semanticDir)) {
            for (Path connDir : (Iterable<Path>) connDirs::iterator) {
                Path pendingDir = connDir.resolve("pending");
                if (!Files.isDirectory(pendingDir)) continue;
                try (Stream<Path> pendingFiles = Files.list(pendingDir)) {
                    for (Path pf : (Iterable<Path>) pendingFiles::iterator) {
                        BasicFileAttributes attrs;
                        try { attrs = Files.readAttributes(pf, BasicFileAttributes.class); }
                        catch (IOException e) { continue; }
                        if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                            Path trashTarget = workdir.resolve("_trash/semantic")
                                .resolve(attrs.lastModifiedTime().toInstant().toEpochMilli() + "-expired-" + pf.getFileName().toString());
                            Files.createDirectories(trashTarget.getParent());
                            Files.move(pf, trashTarget);
                            cleaned++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[housekeeping] cleanupExpiredPending failed: {}", e.toString());
        }
        if (cleaned > 0) log.info("[housekeeping] cleanupExpiredPending moved {} expired pending files", cleaned);
        return cleaned;
    }

    /** Physically delete _trash/semantic/<ts>-* directories older than 30 days. */
    synchronized int cleanupSemanticTrash() {
        Path trashSemanticDir = workdir.resolve("_trash/semantic");
        if (!Files.isDirectory(trashSemanticDir)) return 0;
        Instant cutoff = clock.instant().minus(Duration.ofDays(30));
        int removed = 0;
        try (Stream<Path> entries = Files.list(trashSemanticDir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                BasicFileAttributes attrs;
                try { attrs = Files.readAttributes(entry, BasicFileAttributes.class); }
                catch (IOException e) { continue; }
                if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                    deleteRecursively(entry);
                    removed++;
                }
            }
        } catch (IOException e) {
            log.warn("[housekeeping] cleanupSemanticTrash failed: {}", e.toString());
        }
        if (removed > 0) log.info("[housekeeping] cleanupSemanticTrash removed {} directories", removed);
        return removed;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    deleteRecursively(file);
                }
            }
        }
        Files.deleteIfExists(dir);
    }

    private Path resolveWorkdir() {
        Path defaultPath = Path.of(System.getProperty("user.home")).resolve(".data-talk");
        String env = System.getenv("DATA_TALK_WORKDIR");
        if (env != null) return Path.of(env);
        String prop = System.getProperty("DATA_TALK_WORKDIR");
        return prop != null ? Path.of(prop) : defaultPath;
    }
}
