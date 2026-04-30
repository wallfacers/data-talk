package com.datatalk.application.fileartifact;

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

    public FileArtifactReconciler(
            FileArtifactRepository repo,
            FileArtifactService artifactService,
            SessionWorkdirService workdir,
            SessionBusRegistry buses) {
        this.repo = repo;
        this.artifactService = artifactService;
        this.workdir = workdir;
        this.buses = buses;
    }

    public synchronized void runFullReconcile() {
        reconcileSessionsTree();
        reconcileWorkspacesTree();
    }

    private void reconcileSessionsTree() {
        Path sessionsRoot = workdir.root().sessionsRoot();
        if (!Files.isDirectory(sessionsRoot)) {
            return;
        }

        List<FileArtifact> rows = repo.findAllSessionScoped();
        Map<String, FileArtifact> byPath = rows.stream()
                .collect(Collectors.toMap(FileArtifact::physicalPath, row -> row, (left, right) -> left));
        Set<String> seen = new HashSet<>();

        try (Stream<Path> walk = Files.walk(sessionsRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!isAdoptableFile(path, sessionsRoot)) {
                    continue;
                }
                String absolutePath = path.toAbsolutePath().normalize().toString();
                seen.add(absolutePath);
                if (byPath.containsKey(absolutePath)) {
                    continue;
                }
                String sessionId = sessionIdOf(path, sessionsRoot);
                if (sessionId != null) {
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
                    publishDiscarded(row, "reconcile");
                }
                case ARCHIVED -> log.warn(
                        "file_artifact {} archived row unexpectedly appears in session scope and is missing: {}",
                        row.id(),
                        row.physicalPath());
            }
        }
    }

    private void reconcileWorkspacesTree() {
        for (FileArtifact row : repo.findAllWorkspaceScopedArchived()) {
            if (Files.exists(Path.of(row.physicalPath()))) {
                continue;
            }
            log.warn("file_artifact {} archived file missing: {}", row.id(), row.physicalPath());
            repo.deleteById(row.id());
            publishDiscarded(row, "reconcile");
        }
    }

    private void publishDiscarded(FileArtifact row, String reason) {
        if (row.sessionId() == null) {
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
        if (name.isBlank() || name.startsWith(".") || name.endsWith(".meta.json")) {
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
}
