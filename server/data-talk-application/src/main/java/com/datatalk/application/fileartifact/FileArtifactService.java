package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Use-case service for file artifact reads, candidate promotion, and path safety.
 */
@Service
public class FileArtifactService {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactService.class);

    private final FileArtifactRepository repo;
    private final SessionWorkdirService workdir;
    @SuppressWarnings("unused")
    private final ObjectMapper json;

    public FileArtifactService(FileArtifactRepository repo, SessionWorkdirService workdir, ObjectMapper json) {
        this.repo = repo;
        this.workdir = workdir;
        this.json = json;
    }

    public List<FileArtifact> listForSession(String sessionId) {
        return repo.findBySession(sessionId);
    }

    public List<FileArtifact> listArchivedForConnection(String connectionId) {
        return repo.findArchivedByConnection(connectionId);
    }

    public List<FileArtifact> findCandidatesForSession(String sessionId) {
        return repo.findCandidatesBySession(sessionId);
    }

    public Optional<PathSafetyError> guardPath(String sessionId, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        Path requested = Path.of(requestedPath);
        if (requested.isAbsolute()) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        }
        for (Path segment : requested) {
            String name = segment.toString();
            if ("..".equals(name)) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            if (name.startsWith("_")) {
                return Optional.of(PathSafetyError.PATH_IS_SYSTEM);
            }
        }

        Path base;
        try {
            if (managedBaseContainsSymlink(sessionId)) {
                return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
            }
            base = workdir.require(sessionId);
        } catch (IllegalArgumentException e) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        } catch (IllegalStateException e) {
            return Optional.of(e.getMessage() != null && e.getMessage().contains("symlink")
                    ? PathSafetyError.PATH_CONTAINS_SYMLINK
                    : PathSafetyError.PATH_NOT_FOUND);
        }

        Path target = base.resolve(requested).normalize();
        if (!target.startsWith(base)) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        }

        Optional<PathSafetyError> symlinkError = rejectSymlinkSegments(base, requested.normalize());
        if (symlinkError.isPresent()) {
            return symlinkError;
        }

        BasicFileAttributes before;
        try {
            before = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        if (before.isSymbolicLink() || Files.isSymbolicLink(target)) {
            return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
        }
        if (before.isDirectory()) {
            return Optional.of(PathSafetyError.PATH_IS_DIRECTORY);
        }
        if (!before.isRegularFile()) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        try {
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(base)) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            Optional<PathSafetyError> secondSymlinkError = rejectSymlinkSegments(base, requested.normalize());
            if (secondSymlinkError.isPresent()) {
                return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
            }
            BasicFileAttributes after = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributesChanged(before, after)) {
                return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
            }
        } catch (IOException e) {
            return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
        }

        return Optional.empty();
    }

    private boolean managedBaseContainsSymlink(String sessionId) {
        SessionWorkdirRoot root = workdir.root();
        return Files.isSymbolicLink(root.dataTalkRoot())
                || Files.isSymbolicLink(root.opencodeCwd())
                || Files.isSymbolicLink(root.sessionsRoot())
                || Files.isSymbolicLink(root.sessionDir(sessionId));
    }

    public void markCandidate(String fileArtifactId) {
        FileArtifact artifact = repo.findById(fileArtifactId)
                .orElseThrow(() -> new IllegalArgumentException("file artifact not found: " + fileArtifactId));

        switch (artifact.status()) {
            case TEMPORARY -> {
                repo.updateStatus(fileArtifactId, FileArtifactStatus.CANDIDATE);
                log.info("file_artifact {} promoted TEMPORARY to CANDIDATE", fileArtifactId);
            }
            case CANDIDATE -> {
            }
            case ARCHIVED, DISCARDED -> throw new IllegalStateException(
                    "cannot mark candidate: artifact " + fileArtifactId + " is " + artifact.status());
        }
    }

    private static Optional<PathSafetyError> rejectSymlinkSegments(Path base, Path requested) {
        Path current = base;
        for (Path segment : requested) {
            String name = segment.toString();
            if (".".equals(name) || name.isBlank()) {
                continue;
            }
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
            }
        }
        return Optional.empty();
    }

    private static boolean attributesChanged(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.fileKey() == null || after.fileKey() == null) {
            return true;
        }
        return !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size()
                || !Objects.equals(before.lastModifiedTime(), after.lastModifiedTime())
                || !Objects.equals(before.creationTime(), after.creationTime())
                || before.isRegularFile() != after.isRegularFile()
                || before.isDirectory() != after.isDirectory()
                || before.isSymbolicLink() != after.isSymbolicLink();
    }
}
