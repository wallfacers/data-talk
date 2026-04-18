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
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM sessions");
        for (String id : CONNECTION_IDS) {
            jdbc.update("""
                INSERT OR IGNORE INTO connections(id, kind, host, port, username, password_enc, created_at)
                VALUES(?, 'mysql', 'h', 3306, 'u', x'00', 0)
                """, id);
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
    void list_without_filter_returns_all() throws Exception {
        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-a\",\"title\":\"A\"}"))
            .andExpect(status().isOk());
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
    void delete_cascades_messages() throws Exception {
        String id = createSession("conn-cascade", "含消息");

        jdbc.update("""
            INSERT INTO messages(id, session_id, role, parts_json, created_at)
            VALUES(?, ?, ?, ?, ?)
            """, "msg-" + id, id, "USER", "[]", System.currentTimeMillis());

        Integer before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM messages WHERE session_id = ?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(before).isEqualTo(1);

        mvc.perform(delete("/api/sessions/" + id))
            .andExpect(status().isNoContent());

        Integer after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM messages WHERE session_id = ?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(after).isZero();
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
