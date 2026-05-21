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
class StageFindControllerIT {

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void resetTables() {
        jdbc.update("DELETE FROM stage_tab_payload");
        jdbc.update("DELETE FROM stage_tabs");
        jdbc.update("DELETE FROM sessions");
    }

    @Test
    void findReturnsMetadataItems() throws Exception {
        long now = System.currentTimeMillis();
        insertStageTab("find-1", "query_editor", "My Query", now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('find-1', '{"sql":"SELECT 1"}', 'SELECT 1 FROM users WHERE email = test@example.com', 1, ?)
            """, now);
        insertStageTab("find-2", "chart", "My Chart", now);

        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"metadata"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].id", isOneOf("find-1", "find-2")));
    }

    @Test
    void findWithContentQueryReturnsMatchingTabs() throws Exception {
        long now = System.currentTimeMillis();
        insertStageTab("content-1", "query_editor", "Email Query", now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('content-1', '{"sql":"SELECT * FROM users"}', 'find all users with email test@example.com', 1, ?)
            """, now);

        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"tabs_only"},"query":{"pattern":"email","mode":"fts"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tabIds", hasItem("content-1")));
    }

    @Test
    void findCountModeReturnsTotal() throws Exception {
        long now = System.currentTimeMillis();
        insertStageTab("count-1", "query_editor", "Q1", now);
        insertStageTab("count-2", "query_editor", "Q2", now);

        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"count"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalMatched", is(2)));
    }

    private void insertStageTab(String id, String type, String title, long now) {
        if (hasScopeColumn()) {
            jdbc.update("""
                INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
                VALUES(?, ?, 'workspace', ?, ?, ?)
                """, id, type, title, now, now);
            return;
        }

        jdbc.update("""
            INSERT INTO stage_tabs(id, type, title, created_at, last_touched_at)
            VALUES(?, ?, ?, ?, ?)
            """, id, type, title, now, now);
    }

    private boolean hasScopeColumn() {
        Integer matches = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pragma_table_info('stage_tabs') WHERE name = 'scope'",
            Integer.class);
        return matches != null && matches > 0;
    }
}
