package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * I1: Backend E2E integration test for Dashboard.
 * Verifies Spring bean wiring, REST endpoints, and full promote→load→patch lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void fullLifecycle_promoteLoadPatch() throws Exception {
        // Promote
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "E2E Dashboard",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
                    "version": 999
                  }
                }
                """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        JsonNode promoteJson = mapper.readTree(promoteResponse);
        String id = promoteJson.get("id").asText();
        assertThat(id).startsWith("dash_");
        assertThat(promoteJson.get("version").asInt()).isEqualTo(1);

        // Load
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("E2E Dashboard"))
            .andExpect(jsonPath("$.version").value(1));

        // Patch
        mvc.perform(patch("/api/dashboards/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "baseVersion": 1,
                  "ops": [{ "op": "replace", "path": "/title", "value": "Updated Dashboard" }]
                }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2));

        // Verify patch applied
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Dashboard"))
            .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void patchRejectsStaleVersion() throws Exception {
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "Version Test",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
                    "version": 999
                  }
                }
                """))
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(promoteResponse).get("id").asText();

        mvc.perform(patch("/api/dashboards/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "baseVersion": 0,
                  "ops": [{ "op": "replace", "path": "/title", "value": "X" }]
                }
                """))
            .andExpect(status().isConflict());
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mvc.perform(get("/api/dashboards/dash_nonexistent"))
            .andExpect(status().isNotFound());
    }
}
