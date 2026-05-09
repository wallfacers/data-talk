package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reconciles file_artifact rows with files under sessions/* and archived workspace rows.
 */
@Component
public class FileArtifactReconciler {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactReconciler.class);
    private static final Set<String> IGNORED_SUFFIXES = Set.of(".tmp", ".swp", ".partial", ".swo");

    private final FileArtifactRepository repo;
    private final FileArtifactService artifactService;
    private final SessionWorkdirService workdir;
    private final SessionBusRegistry buses;
    private final SessionRepository sessions;

    public FileArtifactReconciler(
            FileArtifactRepository repo,
            FileArtifactService artifactService,
            SessionWorkdirService workdir,
            SessionBusRegistry buses,
            SessionRepository sessions) {
        this.repo = repo;
        this.artifactService = artifactService;
        this.workdir = workdir;
        this.buses = buses;
        this.sessions = sessions;
    }

    public synchronized void runFullReconcile() {
        Set<String> knownSessionIds = knownSessionIds();
        reconcileSessionsTree(knownSessionIds);
        reconcileWorkspacesTree(knownSessionIds);
        reconcileExternalDirs(workdir.root().externalManagedRoots());
    }

    private void reconcileSessionsTree(Set<String> knownSessionIds) {
        Path sessionsRoot = canonicalDirectory(workdir.root().sessionsRoot());
        if (sessionsRoot == null) {
            return;
        }
        if (!Files.isDirectory(sessionsRoot)) {
            return;
        }

        List<FileArtifact> rows = repo.findAllSessionScoped();
        Map<String, FileArtifact> byPath = rows.stream()
                .collect(Collectors.toMap(FileArtifact::physicalPath, row -> row, (left, right) -> {
                    log.warn("duplicate file_artifact physical_path row {} vs {}", left.id(), right.id());
                    return left;
                }));
        Set<String> seen = new HashSet<>();

        try (Stream<Path> walk = Files.walk(sessionsRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!isAdoptableFile(path, sessionsRoot)) {
                    continue;
                }
                String absolutePath = canonicalFilePath(path);
                seen.add(absolutePath);
                if (byPath.containsKey(absolutePath)) {
                    continue;
                }
                String sessionId = sessionIdOf(path, sessionsRoot);
                if (sessionId != null) {
                    if (!knownSessionIds.contains(sessionId)) {
                        log.info("orphan_session_dir {}", sessionId);
                        continue;
                    }
                    artifactService.recordDetected(sessionId, path, FrontmatterParser.parse(path));
                }
            }
        } catch (IOException e) {
            log.warn("file artifact reconcile failed walking sessions root {}: {}", sessionsRoot, e.toString());
        }

        for (FileArtifact row : rows) {
            if (seen.contains(row.physicalPath()) || Files.exists(Path.of(row.physicalPath()))) {
                continue;
            }
            switch (row.status()) {
                case TEMPORARY, DISCARDED -> repo.deleteById(row.id());
                case CANDIDATE -> {
                    repo.deleteById(row.id());
                    publishDiscarded(row, "reconcile", knownSessionIds);
                }
                case ARCHIVED -> log.warn(
                        "file_artifact {} archived row unexpectedly appears in session scope and is missing: {}",
                        row.id(),
                        row.physicalPath());
            }
        }
    }

    public void reconcileTrash() {
        Path trashRoot = workdir.root().trashRoot();
        if (!Files.isDirectory(trashRoot)) return;

        // FS orphans: files in _trash with no DB discarded row
        try (Stream<Path> walk = Files.list(trashRoot)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String path = file.toAbsolutePath().normalize().toString();
                if (repo.findByPhysicalPath(path).isEmpty()) {
                    try { Files.deleteIfExists(file); }
                    catch (IOException e) { log.warn("[reconcile-trash] rm failed: {}", file); }
                }
            }
        } catch (IOException e) {
            log.warn("[reconcile-trash] walk failed: {}", e.toString());
        }

        // DB orphans: discarded rows where file doesn't exist
        for (FileArtifact row : repo.findAllSessionScoped()) {
            if (row.status() != com.datatalk.domain.fileartifact.FileArtifactStatus.DISCARDED) continue;
            if (!Files.exists(Path.of(row.physicalPath()))) {
                repo.deleteById(row.id());
                log.info("[reconcile-trash] removing stale discarded row {} (file missing)", row.id());
            }
        }
    }

    private void reconcileWorkspacesTree(Set<String> knownSessionIds) {
        for (FileArtifact row : repo.findAllWorkspaceScopedArchived()) {
            if (row.connectionId() == null) {
                // Q2 decision: orphaned archived — valid state, skip
                continue;
            }
            if (row.external()) continue;
            if (Files.exists(Path.of(row.physicalPath()))) {
                continue;
            }
            log.warn("file_artifact {} archived file missing: {}", row.id(), row.physicalPath());
            repo.deleteById(row.id());
            publishDiscarded(row, "reconcile", knownSessionIds);
        }
    }

    public void reconcileExternalDirs(List<Path> dirs) {
        Set<String> knownSessionIds = knownSessionIds();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;

            // Forward: files without DB row → delete file
            try (Stream<Path> walk = Files.list(dir)) {
                for (Path file : (Iterable<Path>) walk::iterator) {
                    if (!Files.isRegularFile(file)) continue;
                    String fname = file.getFileName().toString();
                    if (fname.startsWith(".") || fname.endsWith(".tmp")) continue;
                    String absolutePath = file.toAbsolutePath().normalize().toString();
                    if (repo.findByPhysicalPath(absolutePath).isEmpty()) {
                        try {
                            Files.deleteIfExists(file);
                            log.info("[reconcile-external] removed orphan file {}", absolutePath);
                        } catch (IOException e) {
                            log.warn("[reconcile-external] rm failed: {}", file, e);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("[reconcile-external] walk failed for {}: {}", dir, e.toString());
            }

            // Reverse: DB external row without file → delete row
            for (FileArtifact row : repo.findExternalRowsByDir(dir.toAbsolutePath().normalize().toString())) {
                if (Files.exists(Path.of(row.physicalPath()))) continue;
                log.warn("[reconcile-external] external row {} missing physical {}", row.id(), row.physicalPath());
                repo.deleteById(row.id());
                publishDiscarded(row, "reconcile-external", knownSessionIds);
            }
        }
    }

    private void publishDiscarded(FileArtifact row, String reason, Set<String> knownSessionIds) {
        if (row.sessionId() == null || !knownSessionIds.contains(row.sessionId())) {
            return;
        }
        try {
            buses.getOrCreate(row.sessionId()).publish(new DtEvent.FileArtifactDiscarded(row.id(), reason));
        } catch (Exception e) {
            log.debug("file artifact reconcile publish failed for {}: {}", row.id(), e.toString());
        }
    }

    private static boolean isAdoptableFile(Path path, Path sessionsRoot) {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            return false;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (name.isBlank() || name.startsWith(".")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : IGNORED_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return false;
            }
        }
        return sessionIdOf(path, sessionsRoot) != null;
    }

    private static String sessionIdOf(Path path, Path sessionsRoot) {
        Path relative = sessionsRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        if (relative.getNameCount() < 2) {
            return null;
        }
        String sessionId = relative.getName(0).toString();
        return sessionId.startsWith("_") || sessionId.startsWith(".") ? null : sessionId;
    }

    private Set<String> knownSessionIds() {
        try {
            return sessions.listAll().stream()
                    .map(SessionRecord::id)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("file artifact reconcile failed loading sessions: {}", e.toString());
            return Set.of();
        }
    }

    private static Path canonicalDirectory(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try {
            return root.toRealPath();
        } catch (IOException e) {
            return root.toAbsolutePath().normalize();
        }
    }

    private static String canonicalFilePath(Path path) {
        try {
            return path.toRealPath().toString();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }
}
