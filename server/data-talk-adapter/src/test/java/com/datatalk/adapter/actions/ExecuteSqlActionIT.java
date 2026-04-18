package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ExecuteSqlActionIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired ExecuteSqlAction action;
    @Autowired ArtifactRepository artifacts;

    String connectionId;

    @BeforeAll
    void seed() throws Exception {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE TABLE t(id INT, name TEXT)");
            st.execute("INSERT INTO t VALUES(1,'a'),(2,'b'),(3,'c')");
        }
        conn.deleteAll();
        connectionId = conn.create("Execute SQL Test", "postgresql", pg.getHost(), pg.getFirstMappedPort(),
            pg.getDatabaseName(), pg.getUsername(), pg.getPassword(), null);
        sessRepo.upsert(new SessionRecord("s-exec", connectionId, "T", true, "oc-e", 0L, 0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPreviewAndPersistsArtifact() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-1", connectionId, "oc-e"),
            Map.of("connectionId", connectionId, "sql", "SELECT * FROM t ORDER BY id")
        ).toCompletableFuture().get();

        assertThat(out).containsKeys("artifactId", "columns", "preview", "rowCount");
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
