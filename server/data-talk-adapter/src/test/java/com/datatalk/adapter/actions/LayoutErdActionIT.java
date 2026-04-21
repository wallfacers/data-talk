package com.datatalk.adapter.actions;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
class LayoutErdActionIT {

    private static final String DATABASE_NAME =
        "mem:layout-erd-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired ConnectionService conn;
    @Autowired LayoutErdAction action;
    @Autowired SessionRepository sessions;
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
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT)");
            st.execute("CREATE TABLE orders (id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id), amount DECIMAL)");
        }
        connectionId = conn.create("Layout ERD Test", ConnectionKind.H2, "local", 0,
            DATABASE_NAME, "sa", "", null);
        sessions.upsert(new SessionRecord("s-1", connectionId, "ERD", true, "oc-1", 0L, 0L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsTwoNodesAndOneEdge() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-erd", connectionId, "oc-1"),
            Map.of("connectionId", connectionId, "tables", List.of("users", "orders"))
        ).toCompletableFuture().get();

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) out.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) out.get("edges");
        assertThat(nodes).hasSize(2);
        assertThat(edges).hasSize(1);
    }
}
