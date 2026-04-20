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
    @Autowired JdbcTemplate datatalkJdbc;

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
            null
        );
        sessRepo.upsert(new SessionRecord("s-exec", connectionId, "T", true, "oc-e", 0L, 0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPreviewAndRiskMetadata() throws Exception {
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
}
