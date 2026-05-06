package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionExecutionMetadata;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.action.SqlExecutionRisk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecuteSqlActionTest {

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired ExecuteSqlAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    private String connectionId;

    @BeforeEach
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS t");
            st.execute("CREATE TABLE t(id INT, name VARCHAR(255))");
            st.execute("INSERT INTO t VALUES(1,'a'),(2,'b'),(3,'c')");
        }

        connectionId = conn.create(
            "Execute SQL Test",
            "h2",
            "",
            0,
            "mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            null,
            null
        );
        sessRepo.upsert(new SessionRecord("s-exec", connectionId, "T", true, "oc-e", 0L, 0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void l1SelectExecutesAndReturnsArtifact() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext(
                "s-exec",
                "c-1",
                connectionId,
                "oc-e",
                new ActionExecutionMetadata(new SqlExecutionRisk(RiskLevel.L1, "select", false, false))
            ),
            Map.of("connectionId", connectionId, "sql", "SELECT * FROM t ORDER BY id")
        ).toCompletableFuture().get();

        assertThat(out).containsKeys("artifactId", "columns", "preview", "rowCount", "metadata");
        assertThat((Map<String, Object>) out.get("metadata"))
            .containsEntry("riskLevel", "L1")
            .containsEntry("fallbackUsed", false);
        assertThat((List<?>) out.get("preview")).hasSize(3);
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void falls_back_to_session_data_context_when_input_omits_connection() throws Exception {
        datatalkJdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, "s-exec", connectionId, "Execute SQL Test", "mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "PUBLIC", "schema", 1L);

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext(
                "s-exec",
                "c-2",
                null,
                "oc-e",
                new ActionExecutionMetadata(new SqlExecutionRisk(RiskLevel.L1, "select", false, false))
            ),
            Map.of("sql", "SELECT * FROM t ORDER BY id")
        ).toCompletableFuture().get();

        assertThat((List<?>) out.get("preview")).hasSize(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageSizeLimitsRowsAndReportsTruncation() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-page", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "SELECT * FROM t ORDER BY id", "pageSize", 2)
        ).toCompletableFuture().get();

        assertThat((List<?>) out.get("preview")).hasSize(2);
        assertThat(out)
            .containsEntry("rowCount", 2)
            .containsEntry("truncated", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializes_unsafe_bigint_preview_cells_as_strings() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-3", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "SELECT CAST(9007199254740993 AS BIGINT) AS big_id")
        ).toCompletableFuture().get();

        List<Map<String, Object>> preview = (List<Map<String, Object>>) out.get("preview");
        assertThat(preview).hasSize(1);
        assertThat(preview.get(0).get("BIG_ID")).isEqualTo("9007199254740993");
    }

    @Test
    @SuppressWarnings("unchecked")
    void l2_returnsBlockedInChat() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-l2", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "DELETE FROM t WHERE id = 1")
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "blocked_in_chat");
        Map<String, Object> risk = (Map<String, Object>) out.get("risk");
        assertThat(risk).containsEntry("level", "L2");
        assertThat(risk).containsKey("reason");
        assertThat(risk).containsKey("affectedObjects");
        assertThat(out).containsEntry("sqlPreview", "DELETE FROM t WHERE id = 1");
        // No artifact created — chat path never executes mutating SQL.
        assertThat(artifacts.findBySession("s-exec")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void l3_returnsBlockedInChat() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-l3", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "DELETE FROM t")
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "blocked_in_chat");
        Map<String, Object> risk = (Map<String, Object>) out.get("risk");
        assertThat(risk).containsEntry("level", "L3");
        assertThat(out).containsEntry("sqlPreview", "DELETE FROM t");
        assertThat(artifacts.findBySession("s-exec")).isEmpty();
    }

    /**
     * Security boundary: AI cannot bypass the user-facing confirmation by
     * setting {@code confirmed=true} + matching {@code riskAck} in tool input.
     * SERVER executor actions have no pause-resume primitive, so honoring
     * those flags would let the AI execute L3 SQL with no user signal.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aiCannotBypassConfirmationViaToolInput() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-bypass", connectionId, "oc-e"),
            Map.of(
                "connectionId", connectionId,
                "sql", "DELETE FROM t",
                "confirmed", true,
                "riskAck", "L3"
            )
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "blocked_in_chat");
        assertThat(out).doesNotContainKey("artifactId");
        // Rows in the user H2 DB are unchanged — DELETE never executed.
        try (var c = DriverManager.getConnection("jdbc:h2:mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM t")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }
}
