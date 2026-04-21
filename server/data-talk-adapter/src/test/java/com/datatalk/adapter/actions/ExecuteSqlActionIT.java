package com.datatalk.adapter.actions;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionExecutionMetadata;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.action.SqlExecutionRisk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = DataTalkApplication.class)
@AutoConfigureMockMvc
class ExecuteSqlActionIT {

    private static final String DATABASE_NAME =
        "mem:execute-sql-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired ExecuteSqlAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;

    @BeforeAll
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");
        try (var c = DriverManager.getConnection("jdbc:h2:" + DATABASE_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS t");
            st.execute("CREATE TABLE t(id INT, name TEXT)");
            st.execute("INSERT INTO t VALUES(1,'a'),(2,'b'),(3,'c')");
        }
        connectionId = conn.create("Execute SQL Test", ConnectionKind.H2, "local", 0,
            DATABASE_NAME, "sa", "", null);
        sessRepo.upsert(new SessionRecord("s-exec", connectionId, "T", true, "oc-e", 0L, 0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPreviewAndPersistsArtifact() throws Exception {
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

        assertThat(out).containsKeys("artifactId", "columns", "preview", "rowCount");
        assertThat(out).containsKey("metadata");
        assertThat((Map<String, Object>) out.get("metadata"))
            .containsEntry("riskLevel", "L1")
            .containsEntry("fallbackUsed", false);
        assertThat((List<?>) out.get("preview")).hasSize(3);
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    void rejectsDelete() {
        assertThat(
            action.handle(
                new ActionContext("s-exec", "c-1", connectionId, "oc-e"),
                Map.of("connectionId", connectionId, "sql", "DELETE FROM t")
            ).toCompletableFuture()
        ).isCompletedExceptionally();
    }
}
