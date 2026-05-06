package com.datatalk.application.fileartifact;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactServiceTest {

    @TempDir
    Path tmp;

    RecordingRepository repo;
    SessionWorkdirService workdir;
    SessionBusRegistry buses;
    SessionBus bus;
    FileArtifactService service;
    Path sessionDir;

    @BeforeEach
    void setUp() {
        repo = new RecordingRepository();
        SessionWorkdirRoot root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        sessionDir = workdir.getOrCreate("ses_abc", "conn_xyz");
        buses = mock(SessionBusRegistry.class);
        bus = mock(SessionBus.class);
        when(buses.getOrCreate(anyString())).thenReturn(bus);
        service = new FileArtifactService(repo, workdir, buses, new ObjectMapper());
    }

    @Test
    void listMethodsDelegateToRepository() {
        FileArtifact sessionArtifact = artifact("session", FileArtifactStatus.TEMPORARY);
        FileArtifact archivedArtifact = artifact("archived", FileArtifactStatus.ARCHIVED);
        FileArtifact candidateArtifact = artifact("candidate", FileArtifactStatus.CANDIDATE);
        repo.bySession = List.of(sessionArtifact);
        repo.archivedByConnection = List.of(archivedArtifact);
        repo.candidatesBySession = List.of(candidateArtifact);

        assertThat(service.listForSession("ses_abc")).containsExactly(sessionArtifact);
        assertThat(service.listArchivedForConnection("conn_xyz")).containsExactly(archivedArtifact);
        assertThat(service.findCandidatesForSession("ses_abc")).containsExactly(candidateArtifact);
    }

    @Test
    void guardPathRejectsBlankPath() {
        assertThat(service.guardPath("ses_abc", " ")).contains(PathSafetyError.PATH_NOT_FOUND);
    }

    @Test
    void guardPathRejectsAbsolutePath() {
        assertThat(service.guardPath("ses_abc", "/etc/passwd"))
                .contains(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void guardPathRejectsDotDotTraversal() {
        assertThat(service.guardPath("ses_abc", "../../etc/passwd"))
                .contains(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void guardPathRejectsUnderscorePrefixedSegment() {
        assertThat(service.guardPath("ses_abc", "reports/_system.md"))
                .contains(PathSafetyError.PATH_IS_SYSTEM);
    }

    @Test
    void guardPathRejectsMissingSessionWorkdir() {
        assertThat(service.guardPath("ses_missing", "foo.md"))
                .contains(PathSafetyError.PATH_NOT_FOUND);
    }

    @Test
    void guardPathRejectsUnsafeSessionId() {
        assertThat(service.guardPath("../escape", "foo.md"))
                .contains(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void guardPathRejectsMissingFile() {
        assertThat(service.guardPath("ses_abc", "missing.md"))
                .contains(PathSafetyError.PATH_NOT_FOUND);
    }

    @Test
    void guardPathRejectsDirectory() throws Exception {
        Files.createDirectories(sessionDir.resolve("reports"));

        assertThat(service.guardPath("ses_abc", "reports"))
                .contains(PathSafetyError.PATH_IS_DIRECTORY);
    }

    @Test
    void guardPathRejectsTargetSymlink() throws Exception {
        Path victim = tmp.resolve("victim.md");
        Files.writeString(victim, "secret");
        Path link = sessionDir.resolve("link.md");
        createSymlinkOrSkip(link, victim);

        assertThat(service.guardPath("ses_abc", "link.md"))
                .contains(PathSafetyError.PATH_CONTAINS_SYMLINK);
    }

    @Test
    void guardPathRejectsIntermediateSymlinkEscape() throws Exception {
        Path outsideDir = tmp.resolve("outside");
        Files.createDirectories(outsideDir);
        Files.writeString(outsideDir.resolve("report.md"), "# outside\n");
        Path link = sessionDir.resolve("linked");
        createSymlinkOrSkip(link, outsideDir);

        assertThat(service.guardPath("ses_abc", "linked/report.md"))
                .isPresent();
    }

    @Test
    void guardPathRejectsSymlinkedSessionsRoot() throws Exception {
        Path alternateRoot = tmp.resolve("alternate");
        Path alternateSessionsRoot = alternateRoot.resolve("sessions");
        Files.createDirectories(alternateSessionsRoot.resolve("ses_linked"));
        Files.writeString(alternateSessionsRoot.resolve("ses_linked/report.md"), "# report\n");

        Path opencode = tmp.resolve("linked-opencode");
        Files.createDirectories(opencode);
        createSymlinkOrSkip(opencode.resolve("sessions"), alternateSessionsRoot);

        SessionWorkdirRoot linkedRoot = new SessionWorkdirRoot(tmp, opencode);
        SessionWorkdirService linkedWorkdir = new SessionWorkdirService(linkedRoot, new ObjectMapper());
        FileArtifactService linkedService = new FileArtifactService(repo, linkedWorkdir, buses, new ObjectMapper());

        assertThat(linkedService.guardPath("ses_linked", "report.md"))
                .contains(PathSafetyError.PATH_CONTAINS_SYMLINK);
    }

    @Test
    void guardPathAcceptsLegitimateRelativeFile() throws Exception {
        Files.writeString(sessionDir.resolve("orders-er.md"), "# ER\n");

        assertThat(service.guardPath("ses_abc", "orders-er.md")).isEmpty();
    }

    @Test
    void guardPathAcceptsNestedRelativeFile() throws Exception {
        Files.createDirectories(sessionDir.resolve("reports"));
        Files.writeString(sessionDir.resolve("reports/weekly.md"), "# weekly\n");

        assertThat(service.guardPath("ses_abc", "reports/weekly.md")).isEmpty();
    }

    @Test
    void markCandidatePromotesTemporary() {
        repo.found = Optional.of(artifact("fid", FileArtifactStatus.TEMPORARY));

        service.markCandidate("fid");

        assertThat(repo.statusUpdates).containsExactly(new StatusUpdate("fid", FileArtifactStatus.CANDIDATE));
    }

    @Test
    void markCandidateIsIdempotentForCandidate() {
        repo.found = Optional.of(artifact("fid", FileArtifactStatus.CANDIDATE));

        service.markCandidate("fid");

        assertThat(repo.statusUpdates).isEmpty();
    }

    @Test
    void markCandidateRejectsArchived() {
        repo.found = Optional.of(artifact("fid", FileArtifactStatus.ARCHIVED));

        assertThatThrownBy(() -> service.markCandidate("fid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHIVED");
    }

    @Test
    void markCandidateRejectsDiscarded() {
        repo.found = Optional.of(artifact("fid", FileArtifactStatus.DISCARDED));

        assertThatThrownBy(() -> service.markCandidate("fid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISCARDED");
    }

    @Test
    void markCandidateRejectsMissingArtifact() {
        repo.found = Optional.empty();

        assertThatThrownBy(() -> service.markCandidate("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file artifact not found");
    }

    @Test
    void recordDetectedInsertsTemporaryWhenArtifactNotDeclared() throws Exception {
        Path file = sessionDir.resolve("foo.csv");
        Files.writeString(file, "id,val\n1,2\n");

        var inserted = service.recordDetected("ses_abc", file, Map.of());

        assertThat(inserted).isPresent();
        assertThat(repo.inserted).hasSize(1);
        FileArtifact row = repo.inserted.get(0);
        assertThat(row.status()).isEqualTo(FileArtifactStatus.TEMPORARY);
        assertThat(row.filename()).isEqualTo("foo.csv");
        assertThat(row.physicalPath()).isEqualTo(file.toAbsolutePath().normalize().toString());
        verify(bus).publish(org.mockito.ArgumentMatchers.any(DtEvent.FileArtifactDetected.class));
    }

    @Test
    void recordDetectedInsertsCandidateWhenArtifactDeclared() throws Exception {
        Path file = sessionDir.resolve("orders-er.md");
        Files.writeString(file, "# report\n");

        service.recordDetected("ses_abc", file, Map.of("artifact", "yes", "kind", "er_diagram", "title", "Orders"));

        FileArtifact row = repo.inserted.get(0);
        assertThat(row.status()).isEqualTo(FileArtifactStatus.CANDIDATE);
        assertThat(row.kind()).isEqualTo(FileArtifactKind.ER_DIAGRAM);
        assertThat(row.title()).isEqualTo("Orders");
    }

    @Test
    void recordDetectedIsIdempotentWhenPathAlreadyExists() throws Exception {
        Path file = sessionDir.resolve("foo.csv");
        Files.writeString(file, "x");
        FileArtifact existing = artifact("existing", FileArtifactStatus.TEMPORARY);
        repo.byPhysicalPath.put(file.toAbsolutePath().normalize().toString(), existing);

        var result = service.recordDetected("ses_abc", file, Map.of("artifact", "true"));

        assertThat(result).contains(existing);
        assertThat(repo.inserted).isEmpty();
    }

    @Test
    void recordModifiedUpdatesSizeAndPromotesTemporaryWhenArtifactDeclarationAppears() throws Exception {
        Path file = sessionDir.resolve("foo.md");
        Files.writeString(file, "---\nartifact: true\n---\n");
        FileArtifact existing = artifactAt("fid", FileArtifactStatus.TEMPORARY, file);
        repo.byPhysicalPath.put(file.toAbsolutePath().normalize().toString(), existing);

        service.recordModified(file, Map.of("artifact", "true", "kind", "report"));

        assertThat(repo.metadataUpdates).hasSize(1);
        assertThat(repo.statusUpdates).containsExactly(new StatusUpdate("fid", FileArtifactStatus.CANDIDATE));
        verify(bus).publish(org.mockito.ArgumentMatchers.any(DtEvent.FileArtifactArchiveRequested.class));
    }

    @Test
    void recordModifiedDoesNotPromoteCandidateAgain() throws Exception {
        Path file = sessionDir.resolve("foo.md");
        Files.writeString(file, "x");
        repo.byPhysicalPath.put(file.toAbsolutePath().normalize().toString(),
                artifactAt("fid", FileArtifactStatus.CANDIDATE, file));

        service.recordModified(file, Map.of("artifact", "true"));

        assertThat(repo.metadataUpdates).hasSize(1);
        assertThat(repo.statusUpdates).isEmpty();
    }

    @Test
    void recordDeletedSilentlyDeletesTemporaryRow() {
        FileArtifact existing = artifact("fid", FileArtifactStatus.TEMPORARY);
        repo.byPhysicalPath.put(existing.physicalPath(), existing);

        service.recordDeleted(Path.of(existing.physicalPath()));

        assertThat(repo.deletedIds).containsExactly("fid");
    }

    @Test
    void recordDeletedPublishesDiscardedForCandidateRow() {
        FileArtifact existing = artifact("fid", FileArtifactStatus.CANDIDATE);
        repo.byPhysicalPath.put(existing.physicalPath(), existing);

        service.recordDeleted(Path.of(existing.physicalPath()));

        assertThat(repo.deletedIds).containsExactly("fid");
        verify(bus).publish(org.mockito.ArgumentMatchers.any(DtEvent.FileArtifactDiscarded.class));
    }

    @Test
    void recordDeletedNoopsWhenRowMissing() {
        service.recordDeleted(Path.of("/abs/missing.md"));

        assertThat(repo.deletedIds).isEmpty();
    }

    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
    }

    private static FileArtifact artifact(String id, FileArtifactStatus status) {
        Instant now = Instant.now();
        return new FileArtifact(
                id,
                FileArtifactScope.SESSION,
                status,
                FileArtifactKind.OTHER,
                "ses_abc",
                null,
                id + ".md",
                "/tmp/" + id + ".md",
                1L,
                null,
                null,
                null,
                now,
                now,
                null,
                Map.of());
    }

    private static FileArtifact artifactAt(String id, FileArtifactStatus status, Path path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id,
                FileArtifactScope.SESSION,
                status,
                FileArtifactKind.OTHER,
                "ses_abc",
                null,
                path.getFileName().toString(),
                path.toAbsolutePath().normalize().toString(),
                1L,
                null,
                null,
                null,
                now,
                now,
                null,
                Map.of());
    }

    private record StatusUpdate(String id, FileArtifactStatus status) {}

    private record MetadataUpdate(String id, long sizeBytes, long updatedAtMillis) {}

    // --- archiveCandidate tests ---

    @Test
    void archiveCandidate_returns_PathRejected_for_dotdot_path() {
        var result = service.archiveCandidate(
                "ses_abc", "../escape.md",
                FileArtifactKind.ER_DIAGRAM, null, null,
                Clock.systemUTC(), new IdGenerator());

        assertThat(result).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.PathRejected.class);
        var rejected = (FileArtifactService.ArchiveCandidateOutcome.PathRejected) result;
        assertThat(rejected.error()).isEqualTo(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void archiveCandidate_inserts_new_candidate_row_when_no_existing() throws Exception {
        Path file = sessionDir.resolve("orders-er.md");
        Files.writeString(file, "# ER Diagram\n");

        var result = service.archiveCandidate(
                "ses_abc", "orders-er.md",
                FileArtifactKind.ER_DIAGRAM, "Orders ER", "Entity-relationship diagram",
                Clock.systemUTC(), new IdGenerator());

        assertThat(result).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        var success = (FileArtifactService.ArchiveCandidateOutcome.Success) result;
        assertThat(success.alreadyArchived()).isFalse();
        assertThat(repo.inserted).hasSize(1);
        assertThat(repo.inserted.get(0).status()).isEqualTo(FileArtifactStatus.CANDIDATE);
        assertThat(repo.inserted.get(0).kind()).isEqualTo(FileArtifactKind.ER_DIAGRAM);
    }

    @Test
    void archiveCandidate_promotes_existing_temporary_row() throws Exception {
        Path file = sessionDir.resolve("x.md");
        Files.writeString(file, "# temp\n");
        String physicalPath = file.toRealPath().toString();
        FileArtifact temporary = artifactAt("fid_temp", FileArtifactStatus.TEMPORARY, file);
        repo.bySession = List.of(temporary);

        var result = service.archiveCandidate(
                "ses_abc", "x.md",
                FileArtifactKind.OTHER, null, null,
                Clock.systemUTC(), new IdGenerator());

        assertThat(result).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        var success = (FileArtifactService.ArchiveCandidateOutcome.Success) result;
        assertThat(success.fileArtifactId()).isEqualTo("fid_temp");
        assertThat(success.alreadyArchived()).isFalse();
        assertThat(repo.statusUpdates).containsExactly(new StatusUpdate("fid_temp", FileArtifactStatus.CANDIDATE));
        assertThat(repo.inserted).isEmpty();
    }

    @Test
    void archiveCandidate_is_idempotent_for_archived_row() throws Exception {
        Path file = sessionDir.resolve("done.md");
        Files.writeString(file, "# archived\n");
        String physicalPath = file.toRealPath().toString();
        FileArtifact archived = artifactAt("fid_arch", FileArtifactStatus.ARCHIVED, file);
        repo.bySession = List.of(archived);

        var result = service.archiveCandidate(
                "ses_abc", "done.md",
                FileArtifactKind.OTHER, null, null,
                Clock.systemUTC(), new IdGenerator());

        assertThat(result).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        var success = (FileArtifactService.ArchiveCandidateOutcome.Success) result;
        assertThat(success.fileArtifactId()).isEqualTo("fid_arch");
        assertThat(success.alreadyArchived()).isTrue();
        assertThat(repo.statusUpdates).isEmpty();
        assertThat(repo.inserted).isEmpty();
        assertThat(repo.metadataUpdates).isEmpty();
    }

    private static final class RecordingRepository implements FileArtifactRepository {
        Optional<FileArtifact> found = Optional.empty();
        List<FileArtifact> bySession = List.of();
        List<FileArtifact> archivedByConnection = List.of();
        List<FileArtifact> candidatesBySession = List.of();
        List<FileArtifact> sessionScoped = List.of();
        List<FileArtifact> workspaceArchived = List.of();
        Map<String, FileArtifact> byPhysicalPath = new java.util.HashMap<>();
        List<FileArtifact> inserted = new ArrayList<>();
        List<StatusUpdate> statusUpdates = new ArrayList<>();
        List<MetadataUpdate> metadataUpdates = new ArrayList<>();
        List<String> deletedIds = new ArrayList<>();

        @Override
        public void insert(FileArtifact artifact) {
            inserted.add(artifact);
            byPhysicalPath.put(artifact.physicalPath(), artifact);
        }

        @Override
        public Optional<FileArtifact> findById(String id) {
            return found;
        }

        @Override
        public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
            return Optional.ofNullable(byPhysicalPath.get(physicalPath));
        }

        @Override
        public List<FileArtifact> findBySession(String sessionId) {
            return bySession;
        }

        @Override
        public List<FileArtifact> findArchivedByConnection(String connectionId) {
            return archivedByConnection;
        }

        @Override
        public List<FileArtifact> findCandidatesBySession(String sessionId) {
            return candidatesBySession;
        }

        @Override
        public List<FileArtifact> findAllSessionScoped() {
            return sessionScoped;
        }

        @Override
        public List<FileArtifact> findAllWorkspaceScopedArchived() {
            return workspaceArchived;
        }

        @Override
        public void updateStatus(String id, FileArtifactStatus newStatus) {
            statusUpdates.add(new StatusUpdate(id, newStatus));
        }

        @Override
        public void updateLocation(
                String id,
                FileArtifactStatus newStatus,
                String newScope,
                String newPhysicalPath,
                String newConnectionId) {
        }

        @Override
        public void markArchived(String id, String connectionId, String newPhysicalPath) {
        }

        @Override
        public void deleteTransientByForSession(String sessionId) {
        }

        @Override
        public void detachArchivedFromSession(String sessionId) {
        }

        @Override
        public void deleteById(String id) {
            deletedIds.add(id);
        }

        @Override
        public void updateMetadata(String id, long sizeBytes, long updatedAtMillis) {
            metadataUpdates.add(new MetadataUpdate(id, sizeBytes, updatedAtMillis));
        }

        @Override
        public int countCandidatesBySession(String sessionId) {
            return 0;
        }

        @Override
        public ConnectionResourceCounts countResourcesByConnection(String connectionId, List<String> sessionIds) {
            return new ConnectionResourceCounts(0, 0, 0, 0);
        }

        @Override
        public void deleteTransientByForConnection(List<String> sessionIds) {
        }

        @Override
        public void detachArchivedFromConnection(String connectionId, String connectionName, long deletedAtMillis) {
        }
    }
}
