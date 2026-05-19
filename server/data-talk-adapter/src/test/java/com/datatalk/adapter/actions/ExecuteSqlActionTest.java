package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.sql.SqlPendingConfirmationStore;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecuteSqlActionTest {

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired ExecuteSqlAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;
    @Autowired SqlPendingConfirmationStore confirmationStore;

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
            null,
            null,
            null,
            null,
            null,
            null,
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
                ActionExecutionMetadata.aiInitiated(new SqlExecutionRisk(RiskLevel.L1, "select", false, false))
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
                ActionExecutionMetadata.aiInitiated(new SqlExecutionRisk(RiskLevel.L1, "select", false, false))
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

    // === New tests for conversational confirmation flow ===

    @Test
    @SuppressWarnings("unchecked")
    void delete_returnsRequiresConfirmation() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-del", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "DELETE FROM t WHERE id = 1")
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "requires_confirmation");
        assertThat(out).containsKey("confirmationId");
        assertThat(out).containsKey("sqlPreview");
        assertThat(out).containsKey("affectedObjects");
        assertThat(out).containsKey("message");
        // No artifact created — SQL not yet executed
        assertThat(artifacts.findBySession("s-exec")).isEmpty();
        // Confirmation ID is a valid UUID stored in the confirmation store
        String confirmationId = (String) out.get("confirmationId");
        assertThat(confirmationStore.get(confirmationId)).isPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteWithoutWhere_returnsRequiresConfirmation() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-del-all", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "DELETE FROM t")
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "requires_confirmation");
        assertThat(out).containsKey("confirmationId");
        assertThat(out).containsKey("sqlPreview");
        assertThat(artifacts.findBySession("s-exec")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmationWithValidConfirmationId_executesSuccessfully() throws Exception {
        // First call: DELETE returns requires_confirmation
        Map<String, Object> pending = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-conf-1", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "DELETE FROM t WHERE id = 1")
        ).toCompletableFuture().get();

        assertThat(pending).containsEntry("status", "requires_confirmation");
        String confirmationId = (String) pending.get("confirmationId");

        // Second call: confirm with the confirmationId
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-conf-2", connectionId, "oc-e"),
            Map.of("confirmationId", confirmationId)
        ).toCompletableFuture().get();

        assertThat(out).containsKey("artifactId");
        assertThat(out).containsKey("columns");
        assertThat(out).containsKey("preview");
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
        // Confirmation should be consumed
        assertThat(confirmationStore.get(confirmationId)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmationWithExpiredConfirmationId_returnsError() throws Exception {
        // Manually create and then remove a confirmation to simulate expiration
        String confirmationId = confirmationStore.create(
            new SqlPendingConfirmationStore.PendingConfirmation(
                "DELETE FROM t WHERE id = 1",
                connectionId,
                "s-exec",
                "mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                null,
                "ai",
                List.of("t")
            )
        );
        // Remove it to simulate expiration
        confirmationStore.remove(confirmationId);

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-expired", connectionId, "oc-e"),
            Map.of("confirmationId", confirmationId)
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "confirmation_invalid");
        assertThat(out).containsKey("message");
        assertThat(out).doesNotContainKey("artifactId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmationWithInvalidConfirmationId_returnsError() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-invalid", connectionId, "oc-e"),
            Map.of("confirmationId", "nonexistent-id-12345")
        ).toCompletableFuture().get();

        assertThat(out).containsEntry("status", "confirmation_invalid");
        assertThat(out).containsKey("message");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonDeleteDml_insertDoesNotRequireConfirmation() throws Exception {
        // INSERT should NOT require confirmation — it bypasses the DELETE gate.
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-ins", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "INSERT INTO t VALUES(4,'d')")
        ).toCompletableFuture().get();

        // INSERT executes directly via executeUpdate, returns affectedRows
        assertThat(out).containsKey("artifactId");
        assertThat(out).containsEntry("affectedRows", 1);
        assertThat(out).doesNotContainEntry("status", "requires_confirmation");
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonDeleteDml_updateDoesNotRequireConfirmation() throws Exception {
        // UPDATE should NOT require confirmation on the AI path.
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-upd", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "UPDATE t SET name = 'x' WHERE id = 1")
        ).toCompletableFuture().get();

        // UPDATE executes directly via executeUpdate, returns affectedRows
        assertThat(out).containsKey("artifactId");
        assertThat(out).containsEntry("affectedRows", 1);
        assertThat(out).doesNotContainEntry("status", "requires_confirmation");
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectExecutesDirectly_withoutConfirmation() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-sel", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "SELECT COUNT(*) FROM t")
        ).toCompletableFuture().get();

        assertThat(out).containsKey("artifactId");
        assertThat(out).doesNotContainKey("status");
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiCannotBypassConfirmationViaConfirmedFlag() throws Exception {
        // DELETE should always require confirmation regardless of any confirmed flag
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-bypass", connectionId, "oc-e"),
            Map.of(
                "connectionId", connectionId,
                "sql", "DELETE FROM t",
                "confirmed", true,
                "riskAck", "L3"
            )
        ).toCompletableFuture().get();

        // DELETE always requires confirmation — confirmed flag is ignored
        assertThat(out).containsEntry("status", "requires_confirmation");
        assertThat(out).doesNotContainKey("artifactId");
        // Rows in the user H2 DB are unchanged — DELETE never executed
        try (var c = DriverManager.getConnection("jdbc:h2:mem:execsql;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM t")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }
}
