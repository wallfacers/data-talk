package com.datatalk.adapter.controller;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class ErTabControllerIT {

    private static final String DB =
        "mem:er-controller-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired ConnectionService conn;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;

    @BeforeAll
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + DB, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255))");
            st.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id))");
        }
        connectionId = conn.create("ER ctrl IT", ConnectionKind.H2, "local", 0, DB, "sa", "", null);
    }

    @Test
    void seedInspectorReturns200WithGraph() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", connectionId,
            "tables", List.of("orders"),
            "neighborDepth", 1
        ));

        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes").isArray())
            .andExpect(jsonPath("$.edges", hasSize(1)))
            .andExpect(jsonPath("$.summary").isString());
    }

    @Test
    void seedInspectorMissingTableReturns404() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", connectionId,
            "tables", List.of("orde"),
            "neighborDepth", 0
        ));

        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("tables_not_found"))
            .andExpect(jsonPath("$.aiHint").isString());
    }

    @Test
    void seedInspectorUnknownConnectionReturns404() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", "missing-conn",
            "tables", List.of("orders"),
            "neighborDepth", 0
        ));

        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("connection_unavailable"));
    }

}
