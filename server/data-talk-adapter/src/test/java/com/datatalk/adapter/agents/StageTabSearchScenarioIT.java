package com.datatalk.adapter.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI behavior regression test: verifies the find->patch->find pipeline
 * observes its own writes via force-flush of the FTS index.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StageTabSearchScenarioIT {

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void resetTables() {
        jdbc.update("DELETE FROM stage_tab_payload");
        jdbc.update("DELETE FROM stage_tabs");
    }

    @Test
    void aiPipelineFindThenReadThenPatchObservesItsOwnWrite() throws Exception {
        long now = System.currentTimeMillis();

        // Step 1: PUT tab with initial content
        mvc.perform(put("/api/stage/tabs/scenario-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"Scenario Tab",
                     "payloadJson":"{\\"sql\\":\\"SELECT * FROM users\\"}",
                     "contentText":"SELECT * FROM users WHERE name = Alice"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payloadVersion", is(1)));

        // Step 2: ui_find(email) — should NOT find "email" initially
        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"tabs_only"},"query":{"pattern":"email","mode":"fts"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tabIds", hasSize(0)));

        // Step 3: Patch content to add email
        // Re-PUT with updated content (using If-Match for concurrency)
        mvc.perform(put("/api/stage/tabs/scenario-1")
                .header("If-Match", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"Scenario Tab",
                     "payloadJson":"{\\"sql\\":\\"SELECT * FROM users WHERE email IS NOT NULL\\"}",
                     "contentText":"SELECT * FROM users WHERE email IS NOT NULL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payloadVersion", is(2)));

        // Step 4: ui_find(email) again — should NOW find the tab
        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"tabs_only"},"query":{"pattern":"email","mode":"fts"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tabIds", hasItem("scenario-1")));

        // Step 5: ui_find with count mode to verify
        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"count"},"query":{"pattern":"email","mode":"fts"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalMatched", greaterThanOrEqualTo(1)));

        // Step 6: Read back the tab content
        mvc.perform(get("/api/stage/tabs/scenario-1/payload"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentText", is("SELECT * FROM users WHERE email IS NOT NULL")));
    }

    @Test
    void metadataFindReturnsCorrectTabInfo() throws Exception {
        long now = System.currentTimeMillis();

        // Create two tabs
        mvc.perform(put("/api/stage/tabs/scenario-meta-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"query_editor","scope":"workspace","title":"First Query"}
                    """))
            .andExpect(status().isOk());

        mvc.perform(put("/api/stage/tabs/scenario-meta-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"chart","scope":"workspace","title":"Sales Chart"}
                    """))
            .andExpect(status().isOk());

        // Find by type filter
        mvc.perform(post("/api/stage/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"output":{"mode":"metadata"},"filter":{"scope":"workspace","type":"query_editor"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id", is("scenario-meta-1")))
            .andExpect(jsonPath("$.items[0].type", is("query_editor")));
    }
}
