package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    private static final String[] CONNECTION_IDS = {
        "conn-1", "conn-patch", "conn-patch-blank", "conn-del", "conn-cascade", "c-a", "c-b"
    };

    @BeforeEach
    void seedConnectionsAndReset() {
        jdbc.update("DELETE FROM action_invocations");
        jdbc.update("DELETE FROM artifacts");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM query_results");
        jdbc.update("DELETE FROM synthetic_session_messages");
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        for (String id : CONNECTION_IDS) {
            jdbc.update("""
                INSERT OR IGNORE INTO connections(id, name, kind, host, port, username, password_enc, created_at)
                VALUES(?, ?, 'mysql', 'h', 3306, 'u', x'00', 0)
                """, id, "seed-" + id);
        }
    }

    @Test
    void create_then_list_returns_session() throws Exception {
        String body = """
            {"connectionId":"conn-1","title":"冒烟测试"}
            """;

        String created = mvc.perform(post("/api/sessions")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.connectionId").value("conn-1"))
            .andExpect(jsonPath("$.title").value("冒烟测试"))
            .andExpect(jsonPath("$.hasEverSent").value(false))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/sessions").param("connectionId", "conn-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].connectionId").value("conn-1"));
    }

    @Test
    void 创建会话允许省略_connectionId() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"title\":\"无连接会话\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("无连接会话"));
    }

    @Test
    void 创建会话允许_connectionId_显式为_null() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"connectionId\":null,\"title\":\"空连接\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void 创建会话允许_connectionId_为空字符串并视为_null() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"connectionId\":\"\",\"title\":\"空串\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void create_uses_en_locale_for_default_title() throws Exception {
        mvc.perform(post("/api/sessions")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .contentType("application/json")
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("New Session"));
    }

    @Test
    void create_rejects_null_body_with_localized_message() throws Exception {
        mvc.perform(post("/api/sessions")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType("application/json")
                .content("null"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("请求体不能为空"));
    }

    @Test
    void list_without_filter_returns_all() throws Exception {
        String firstId = createSession("c-a", "A");
        jdbc.update("UPDATE sessions SET has_ever_sent = 1 WHERE id = ?", firstId);

        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-b\",\"title\":\"B\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void patch_updates_title() throws Exception {
        String id = createSession("conn-patch", "旧标题");

        mvc.perform(patch("/api/sessions/" + id)
                .contentType("application/json")
                .content("{\"title\":\"新标题\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("新标题"));

        mvc.perform(get("/api/sessions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("新标题"));
    }

    @Test
    void patch_rejects_blank_title() throws Exception {
        String id = createSession("conn-patch-blank", "原标题");

        mvc.perform(patch("/api/sessions/" + id)
                .contentType("application/json")
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void patch_missing_returns_404() throws Exception {
        mvc.perform(patch("/api/sessions/nope")
                .contentType("application/json")
                .content("{\"title\":\"x\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns_204_then_404() throws Exception {
        String id = createSession("conn-del", "待删");

        mvc.perform(delete("/api/sessions/" + id))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/sessions/" + id))
            .andExpect(status().isNotFound());

        mvc.perform(delete("/api/sessions/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_cascades_events() throws Exception {
        String id = createSession("conn-cascade", "含事件");

        jdbc.update("""
            INSERT INTO events(event_id, session_id, event_type, payload_json, ts)
            VALUES(?, ?, ?, ?, ?)
            """, 1L, id, "message.created", "{}", System.currentTimeMillis());

        Integer before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM events WHERE session_id = ?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(before).isEqualTo(1);

        mvc.perform(delete("/api/sessions/" + id))
            .andExpect(status().isNoContent());

        Integer after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM events WHERE session_id = ?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(after).isZero();
    }

    @Test
    void delete_all_cascades_session_related_resources() throws Exception {
        String s1 = createSession("conn-cascade", "会话A");
        String s2 = createSession("conn-cascade", "会话B");
        long now = System.currentTimeMillis();

        jdbc.update("""
            INSERT INTO artifacts(id, version, session_id, kind, produced_by, payload_ref, payload_size,
                                  supersedes_id, supersedes_ver, pinned, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, "art-1", 1, s1, "table", "call-1", "inline:{}", 2, null, null, 0, now);
        jdbc.update("""
            INSERT INTO events(event_id, session_id, event_type, payload_json, ts)
            VALUES(?, ?, ?, ?, ?)
            """, 1L, s1, "message.created", "{}", now);
        jdbc.update("""
            INSERT INTO synthetic_session_messages(id, session_id, kind, text, metadata_json, created_at)
            VALUES(?, ?, ?, ?, ?, ?)
            """, "syn-1", s1, "bang_query", "select 1", "{}", now);
        jdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot,
                                              database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, s1, "conn-cascade", "seed-conn-cascade", "db1", "public", "schema", now);

        Integer beforeSessions = jdbc.queryForObject("SELECT COUNT(*) FROM sessions", Integer.class);
        Integer beforeArtifacts = jdbc.queryForObject("SELECT COUNT(*) FROM artifacts", Integer.class);
        Integer beforeEvents = jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class);
        Integer beforeSynthetic = jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_session_messages", Integer.class);
        Integer beforeContexts = jdbc.queryForObject("SELECT COUNT(*) FROM session_data_contexts", Integer.class);
        org.assertj.core.api.Assertions.assertThat(beforeSessions).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(beforeArtifacts).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(beforeEvents).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(beforeSynthetic).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(beforeContexts).isEqualTo(1);

        mvc.perform(delete("/api/sessions"))
            .andExpect(status().isNoContent());

        Integer afterSessions = jdbc.queryForObject("SELECT COUNT(*) FROM sessions", Integer.class);
        Integer afterArtifacts = jdbc.queryForObject("SELECT COUNT(*) FROM artifacts", Integer.class);
        Integer afterEvents = jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class);
        Integer afterSynthetic = jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_session_messages", Integer.class);
        Integer afterContexts = jdbc.queryForObject("SELECT COUNT(*) FROM session_data_contexts", Integer.class);
        org.assertj.core.api.Assertions.assertThat(afterSessions).isZero();
        org.assertj.core.api.Assertions.assertThat(afterArtifacts).isZero();
        org.assertj.core.api.Assertions.assertThat(afterEvents).isZero();
        org.assertj.core.api.Assertions.assertThat(afterSynthetic).isZero();
        org.assertj.core.api.Assertions.assertThat(afterContexts).isZero();
    }

    private String createSession(String connectionId, String title) throws Exception {
        String body = "{\"connectionId\":\"" + connectionId + "\",\"title\":\"" + title + "\"}";
        String json = mvc.perform(post("/api/sessions")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode node = om.readTree(json);
        return node.get("id").asText();
    }
}
