package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;

/**
 * Integration tests for two-phase connection deletion (Part 5a).
 *
 * <p>Uses RANDOM_PORT + WebTestClient, consistent with ConnectionControllerIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionDeletionIT {

    @LocalServerPort int port;
    @Autowired ObjectMapper om;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String createConnection(WebTestClient w, String body) throws Exception {
        byte[] res = w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange().expectStatus().isCreated()
            .expectBody().returnResult().getResponseBody();
        JsonNode node = om.readTree(Objects.requireNonNull(res));
        return node.get("id").asText();
    }

    @Test
    void delete_withoutForce_returns_409_when_sessions_exist() throws Exception {
        var w = web();
        String connId = createConnection(w, """
            {"name":"待删连接-有会话","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        // Seed a child session via the session API
        w.post().uri("/api/sessions").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"connectionId":"%s","title":"child session"}
                """.formatted(connId))
            .exchange().expectStatus().isOk();

        // Mark the session as ever-sent so it is not the reusable empty slot
        jdbc.update("UPDATE sessions SET has_ever_sent = 1 WHERE connection_id = ?", connId);

        // DELETE without force should block with 409 + counts
        w.delete().uri("/api/connections/" + connId).exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("connection_has_resources")
            .jsonPath("$.connectionId").isEqualTo(connId)
            .jsonPath("$.counts.sessions").value(org.hamcrest.Matchers.greaterThan(0))
            .jsonPath("$.counts.candidates").isEqualTo(0)
            .jsonPath("$.counts.temporary").isEqualTo(0)
            .jsonPath("$.counts.archived").isEqualTo(0);
    }

    @Test
    void delete_withoutForce_returns_409_when_candidates_exist() throws Exception {
        var w = web();
        String connId = createConnection(w, """
            {"name":"待删连接-有候选","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        String sid = createSessionViaJdbc(connId);

        // Insert a candidate file_artifact row for this session
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO file_artifact(id, scope, status, kind, session_id, connection_id,
                filename, physical_path, size_bytes, mime_type, title, summary,
                created_at, updated_at, archived_at, metadata_json)
            VALUES(?, 'session', 'candidate', 'other', ?, NULL,
                'report.md', '/abs/sessions/%s/report.md', 256, 'text/markdown',
                'Test Report', 'A test candidate', ?, ?, NULL, NULL)
            """.formatted(sid),
            "fa-cand-conn", sid, now, now);

        w.delete().uri("/api/connections/" + connId).exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("connection_has_resources")
            .jsonPath("$.counts.candidates").value(org.hamcrest.Matchers.greaterThan(0));
    }

    @Test
    void delete_withoutForce_returns_409_when_archived_exist() throws Exception {
        var w = web();
        String connId = createConnection(w, """
            {"name":"待删连接-有归档","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO file_artifact(id, scope, status, kind, session_id, connection_id,
                filename, physical_path, size_bytes, mime_type, title, summary,
                created_at, updated_at, archived_at, metadata_json)
            VALUES(?, 'workspace', 'archived', 'other', NULL, ?,
                'archived.md', '/abs/workspaces/%s/archived.md', 512, 'text/markdown',
                'Archived', 'An archived file', ?, ?, ?, NULL)
            """.formatted(connId),
            "fa-arch-conn", connId, now, now, now);

        w.delete().uri("/api/connections/" + connId).exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("connection_has_resources")
            .jsonPath("$.counts.archived").value(org.hamcrest.Matchers.greaterThan(0));
    }

    @Test
    void delete_withoutForce_returns_204_when_no_resources() throws Exception {
        var w = web();
        String connId = createConnection(w, """
            {"name":"待删连接-空","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.delete().uri("/api/connections/" + connId).exchange()
            .expectStatus().isNoContent();

        // Connection row should be gone
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM connections WHERE id = ?", Integer.class, connId);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    @Test
    void delete_withForce_deletes_sessions_and_archived_rows() throws Exception {
        var w = web();
        String connId = createConnection(w, """
            {"name":"强制删除连接","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        String sid = createSessionViaJdbc(connId);

        // Seed a candidate row (will be cleaned up by session force-delete)
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO file_artifact(id, scope, status, kind, session_id, connection_id,
                filename, physical_path, size_bytes, mime_type, title, summary,
                created_at, updated_at, archived_at, metadata_json)
            VALUES(?, 'session', 'candidate', 'other', ?, NULL,
                'temp.md', '/abs/sessions/%s/temp.md', 128, 'text/markdown',
                'Temp', '', ?, ?, NULL, NULL)
            """.formatted(sid),
            "fa-force-cand", sid, now, now);

        // Seed an archived row directly on the connection
        jdbc.update("""
            INSERT INTO file_artifact(id, scope, status, kind, session_id, connection_id,
                filename, physical_path, size_bytes, mime_type, title, summary,
                created_at, updated_at, archived_at, metadata_json)
            VALUES(?, 'workspace', 'archived', 'other', NULL, ?,
                'lib.md', '/abs/workspaces/%s/lib.md', 300, 'text/markdown',
                'Lib', '', ?, ?, ?, NULL)
            """.formatted(connId),
            "fa-force-arch", connId, now, now, now);

        w.delete().uri("/api/connections/" + connId + "?force=true").exchange()
            .expectStatus().isNoContent();

        // Connection row gone
        Integer connCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM connections WHERE id = ?", Integer.class, connId);
        org.assertj.core.api.Assertions.assertThat(connCount).isZero();

        // Session row gone
        Integer sessCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE id = ?", Integer.class, sid);
        org.assertj.core.api.Assertions.assertThat(sessCount).isZero();

        // Candidate row gone (deleted as transient by session force-delete)
        Integer candCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM file_artifact WHERE id = ?", Integer.class, "fa-force-cand");
        org.assertj.core.api.Assertions.assertThat(candCount).isZero();

        // Archived row still exists but with connection_id = NULL and orphanedFromConnection metadata
        String metaStr = jdbc.queryForObject(
            "SELECT metadata_json FROM file_artifact WHERE id = ?",
            String.class, "fa-force-arch");
        JsonNode metaNode = om.readTree(metaStr);
        org.assertj.core.api.Assertions.assertThat(metaNode.get("orphanedFromConnection").asText())
            .isEqualTo("强制删除连接");
        org.assertj.core.api.Assertions.assertThat(metaNode.get("orphanedFromConnectionId").asText())
            .isEqualTo(connId);

        String archivedConnId = jdbc.queryForObject(
            "SELECT connection_id FROM file_artifact WHERE id = ?",
            String.class, "fa-force-arch");
        org.assertj.core.api.Assertions.assertThat(archivedConnId).isNull();
    }

    @Test
    void delete_withForce_returns_404_for_missing_connection() throws Exception {
        var w = web();

        w.delete().uri("/api/connections/nonexistent-conn?force=true").exchange()
            .expectStatus().isNotFound();
    }

    private String createSessionViaJdbc(String connectionId) {
        String sid = "sess-force-" + connectionId;
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT OR IGNORE INTO sessions(id, connection_id, title, has_ever_sent,
                opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 0, NULL, ?, ?, 0)
            """, sid, connectionId, "force-test-session", now, now);
        return sid;
    }
}
