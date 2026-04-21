package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import org.springframework.beans.factory.annotation.Qualifier;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ReadSchemaActionIT {
    private static final String DB_NAME = "mem:readschema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Autowired ConnectionService conn;
    @Autowired ReadSchemaAction action;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;

    @BeforeEach
    void seedDb() throws Exception {
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + DB_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE SCHEMA IF NOT EXISTS analytics");
            st.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255), created_at TIMESTAMP)");
            st.execute("CREATE TABLE orders (id INT PRIMARY KEY, user_id INT)");
            st.execute("CREATE TABLE analytics.audit_log (id INT PRIMARY KEY, action VARCHAR(255))");
        }
        connectionId = conn.create("Read Schema Test", "h2", "", 0, DB_NAME, "sa", "", null);
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-1", connectionId, "Read Schema", "oc-1", now, now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaReturnsBothTables() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-1", connectionId, "oc-1"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaUsesSessionContextWhenInputOmitsConnection() throws Exception {
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-ctx", connectionId, "Read Schema", "oc-ctx", now, now);
        datatalkJdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, "s-ctx", connectionId, "Read Schema Test", DB_NAME, "PUBLIC", "schema", now);

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-ctx", "c-2", null, "oc-ctx"),
            Map.of()
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }
}
