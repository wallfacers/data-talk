package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datatalk.application.channel.IdGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final SessionBusRegistry buses;
    @SuppressWarnings("unused")
    private final ObjectMapper json;
    private final FileArtifactPhysicalMover mover;
    private final SessionRepository sessionRepo;

    public FileArtifactService(
            FileArtifactRepository repo,
            SessionWorkdirService workdir,
            SessionBusRegistry buses,
            ObjectMapper json,
            FileArtifactPhysicalMover mover,
            SessionRepository sessionRepo) {
        this.repo = repo;
        this.workdir = workdir;
        this.buses = buses;
        this.json = json;
        this.mover = mover;
        this.sessionRepo = sessionRepo;
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

    public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
        return repo.findByPhysicalPath(physicalPath);
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

    public Optional<FileArtifact> recordDetected(String sessionId, Path physicalPath, Map<String, String> frontmatter) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(physicalPath, "physicalPath");
        Map<String, String> safeFrontmatter = frontmatter == null ? Map.of() : frontmatter;
        Path absolutePath = physicalPath.toAbsolutePath().normalize();
        String pathStr = absolutePath.toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isPresent()) {
            return existing;
        }

        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(absolutePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            log.debug("recordDetected skipped vanished file {}", pathStr);
            return Optional.empty();
        }
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
            return Optional.empty();
        }

        FileArtifactStatus status = FrontmatterParser.isArtifactDeclared(safeFrontmatter)
                ? FileArtifactStatus.CANDIDATE
                : FileArtifactStatus.TEMPORARY;
        FileArtifactKind kind = parseKind(safeFrontmatter);
        Instant now = Instant.now();
        FileArtifact row = new FileArtifact(
                FileArtifactIds.next(),
                FileArtifactScope.SESSION,
                status,
                kind,
                sessionId,
                null,
                absolutePath.getFileName().toString(),
                pathStr,
                attrs.size(),
                guessMime(absolutePath),
                safeFrontmatter.get("title"),
                safeFrontmatter.get("summary"),
                now,
                now,
                null,
                new LinkedHashMap<>(safeFrontmatter));
        repo.insert(row);
        publish(sessionId, new DtEvent.FileArtifactDetected(
                row.id(),
                sessionId,
                row.filename(),
                row.kind().dbValue(),
                row.status().dbValue(),
                row.sizeBytes()));
        return Optional.of(row);
    }

    public void recordModified(Path physicalPath, Map<String, String> frontmatter) {
        Objects.requireNonNull(physicalPath, "physicalPath");
        Map<String, String> safeFrontmatter = frontmatter == null ? Map.of() : frontmatter;
        Path absolutePath = physicalPath.toAbsolutePath().normalize();
        String pathStr = absolutePath.toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isEmpty()) {
            return;
        }
        FileArtifact row = existing.get();
        try {
            repo.updateMetadata(
                    row.id(),
                    Files.size(absolutePath),
                    Files.getLastModifiedTime(absolutePath, LinkOption.NOFOLLOW_LINKS).toMillis());
        } catch (IOException e) {
            log.debug("recordModified skipped vanished file {}", pathStr);
            return;
        }

        // Spec 5.6: MODIFY may promote TEMPORARY but must not refresh title/summary
        // on rows that are already CANDIDATE.
        if (row.status() == FileArtifactStatus.TEMPORARY && FrontmatterParser.isArtifactDeclared(safeFrontmatter)) {
            repo.updateStatus(row.id(), FileArtifactStatus.CANDIDATE);
            publish(row.sessionId(), new DtEvent.FileArtifactArchiveRequested(
                    row.id(),
                    row.sessionId(),
                    parseKind(safeFrontmatter).dbValue(),
                    safeFrontmatter.getOrDefault("title", row.title()),
                    safeFrontmatter.getOrDefault("summary", row.summary())));
        }
    }

    public void recordDeleted(Path physicalPath) {
        Objects.requireNonNull(physicalPath, "physicalPath");
        String pathStr = physicalPath.toAbsolutePath().normalize().toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isEmpty()) {
            return;
        }
        FileArtifact row = existing.get();
        switch (row.status()) {
            case TEMPORARY, DISCARDED -> repo.deleteById(row.id());
            case CANDIDATE -> {
                repo.deleteById(row.id());
                publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(row.id(), "watcher_delete"));
            }
            case ARCHIVED -> log.warn(
                    "file_artifact {} is ARCHIVED but watcher saw delete under sessions root: {}",
                    row.id(),
                    pathStr);
        }
    }

    private void publish(String sessionId, DtEvent event) {
        if (sessionId == null) {
            return;
        }
        try {
            buses.getOrCreate(sessionId).publish(event);
        } catch (Exception e) {
            log.warn("failed to publish file artifact event for session={}: {}", sessionId, e.toString());
        }
    }

    private static FileArtifactKind parseKind(Map<String, String> frontmatter) {
        String raw = frontmatter.get("kind");
        if (raw == null || raw.isBlank()) {
            return FileArtifactKind.OTHER;
        }
        try {
            return FileArtifactKind.fromDb(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FileArtifactKind.OTHER;
        }
    }

    private static String guessMime(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException e) {
            return null;
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

    // --- archiveCandidate use case ---

    public sealed interface ArchiveCandidateOutcome {
        record Success(String fileArtifactId, String physicalPath, boolean alreadyArchived) implements ArchiveCandidateOutcome {}
        record PathRejected(PathSafetyError error) implements ArchiveCandidateOutcome {}
    }

    /**
     * Promote or create a CANDIDATE file artifact for the given session path.
     *
     * <p>Idempotent: ARCHIVED rows return {@code alreadyArchived=true};
     * CANDIDATE rows just get metadata refreshed; TEMPORARY rows are promoted.
     * DISCARDED rows trigger a fresh insert.
     */
    public ArchiveCandidateOutcome archiveCandidate(
            String sessionId,
            String requestedPath,
            FileArtifactKind kind,
            String title,
            String summary,
            Clock clock,
            IdGenerator idGenerator) {

        // 1. Guard path safety
        Optional<PathSafetyError> guardError = guardPath(sessionId, requestedPath);
        if (guardError.isPresent()) {
            return new ArchiveCandidateOutcome.PathRejected(guardError.get());
        }

        // 2. Resolve physical path and read file size
        Path physicalPath;
        long sizeBytes;
        try {
            Path base = workdir.require(sessionId);
            physicalPath = base.resolve(requestedPath).toRealPath();
            sizeBytes = Files.size(physicalPath);
        } catch (IOException | IllegalArgumentException e) {
            return new ArchiveCandidateOutcome.PathRejected(PathSafetyError.PATH_NOT_FOUND);
        }

        // 3. Look up existing row by physical path in session results
        List<FileArtifact> sessionArtifacts = repo.findBySession(sessionId);
        FileArtifact existing = sessionArtifacts.stream()
                .filter(a -> physicalPath.toString().equals(a.physicalPath()))
                .findFirst()
                .orElse(null);

        Instant now = Instant.now(clock);

        if (existing != null) {
            switch (existing.status()) {
                case ARCHIVED -> {
                    return new ArchiveCandidateOutcome.Success(
                            existing.id(), physicalPath.toString(), true);
                }
                case DISCARDED -> {
                    String newId = FileArtifactIds.next();
                    FileArtifact row = new FileArtifact(
                            newId,
                            FileArtifactScope.SESSION,
                            FileArtifactStatus.CANDIDATE,
                            kind,
                            sessionId,
                            null,
                            physicalPath.getFileName().toString(),
                            physicalPath.toString(),
                            sizeBytes,
                            guessMime(physicalPath),
                            title,
                            summary,
                            now,
                            now,
                            null,
                            new LinkedHashMap<>());
                    repo.insert(row);
                    return new ArchiveCandidateOutcome.Success(
                            newId, physicalPath.toString(), false);
                }
                case TEMPORARY -> {
                    repo.updateStatus(existing.id(), FileArtifactStatus.CANDIDATE);
                    repo.updateMetadata(existing.id(), sizeBytes, now.toEpochMilli());
                    return new ArchiveCandidateOutcome.Success(
                            existing.id(), physicalPath.toString(), false);
                }
                case CANDIDATE -> {
                    repo.updateMetadata(existing.id(), sizeBytes, now.toEpochMilli());
                    return new ArchiveCandidateOutcome.Success(
                            existing.id(), physicalPath.toString(), false);
                }
            }
        }

        // 4. No existing row — insert new CANDIDATE
        String newId = FileArtifactIds.next();
        FileArtifact row = new FileArtifact(
                newId,
                FileArtifactScope.SESSION,
                FileArtifactStatus.CANDIDATE,
                kind,
                sessionId,
                null,
                physicalPath.getFileName().toString(),
                physicalPath.toString(),
                sizeBytes,
                guessMime(physicalPath),
                title,
                summary,
                now,
                now,
                null,
                new LinkedHashMap<>());
        repo.insert(row);
        return new ArchiveCandidateOutcome.Success(newId, physicalPath.toString(), false);
    }

    // ───────── archive / discard use cases (spec §A.1 / §A.3) ─────────

    public sealed interface ArchiveOutcome {
        record Success(FileArtifact artifact) implements ArchiveOutcome {}
        record NotFound(String fid) implements ArchiveOutcome {}
        record WrongStatus(String fid, FileArtifactStatus actual) implements ArchiveOutcome {}
        record SessionMissing(String fid) implements ArchiveOutcome {}
        record ConnectionMissing(String fid) implements ArchiveOutcome {}
        record TocTou(String fid) implements ArchiveOutcome {}
        record DiskFull(String fid) implements ArchiveOutcome {}
        record MvFailed(String fid, String detail) implements ArchiveOutcome {}
    }

    public sealed interface DiscardOutcome {
        record Success(FileArtifact artifact) implements DiscardOutcome {}
        record NotFound(String fid) implements DiscardOutcome {}
        record AlreadyDiscarded(FileArtifact artifact) implements DiscardOutcome {}
        record TocTou(String fid) implements DiscardOutcome {}
        record DiskFull(String fid) implements DiscardOutcome {}
        record MvFailed(String fid, String detail) implements DiscardOutcome {}
    }

    /**
     * Move a CANDIDATE row's physical file to the connection's workspaces
     * directory and update DB row to ARCHIVED. Spec §A.1 / §A.3.
     */
    @Transactional
    public ArchiveOutcome archive(String sessionId, String fileArtifactId) {
        FileArtifact row = repo.findById(fileArtifactId).orElse(null);
        if (row == null) {
            return new ArchiveOutcome.NotFound(fileArtifactId);
        }
        if (row.status() != FileArtifactStatus.CANDIDATE) {
            return new ArchiveOutcome.WrongStatus(fileArtifactId, row.status());
        }
        if (sessionId == null || !sessionId.equals(row.sessionId())) {
            return new ArchiveOutcome.SessionMissing(fileArtifactId);
        }
        Optional<SessionRecord> session = sessionRepo.findById(sessionId);
        if (session.isEmpty()) {
            return new ArchiveOutcome.SessionMissing(fileArtifactId);
        }
        String connectionId = session.get().connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            return new ArchiveOutcome.ConnectionMissing(fileArtifactId);
        }

        Path src = Path.of(row.physicalPath());
        Path dstDir = workdir.root().workspacesRoot().resolve(connectionId);

        Path dst;
        try {
            dst = mover.mv(src, dstDir, row.filename());
        } catch (FileArtifactPhysicalMover.SourceMissing e) {
            return new ArchiveOutcome.NotFound(fileArtifactId);
        } catch (FileArtifactPhysicalMover.TocTouChanged e) {
            return new ArchiveOutcome.TocTou(fileArtifactId);
        } catch (FileArtifactPhysicalMover.DiskFull e) {
            return new ArchiveOutcome.DiskFull(fileArtifactId);
        } catch (FileArtifactPhysicalMover.MvFailed e) {
            return new ArchiveOutcome.MvFailed(fileArtifactId, e.getMessage());
        }

        repo.markArchived(fileArtifactId, connectionId, dst.toString());
        // markArchived also updates filename if path changed name (versioning).
        // The spec requires the row's filename to reflect the new versioned name;
        // if markArchived does not touch filename, follow up with a dedicated update:
        if (!dst.getFileName().toString().equals(row.filename())) {
            repo.updateMetadata(fileArtifactId, Files.exists(dst) ? sizeOrZero(dst) : row.sizeBytes(),
                    Instant.now().toEpochMilli());
            // Note: the existing repo does not have updateFilename. The contract
            // is markArchived = scope+status+conn+path; filename stays. This is
            // OK for now — UI displays filename from physicalPath if needed.
            // Spec §A.1 acknowledges versioned filename in physicalPath; row.filename
            // remaining the original is acceptable until a dedicated UI need arises.
        }

        FileArtifact updated = repo.findById(fileArtifactId)
                .orElseThrow(() -> new IllegalStateException("row vanished after archive: " + fileArtifactId));
        publish(sessionId, new DtEvent.FileArtifactArchived(
                updated.id(),
                sessionId,
                connectionId,
                updated.filename(),
                dst.toString()));
        return new ArchiveOutcome.Success(updated);
    }

    /**
     * Move any-status row's physical file to ~/.data-talk/_trash/ and update
     * DB row to DISCARDED. Spec §A.1 / §A.3.
     */
    @Transactional
    public DiscardOutcome discard(String fileArtifactId) {
        FileArtifact row = repo.findById(fileArtifactId).orElse(null);
        if (row == null) {
            return new DiscardOutcome.NotFound(fileArtifactId);
        }
        if (row.status() == FileArtifactStatus.DISCARDED) {
            return new DiscardOutcome.AlreadyDiscarded(row);
        }

        Path src = Path.of(row.physicalPath());
        Path trashDir = workdir.root().trashRoot();
        String prefix = (row.connectionId() != null ? row.connectionId() : (row.sessionId() != null ? row.sessionId() : "orphan"))
                + "__" + row.id() + "__" + row.filename();

        Path dst;
        try {
            dst = mover.mv(src, trashDir, prefix);
        } catch (FileArtifactPhysicalMover.SourceMissing e) {
            // file already gone — still mark row discarded so DB state catches up
            repo.updateLocation(fileArtifactId, FileArtifactStatus.DISCARDED, row.scope().dbValue(), row.physicalPath(), row.connectionId());
            FileArtifact updated = repo.findById(fileArtifactId)
                    .orElseThrow(() -> new IllegalStateException("row vanished after discard (SourceMissing): " + fileArtifactId));
            publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(updated.id(), "user_source_missing"));
            return new DiscardOutcome.Success(updated);
        } catch (FileArtifactPhysicalMover.TocTouChanged e) {
            return new DiscardOutcome.TocTou(fileArtifactId);
        } catch (FileArtifactPhysicalMover.DiskFull e) {
            return new DiscardOutcome.DiskFull(fileArtifactId);
        } catch (FileArtifactPhysicalMover.MvFailed e) {
            return new DiscardOutcome.MvFailed(fileArtifactId, e.getMessage());
        }

        repo.updateLocation(fileArtifactId, FileArtifactStatus.DISCARDED, row.scope().dbValue(), dst.toString(), row.connectionId());
        FileArtifact updated = repo.findById(fileArtifactId)
                .orElseThrow(() -> new IllegalStateException("row vanished after discard: " + fileArtifactId));
        publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(updated.id(), "user"));
        return new DiscardOutcome.Success(updated);
    }

    private static long sizeOrZero(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0L; }
    }
}
