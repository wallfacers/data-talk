package com.datatalk.adapter.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StageTabControllerIT {

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void resetTables() {
        jdbc.update("DELETE FROM stage_tab_payload");
        jdbc.update("DELETE FROM stage_tabs");
    }

    @Test
    void putCreatesAndReturnsPayloadVersion() throws Exception {
        mvc.perform(put("/api/stage/tabs/tab-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"My Tab"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is("tab-1")))
            .andExpect(jsonPath("$.payloadVersion", is(1)));
    }

    @Test
    void putReturns412WhenIfMatchVersionStale() throws Exception {
        // Create tab first
        mvc.perform(put("/api/stage/tabs/tab-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"V1"}
                    """))
            .andExpect(status().isOk());

        // Try update with stale If-Match
        mvc.perform(put("/api/stage/tabs/tab-2")
                .header("If-Match", "99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"V2"}
                    """))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.error", is("CONCURRENCY_CONFLICT")));
    }

    @Test
    void getListsByScopeAndArchived() throws Exception {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('ws-1', 'query_editor', 'workspace', 'Active WS', ?, ?)
            """, now, now);
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, archived, created_at, last_touched_at)
            VALUES('ws-2', 'chart', 'workspace', 'Archived WS', 1, ?, ?)
            """, now, now);

        // Default: exclude archived
        mvc.perform(get("/api/stage/tabs?scope=workspace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id", is("ws-1")));

        // Include archived
        mvc.perform(get("/api/stage/tabs?scope=workspace&archived=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void getPayloadReturnsContentJson() throws Exception {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('payload-1', 'query_editor', 'workspace', 'Payload Test', ?, ?)
            """, now, now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('payload-1', '{"sql":"SELECT 1"}', 'SELECT 1', 1, ?)
            """, now);

        mvc.perform(get("/api/stage/tabs/payload-1/payload"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tabId", is("payload-1")))
            .andExpect(jsonPath("$.payload.sql", is("SELECT 1")))
            .andExpect(jsonPath("$.payloadVersion", is(1)))
            .andExpect(jsonPath("$.payloadJson", is("{\"sql\":\"SELECT 1\"}")))
            .andExpect(jsonPath("$.contentText", is("SELECT 1")));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('del-1', 'query_editor', 'workspace', 'Delete Me', ?, ?)
            """, now, now);

        mvc.perform(delete("/api/stage/tabs/del-1"))
            .andExpect(status().isNoContent());

        // Second delete returns 404
        mvc.perform(delete("/api/stage/tabs/del-1"))
            .andExpect(status().isNotFound());
    }
}
