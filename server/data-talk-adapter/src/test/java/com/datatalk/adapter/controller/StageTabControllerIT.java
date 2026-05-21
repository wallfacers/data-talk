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
        jdbc.update("DELETE FROM sessions");
    }

    @Test
    void putCreatesAndReturnsPayloadVersion() throws Exception {
        mvc.perform(put("/api/stage/tabs/tab-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","title":"My Tab"}
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
                    {"type":"query_editor","title":"V1"}
                    """))
            .andExpect(status().isOk());

        // Try update with stale If-Match
        mvc.perform(put("/api/stage/tabs/tab-2")
                .header("If-Match", "99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","title":"V2"}
                    """))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.error", is("CONCURRENCY_CONFLICT")));
    }

    @Test
    void getListsByOriginSessionIdAndArchived() throws Exception {
        long now = System.currentTimeMillis();
        insertSession("sess-1", "April Weekly", now);
        insertSession("sess-2", "May Weekly", now);
        insertStageTab("ws-1", "query_editor", "Active S1", "sess-1", false, null, now);
        insertStageTab("ws-2", "chart", "Archived S1", "sess-1", true, now, now);
        insertStageTab("ws-3", "query_editor", "Active S2", "sess-2", false, null, now);

        mvc.perform(get("/api/stage/tabs?originSessionId=sess-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id", is("ws-1")))
            .andExpect(jsonPath("$.items[0].originSessionId", is("sess-1")))
            .andExpect(jsonPath("$.items[0].scope").doesNotExist());

        mvc.perform(get("/api/stage/tabs?originSessionId=sess-1&archived=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void getPayloadReturnsContentJson() throws Exception {
        long now = System.currentTimeMillis();
        insertStageTab("payload-1", "query_editor", "Payload Test", null, false, null, now);
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
        insertStageTab("del-1", "query_editor", "Delete Me", null, false, null, now);

        mvc.perform(delete("/api/stage/tabs/del-1"))
            .andExpect(status().isNoContent());

        // DELETE is idempotent — second call still returns 204 even if the row is already gone
        mvc.perform(delete("/api/stage/tabs/del-1"))
            .andExpect(status().isNoContent());
    }

    private void insertSession(String id, String title, long now) {
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, NULL, ?, 0, NULL, ?, ?, 0)
            """, id, title, now, now);
    }

    private void insertStageTab(
        String id,
        String type,
        String title,
        String originSessionId,
        boolean archived,
        Long archivedAt,
        long now
    ) {
        if (hasScopeColumn()) {
            jdbc.update("""
                INSERT INTO stage_tabs(
                    id, type, scope, title, origin_session_id, archived, archived_at, created_at, last_touched_at
                )
                VALUES(?, ?, 'workspace', ?, ?, ?, ?, ?, ?)
                """, id, type, title, originSessionId, archived ? 1 : 0, archivedAt, now, now);
            return;
        }

        jdbc.update("""
            INSERT INTO stage_tabs(
                id, type, title, origin_session_id, archived, archived_at, created_at, last_touched_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """, id, type, title, originSessionId, archived ? 1 : 0, archivedAt, now, now);
    }

    private boolean hasScopeColumn() {
        Integer matches = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pragma_table_info('stage_tabs') WHERE name = 'scope'",
            Integer.class);
        return matches != null && matches > 0;
    }
}
