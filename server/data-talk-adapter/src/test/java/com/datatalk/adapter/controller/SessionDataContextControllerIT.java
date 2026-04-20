package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionDataContextControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void resetAndSeed() {
        jdbc.update("DELETE FROM action_invocations");
        jdbc.update("DELETE FROM artifacts");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM query_results");
        jdbc.update("DELETE FROM synthetic_session_messages");
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        jdbc.update("DELETE FROM connections");
        jdbc.update("""
            INSERT INTO connections(id, name, kind, host, port, database_name, username, password_enc, created_at, connect_timeout)
            VALUES('c1', '主库', 'postgres', 'localhost', 5432, 'app_db', 'u', x'00', 0, 3000)
            """);
    }

    @Test
    void get_returns_empty_context_before_any_set() throws Exception {
        String sessionId = createSession();

        mvc.perform(get("/api/sessions/" + sessionId + "/data-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.connectionId").doesNotExist())
            .andExpect(jsonPath("$.database").doesNotExist())
            .andExpect(jsonPath("$.schema").doesNotExist())
            .andExpect(jsonPath("$.selectedLevel").doesNotExist());
    }

    @Test
    void put_persists_context_and_returns_snapshot() throws Exception {
        String sessionId = createSession();

        mvc.perform(put("/api/sessions/" + sessionId + "/data-context")
                .contentType("application/json")
                .content("""
                    {"connectionId":"c1","database":"analytics","schema":"public","selectedLevel":"schema"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.connectionId").value("c1"))
            .andExpect(jsonPath("$.connectionNameSnapshot").value("主库"))
            .andExpect(jsonPath("$.database").value("analytics"))
            .andExpect(jsonPath("$.schema").value("public"))
            .andExpect(jsonPath("$.selectedLevel").value("schema"));

        mvc.perform(get("/api/sessions/" + sessionId + "/data-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connectionId").value("c1"))
            .andExpect(jsonPath("$.database").value("analytics"))
            .andExpect(jsonPath("$.schema").value("public"));
    }

    @Test
    void put_with_null_connection_clears_context() throws Exception {
        String sessionId = createSession();
        jdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, 'c1', '主库', 'analytics', 'public', 'schema', 123)
            """, sessionId);

        mvc.perform(put("/api/sessions/" + sessionId + "/data-context")
                .contentType("application/json")
                .content("""
                    {"connectionId":null,"database":"ignored","schema":"ignored","selectedLevel":"database"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.connectionId").doesNotExist())
            .andExpect(jsonPath("$.database").doesNotExist())
            .andExpect(jsonPath("$.schema").doesNotExist())
            .andExpect(jsonPath("$.selectedLevel").doesNotExist());
    }

    @Test
    void delete_session_cascades_data_context() throws Exception {
        String sessionId = createSession();
        jdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, 'c1', '主库', 'analytics', 'public', 'schema', 123)
            """, sessionId);

        mvc.perform(delete("/api/sessions/" + sessionId))
            .andExpect(status().isNoContent());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM session_data_contexts WHERE session_id = ?",
            Integer.class,
            sessionId
        );
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    private String createSession() throws Exception {
        String json = mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("""
                    {"connectionId":"c1","title":"上下文测试"}
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode node = om.readTree(json);
        return node.get("id").asText();
    }
}
