package com.datatalk.application.fileartifact;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileArtifactServiceTest {

    @TempDir
    Path tmp;

    RecordingRepository repo;
    SessionWorkdirService workdir;
    FileArtifactService service;
    Path sessionDir;

    @BeforeEach
    void setUp() {
        repo = new RecordingRepository();
        SessionWorkdirRoot root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        sessionDir = workdir.getOrCreate("ses_abc", "conn_xyz");
        service = new FileArtifactService(repo, workdir, new ObjectMapper());
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
        FileArtifactService linkedService = new FileArtifactService(repo, linkedWorkdir, new ObjectMapper());

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

    private record StatusUpdate(String id, FileArtifactStatus status) {}

    private static final class RecordingRepository implements FileArtifactRepository {
        Optional<FileArtifact> found = Optional.empty();
        List<FileArtifact> bySession = List.of();
        List<FileArtifact> archivedByConnection = List.of();
        List<FileArtifact> candidatesBySession = List.of();
        List<StatusUpdate> statusUpdates = new ArrayList<>();

        @Override
        public void insert(FileArtifact artifact) {
        }

        @Override
        public Optional<FileArtifact> findById(String id) {
            return found;
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
        }

        @Override
        public void updateMetadata(String id, long sizeBytes, long updatedAtMillis) {
        }
    }
}
