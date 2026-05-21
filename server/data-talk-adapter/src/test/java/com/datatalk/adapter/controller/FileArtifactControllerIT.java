package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class FileArtifactControllerIT {

    private static final String SESSION_ID = "ses-file-controller-it";
    private static final String OTHER_SESSION_ID = "ses-file-controller-other";

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    FileArtifactRepository repo;

    @Autowired
    SessionWorkdirRoot workdirRoot;

    @Autowired
    @Qualifier("datatalkJdbc")
    JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM file_artifact");
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void getSessionFilesReturnsSessionArtifacts() throws Exception {
        repo.insert(stub("file-controller-a1", FileArtifactStatus.TEMPORARY, SESSION_ID, null));
        repo.insert(stub("file-controller-a2", FileArtifactStatus.CANDIDATE, SESSION_ID, null));
        repo.insert(stub("file-controller-a3", FileArtifactStatus.TEMPORARY, OTHER_SESSION_ID, null));

        mvc.perform(get("/api/sessions/{sessionId}/files", SESSION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].scope", everyItem(is("session"))))
            .andExpect(jsonPath("$[*].kind", everyItem(is("other"))))
            .andExpect(jsonPath("$[*].status", containsInAnyOrder("temporary", "candidate")));
    }

    @Test
    void postMarkCandidatePromotesStatus() throws Exception {
        repo.insert(stub("file-controller-candidate", FileArtifactStatus.TEMPORARY, SESSION_ID, null));

        mvc.perform(post("/api/files/{fileArtifactId}/mark-candidate", "file-controller-candidate"))
            .andExpect(status().isNoContent());

        assertThat(repo.findById("file-controller-candidate").orElseThrow().status())
            .isEqualTo(FileArtifactStatus.CANDIDATE);
    }

    // ───────── Part 5a: archive / discard ─────────

    @Test
    void postArchive_promotes_candidate_to_archived() throws Exception {
        String connId = "conn-archive-it";
        // Seed connection row
        jdbc.update("""
            INSERT OR IGNORE INTO connections(id, name, kind, host, port, username, password_enc, created_at)
            VALUES(?, ?, 'mysql', 'h', 3306, 'u', x'00', 0)
            """, connId, "conn-archive-it");

        // Create session with connectionId
        jdbc.update("""
            INSERT OR IGNORE INTO sessions(id, connection_id, title, has_ever_sent,
                opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 0, NULL, ?, ?, 0)
            """, SESSION_ID, connId, "archive-test", System.currentTimeMillis(), System.currentTimeMillis());

        // Create the actual source file at the session workdir path
        Path sessionDir = workdirRoot.sessionsRoot().resolve(SESSION_ID);
        Files.createDirectories(sessionDir);
        Path srcFile = sessionDir.resolve("archive-test.md");
        Files.writeString(srcFile, "# Archive Test Content");

        // Insert candidate row pointing to that file
        Instant now = Instant.now();
        FileArtifact candidate = new FileArtifact(
            "fa-archive-it",
            FileArtifactScope.SESSION,
            FileArtifactStatus.CANDIDATE,
            FileArtifactKind.OTHER,
            SESSION_ID,
            null,
            "archive-test.md",
            srcFile.toAbsolutePath().toString(),
            Files.size(srcFile),
            "text/markdown",
            "Archive Test",
            "Summary",
            now,
            now,
            null,
            Map.of()
        , false);
        repo.insert(candidate);

        // Archive the file
        mvc.perform(post("/api/sessions/{sessionId}/files/{fileArtifactId}/archive",
                SESSION_ID, "fa-archive-it"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("fa-archive-it"))
            .andExpect(jsonPath("$.status").value("archived"));

        // Verify DB state
        FileArtifact archived = repo.findById("fa-archive-it").orElseThrow();
        assertThat(archived.status()).isEqualTo(FileArtifactStatus.ARCHIVED);
        assertThat(archived.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(archived.connectionId()).isEqualTo(connId);

        // Verify file moved to workspace
        Path workspaceDir = workdirRoot.workspacesRoot().resolve(connId);
        Path movedFile = workspaceDir.resolve("archive-test.md");
        assertThat(Files.exists(movedFile)).isTrue();
        assertThat(Files.exists(srcFile)).isFalse();

        // Cleanup
        Files.deleteIfExists(movedFile);
    }

    @Test
    void postArchive_returns_404_for_missing_file() throws Exception {
        String connId = "conn-archive-missing";
        jdbc.update("""
            INSERT OR IGNORE INTO connections(id, name, kind, host, port, username, password_enc, created_at)
            VALUES(?, ?, 'mysql', 'h', 3306, 'u', x'00', 0)
            """, connId, "conn-archive-missing");

        jdbc.update("""
            INSERT OR IGNORE INTO sessions(id, connection_id, title, has_ever_sent,
                opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 0, NULL, ?, ?, 0)
            """, OTHER_SESSION_ID, connId, "missing-file-test",
            System.currentTimeMillis(), System.currentTimeMillis());

        // Candidate row pointing to a nonexistent file
        Instant now = Instant.now();
        repo.insert(new FileArtifact(
            "fa-archive-missing",
            FileArtifactScope.SESSION,
            FileArtifactStatus.CANDIDATE,
            FileArtifactKind.OTHER,
            OTHER_SESSION_ID,
            null,
            "ghost.md",
            "/nonexistent/ghost.md",
            0L,
            null,
            null,
            null,
            now,
            now,
            null,
            Map.of()
        , false));

        mvc.perform(post("/api/sessions/{sessionId}/files/{fileArtifactId}/archive",
                OTHER_SESSION_ID, "fa-archive-missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void postArchive_returns_409_for_wrong_status() throws Exception {
        Instant now = Instant.now();
        repo.insert(new FileArtifact(
            "fa-archive-wrong",
            FileArtifactScope.SESSION,
            FileArtifactStatus.TEMPORARY,
            FileArtifactKind.OTHER,
            SESSION_ID,
            null,
            "temp.md",
            "/abs/sessions/" + SESSION_ID + "/temp.md",
            50L,
            null,
            null,
            null,
            now,
            now,
            null,
            Map.of()
        , false));

        mvc.perform(post("/api/sessions/{sessionId}/files/{fileArtifactId}/archive",
                SESSION_ID, "fa-archive-wrong"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("wrong_status"))
            .andExpect(jsonPath("$.actual").value("temporary"));
    }

    @Test
    void postDiscard_deletes_any_status() throws Exception {
        // Create a real file
        Path sessionDir = workdirRoot.sessionsRoot().resolve(SESSION_ID);
        Files.createDirectories(sessionDir);
        Path srcFile = sessionDir.resolve("discard-test.md");
        Files.writeString(srcFile, "# To be discarded");

        Instant now = Instant.now();
        repo.insert(new FileArtifact(
            "fa-discard-it",
            FileArtifactScope.SESSION,
            FileArtifactStatus.CANDIDATE,
            FileArtifactKind.OTHER,
            SESSION_ID,
            null,
            "discard-test.md",
            srcFile.toAbsolutePath().toString(),
            Files.size(srcFile),
            "text/markdown",
            "Discard Test",
            "",
            now,
            now,
            null,
            Map.of()
        , false));

        mvc.perform(post("/api/files/{fileArtifactId}/discard", "fa-discard-it"))
            .andExpect(status().isNoContent());

        // Verify row is discarded
        FileArtifact discarded = repo.findById("fa-discard-it").orElseThrow();
        assertThat(discarded.status()).isEqualTo(FileArtifactStatus.DISCARDED);

        // Verify file moved to _trash
        Path trashDir = workdirRoot.trashRoot();
        boolean foundInTrash = Files.list(trashDir)
            .anyMatch(p -> p.getFileName().toString().startsWith(SESSION_ID + "__fa-discard-it__"));
        assertThat(foundInTrash).isTrue();

        // Source file should be gone
        assertThat(Files.exists(srcFile)).isFalse();

        // Cleanup trash
        Files.list(trashDir)
            .filter(p -> p.getFileName().toString().contains("fa-discard-it"))
            .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
    }

    @Test
    void postDiscard_idempotent_on_discarded() throws Exception {
        Instant now = Instant.now();
        repo.insert(new FileArtifact(
            "fa-discard-done",
            FileArtifactScope.SESSION,
            FileArtifactStatus.DISCARDED,
            FileArtifactKind.OTHER,
            SESSION_ID,
            null,
            "already-discarded.md",
            "/abs/sessions/" + SESSION_ID + "/already-discarded.md",
            10L,
            null,
            null,
            null,
            now,
            now,
            null,
            Map.of()
        , false));

        // Discarding an already-discarded row returns 204 (idempotent)
        mvc.perform(post("/api/files/{fileArtifactId}/discard", "fa-discard-done"))
            .andExpect(status().isNoContent());
    }

    private static FileArtifact stub(String id, FileArtifactStatus status, String sessionId, String connectionId) {
        Instant now = Instant.now();
        return new FileArtifact(
            id,
            FileArtifactScope.SESSION,
            status,
            FileArtifactKind.OTHER,
            sessionId,
            connectionId,
            id + ".md",
            "/abs/sessions/" + sessionId + "/" + id + ".md",
            100L,
            "text/markdown",
            null,
            null,
            now,
            now,
            null,
            Map.of()
        , false);
    }
}
