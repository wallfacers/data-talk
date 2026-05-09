package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
class JdbcFileArtifactRepositoryIT {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    JdbcTemplate jdbc;

    JdbcFileArtifactRepository repo;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS file_artifact");
        jdbc.execute("""
                CREATE TABLE file_artifact (
                    id VARCHAR(255) PRIMARY KEY,
                    scope VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    kind VARCHAR(32) NOT NULL,
                    session_id VARCHAR(255),
                    connection_id VARCHAR(255),
                    filename VARCHAR(1024) NOT NULL,
                    physical_path VARCHAR(4096) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    mime_type VARCHAR(255),
                    title VARCHAR(1024),
                    summary CLOB,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    archived_at BIGINT,
                    metadata_json CLOB,
                    "external" INTEGER NOT NULL DEFAULT 0
                )
                """);
        repo = new JdbcFileArtifactRepository(jdbc, new ObjectMapper());
    }

    @Test
    void insert_then_findById_round_trips_all_columns() {
        Instant created = Instant.parse("2026-04-29T09:00:00Z");
        Instant updated = Instant.parse("2026-04-29T09:05:00Z");
        FileArtifact artifact = new FileArtifact(
                "file_artifact_1",
                FileArtifactScope.SESSION,
                FileArtifactStatus.TEMPORARY,
                FileArtifactKind.REPORT,
                "ses_abc",
                null,
                "report.md",
                "/abs/sessions/ses_abc/report.md",
                123L,
                "text/markdown",
                "Report",
                "Short summary",
                created,
                updated,
                null,
                Map.of("declared", true, "rows", 42), false);

        repo.insert(artifact);

        var loaded = repo.findById("file_artifact_1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(artifact);

        repo.insert(sample("file_artifact_empty_meta", FileArtifactStatus.TEMPORARY, "ses_x", null));
        String metadataJson = jdbc.queryForObject(
                "SELECT metadata_json FROM file_artifact WHERE id = ?",
                String.class,
                "file_artifact_empty_meta");
        assertThat(metadataJson).isNull();
    }

    @Test
    void findById_returns_empty_for_missing_id_and_tolerates_invalid_metadata_json() {
        long now = Instant.parse("2026-04-29T09:00:00Z").toEpochMilli();
        jdbc.update("""
                INSERT INTO file_artifact (
                    id, scope, status, kind, session_id, connection_id, filename, physical_path,
                    size_bytes, mime_type, title, summary, created_at, updated_at, archived_at, metadata_json,
                    "external"
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "bad_meta",
                "session",
                "temporary",
                "other",
                "ses_x",
                null,
                "x.md",
                "/abs/sessions/ses_x/x.md",
                1L,
                null,
                null,
                null,
                now,
                now,
                null,
                "{not-json",
                false);

        assertThat(repo.findById("missing")).isEmpty();
        assertThat(repo.findById("bad_meta")).isPresent().get()
                .extracting(FileArtifact::metadata)
                .isEqualTo(Map.of());
    }

    @Test
    void findBySession_returns_all_statuses_for_that_session_ordered_newest_first() {
        repo.insert(sampleAt("old", FileArtifactStatus.TEMPORARY, "ses_x", null, "2026-04-29T09:00:00Z"));
        repo.insert(sampleAt("new", FileArtifactStatus.CANDIDATE, "ses_x", null, "2026-04-29T10:00:00Z"));
        repo.insert(sampleAt("other", FileArtifactStatus.TEMPORARY, "ses_other", null, "2026-04-29T11:00:00Z"));

        List<FileArtifact> rows = repo.findBySession("ses_x");

        assertThat(rows).extracting(FileArtifact::id).containsExactly("new", "old");
    }

    @Test
    void findArchivedByConnection_filters_archived_workspace_rows_ordered_newest_first() {
        repo.insert(archivedAt("old", "conn_p", "2026-04-29T09:00:00Z"));
        repo.insert(archivedAt("new", "conn_p", "2026-04-29T10:00:00Z"));
        repo.insert(archivedAt("other_conn", "conn_other", "2026-04-29T11:00:00Z"));
        repo.insert(sample("candidate", FileArtifactStatus.CANDIDATE, "ses_x", "conn_p"));

        var rows = repo.findArchivedByConnection("conn_p");

        assertThat(rows).extracting(FileArtifact::id).containsExactly("new", "old");
    }

    @Test
    void findCandidatesBySession_filters_status() {
        repo.insert(sample("temporary", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("candidate", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(sample("other_session", FileArtifactStatus.CANDIDATE, "ses_other", null));

        var candidates = repo.findCandidatesBySession("ses_x");

        assertThat(candidates).extracting(FileArtifact::id).containsExactly("candidate");
    }

    @Test
    void updateStatus_updates_status_and_updatedAt() {
        repo.insert(sampleAt("artifact", FileArtifactStatus.TEMPORARY, "ses_x", null, "2020-01-01T00:00:00Z"));

        repo.updateStatus("artifact", FileArtifactStatus.CANDIDATE);

        var loaded = repo.findById("artifact").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FileArtifactStatus.CANDIDATE);
        assertThat(loaded.updatedAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void updateLocation_updates_status_scope_path_connection_and_updatedAt() {
        repo.insert(sampleAt("artifact", FileArtifactStatus.TEMPORARY, "ses_x", null, "2020-01-01T00:00:00Z"));

        repo.updateLocation(
                "artifact",
                FileArtifactStatus.DISCARDED,
                FileArtifactScope.WORKSPACE.dbValue(),
                "/abs/trash/artifact.md",
                "conn_p");

        var loaded = repo.findById("artifact").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FileArtifactStatus.DISCARDED);
        assertThat(loaded.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(loaded.physicalPath()).isEqualTo("/abs/trash/artifact.md");
        assertThat(loaded.connectionId()).isEqualTo("conn_p");
        assertThat(loaded.updatedAt()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void markArchived_sets_workspace_status_path_connection_archivedAt_and_updatedAt() {
        repo.insert(sampleAt("candidate", FileArtifactStatus.CANDIDATE, "ses_x", null, "2020-01-01T00:00:00Z"));

        repo.markArchived("candidate", "conn_p", "/abs/workspaces/conn_p/report.md");

        var loaded = repo.findById("candidate").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FileArtifactStatus.ARCHIVED);
        assertThat(loaded.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(loaded.connectionId()).isEqualTo("conn_p");
        assertThat(loaded.physicalPath()).isEqualTo("/abs/workspaces/conn_p/report.md");
        assertThat(loaded.archivedAtOpt()).isPresent();
        assertThat(loaded.updatedAt()).isEqualTo(loaded.archivedAt());
    }

    @Test
    void session_delete_helpers_delete_transient_and_detach_archived_only_for_session() {
        repo.insert(sample("temporary", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("candidate", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(archivedForSession("archived", "ses_x", "conn_p"));
        repo.insert(sample("other_session", FileArtifactStatus.TEMPORARY, "ses_other", null));

        repo.deleteTransientByForSession("ses_x");
        repo.detachArchivedFromSession("ses_x");

        assertThat(repo.findBySession("ses_x")).isEmpty();
        assertThat(repo.findById("temporary")).isEmpty();
        assertThat(repo.findById("candidate")).isEmpty();
        assertThat(repo.findById("archived")).isPresent().get()
                .extracting(FileArtifact::sessionId)
                .isNull();
        assertThat(repo.findById("other_session")).isPresent();
    }

    @Test
    void updateMetadata_updates_size_and_updatedAt_epoch_millis_and_deleteById_removes_one_row() {
        repo.insert(sample("artifact", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("other", FileArtifactStatus.TEMPORARY, "ses_x", null));
        Instant target = Instant.parse("2026-04-29T10:00:00Z");

        repo.updateMetadata("artifact", 9999L, target.toEpochMilli());

        var loaded = repo.findById("artifact").orElseThrow();
        assertThat(loaded.sizeBytes()).isEqualTo(9999L);
        assertThat(loaded.updatedAt()).isEqualTo(target);

        repo.deleteById("artifact");
        assertThat(repo.findById("artifact")).isEmpty();
        assertThat(repo.findById("other")).isPresent();
    }

    @Test
    void findByPhysicalPath_returns_row_when_present() {
        repo.insert(samplePath("a1", "/abs/sessions/ses_x/foo.md"));

        var loaded = repo.findByPhysicalPath("/abs/sessions/ses_x/foo.md");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo("a1");
    }

    @Test
    void findByPhysicalPath_returns_empty_when_absent() {
        assertThat(repo.findByPhysicalPath("/abs/missing.md")).isEmpty();
    }

    @Test
    void findAllSessionScoped_returns_only_session_rows() {
        repo.insert(sample("s1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("s2", FileArtifactStatus.CANDIDATE, "ses_y", null));
        repo.insert(archivedForSession("w1", "ses_x", "conn_p"));

        var rows = repo.findAllSessionScoped();

        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    void findAllWorkspaceScopedArchived_returns_only_workspace_archived_rows() {
        repo.insert(sample("s1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(archivedAt("w1", "conn_p", "2026-04-29T09:00:00Z"));
        repo.insert(archivedAt("w2", "conn_q", "2026-04-29T09:00:00Z"));

        var rows = repo.findAllWorkspaceScopedArchived();

        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("w1", "w2");
    }

    @Test
    void countCandidatesBySession_returns_count_of_candidate_rows() {
        repo.insert(sample("temp1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("cand1", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(sample("cand2", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(sample("cand_other", FileArtifactStatus.CANDIDATE, "ses_y", null));

        assertThat(repo.countCandidatesBySession("ses_x")).isEqualTo(2);
        assertThat(repo.countCandidatesBySession("ses_y")).isEqualTo(1);
        assertThat(repo.countCandidatesBySession("ses_empty")).isEqualTo(0);
    }

    @Test
    void countResourcesByConnection_aggregates_counts_across_sessions_and_archived() {
        // Session 1: 1 candidate, 1 temporary
        repo.insert(sample("s1_cand", FileArtifactStatus.CANDIDATE, "ses_1", "conn_p"));
        repo.insert(sample("s1_temp", FileArtifactStatus.TEMPORARY, "ses_1", "conn_p"));
        // Session 2: 2 candidates, 0 temporary
        repo.insert(sample("s2_cand1", FileArtifactStatus.CANDIDATE, "ses_2", "conn_p"));
        repo.insert(sample("s2_cand2", FileArtifactStatus.CANDIDATE, "ses_2", "conn_p"));
        // Archived directly on connection
        repo.insert(archivedAt("arch1", "conn_p", "2026-04-29T09:00:00Z"));
        repo.insert(archivedAt("arch2", "conn_p", "2026-04-29T10:00:00Z"));
        // Archived on other connection (should not count)
        repo.insert(archivedAt("arch_other", "conn_q", "2026-04-29T09:00:00Z"));

        FileArtifactRepository.ConnectionResourceCounts counts =
                repo.countResourcesByConnection("conn_p", List.of("ses_1", "ses_2"));

        assertThat(counts.sessions()).isEqualTo(2);
        assertThat(counts.candidates()).isEqualTo(3);
        assertThat(counts.temporary()).isEqualTo(1);
        assertThat(counts.archived()).isEqualTo(2);
    }

    @Test
    void deleteTransientByForConnection_bulk_deletes_transient_and_candidate_rows() {
        repo.insert(sample("s1_temp", FileArtifactStatus.TEMPORARY, "ses_1", null));
        repo.insert(sample("s1_cand", FileArtifactStatus.CANDIDATE, "ses_1", null));
        repo.insert(sample("s2_temp", FileArtifactStatus.TEMPORARY, "ses_2", null));
        repo.insert(archivedForSession("s1_arch", "ses_1", "conn_p")); // should remain
        repo.insert(sample("s_other", FileArtifactStatus.TEMPORARY, "ses_other", null));

        repo.deleteTransientByForConnection(List.of("ses_1", "ses_2"));

        assertThat(repo.findById("s1_temp")).isEmpty();
        assertThat(repo.findById("s1_cand")).isEmpty();
        assertThat(repo.findById("s2_temp")).isEmpty();
        assertThat(repo.findById("s1_arch")).isPresent();
        assertThat(repo.findById("s_other")).isPresent();
    }

    @Test
    void deleteTransientByForConnection_emptyList_doesNothing() {
        // Insert some data to verify it's not deleted
        repo.insert(sample("fa_test", FileArtifactStatus.TEMPORARY, "ses_test", null));
        repo.deleteTransientByForConnection(List.of());
        // Should still exist
        assertThat(repo.findById("fa_test")).isPresent();
    }

    @Test
    void detachArchivedFromConnection_sets_connection_null_and_stamps_orphan_metadata() {
        repo.insert(archivedAt("w1", "conn_p", "2026-04-29T09:00:00Z"));
        repo.insert(archivedAt("w2", "conn_p", "2026-04-29T10:00:00Z"));
        repo.insert(archivedAt("w_other", "conn_q", "2026-04-29T09:00:00Z"));

        long deletedAt = Instant.parse("2026-05-07T01:00:00Z").toEpochMilli();
        repo.detachArchivedFromConnection("conn_p", "Production DB", deletedAt);

        // Verify w1 and w2 have connection_id = NULL
        var w1 = repo.findById("w1").orElseThrow();
        var w2 = repo.findById("w2").orElseThrow();
        assertThat(w1.connectionId()).isNull();
        assertThat(w2.connectionId()).isNull();

        // Verify orphan metadata stamped
        assertThat(w1.metadata()).containsEntry("orphanedFromConnection", "Production DB");
        assertThat(w1.metadata()).containsEntry("orphanedFromConnectionId", "conn_p");
        assertThat(w1.metadata()).containsEntry("orphanedAt", deletedAt);

        assertThat(w2.metadata()).containsEntry("orphanedFromConnection", "Production DB");
        assertThat(w2.metadata()).containsEntry("orphanedFromConnectionId", "conn_p");
        assertThat(w2.metadata()).containsEntry("orphanedAt", deletedAt);

        // w_other should be untouched
        var wOther = repo.findById("w_other").orElseThrow();
        assertThat(wOther.connectionId()).isEqualTo("conn_q");
        assertThat(wOther.metadata()).doesNotContainKey("orphanedFromConnection");
    }

    private static FileArtifact sample(String id, FileArtifactStatus status, String sessionId, String connectionId) {
        return sampleAt(id, status, sessionId, connectionId, "2026-04-29T09:00:00Z");
    }

    private static FileArtifact samplePath(String id, String physicalPath) {
        Instant now = Instant.parse("2026-04-29T09:00:00Z");
        return new FileArtifact(
                id,
                FileArtifactScope.SESSION,
                FileArtifactStatus.TEMPORARY,
                FileArtifactKind.OTHER,
                "ses_x",
                null,
                "x.md",
                physicalPath,
                123L,
                "text/markdown",
                null,
                null,
                now,
                now,
                null,
                Map.of(), false);
    }

    private static FileArtifact sampleAt(
            String id,
            FileArtifactStatus status,
            String sessionId,
            String connectionId,
            String createdAt) {
        Instant created = Instant.parse(createdAt);
        return new FileArtifact(
                id,
                FileArtifactScope.SESSION,
                status,
                FileArtifactKind.OTHER,
                sessionId,
                connectionId,
                "x.md",
                "/abs/sessions/" + sessionId + "/x.md",
                123L,
                "text/markdown",
                null,
                null,
                created,
                created,
                null,
                Map.of(), false);
    }

    private static FileArtifact archivedAt(String id, String connectionId, String archivedAt) {
        Instant archivedAtInstant = Instant.parse(archivedAt);
        return new FileArtifact(
                id,
                FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT,
                null,
                connectionId,
                "x.md",
                "/abs/workspaces/" + connectionId + "/x.md",
                123L,
                "text/markdown",
                null,
                null,
                archivedAtInstant,
                archivedAtInstant,
                archivedAtInstant,
                Map.of(), false);
    }

    private static FileArtifact archivedForSession(String id, String sessionId, String connectionId) {
        Instant now = Instant.parse("2026-04-29T09:00:00Z");
        return new FileArtifact(
                id,
                FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT,
                sessionId,
                connectionId,
                "x.md",
                "/abs/workspaces/" + (connectionId != null ? connectionId : "orphan") + "/x.md",
                123L,
                "text/markdown",
                null,
                null,
                now,
                now,
                now,
                Map.of(), false);
    }

    private static FileArtifact insertArchived(String connectionId) {
        return archivedForSession("fa_" + java.util.UUID.randomUUID().toString().substring(0, 6), null, connectionId);
    }

    @Test
    void findOrphanedArchived_returns_archived_rows_with_null_connection_id() {
        repo.insert(insertArchived(null)); // connection_id = NULL
        repo.insert(insertArchived("conn_x"));
        repo.insert(insertArchived(null));

        var rows = repo.findOrphanedArchived(200);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.connectionId() == null);
    }

    @Test
    void reattachArchived_updates_connection_id_and_path() {
        String id = "fa_reattach";
        repo.insert(archivedForSession(id, null, null));
        repo.reattachArchived(id, "conn_new", "/tmp/workspaces/conn_new/orders.md", 1_000L);

        var row = repo.findById(id).orElseThrow();
        assertThat(row.connectionId()).isEqualTo("conn_new");
        assertThat(row.physicalPath()).isEqualTo("/tmp/workspaces/conn_new/orders.md");
    }

    @Test
    void deleteDiscardedById_only_deletes_discarded_rows() {
        String did = "fa_discarded";
        String cid = "fa_candidate";
        repo.insert(sample(did, FileArtifactStatus.DISCARDED, "ses_a", null));
        repo.insert(sample(cid, FileArtifactStatus.CANDIDATE, "ses_a", null));

        repo.deleteDiscardedById(did);
        repo.deleteDiscardedById(cid); // no-op — status mismatch

        assertThat(repo.findById(did)).isEmpty();
        assertThat(repo.findById(cid)).isPresent();
    }

    @Test
    void countOrphanedArchived_counts_correctly() {
        repo.insert(insertArchived(null));
        repo.insert(insertArchived(null));
        repo.insert(insertArchived("conn_x"));

        assertThat(repo.countOrphanedArchived()).isEqualTo(2);
    }
}
