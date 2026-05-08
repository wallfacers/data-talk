package com.datatalk.adapter.controller;

import com.datatalk.application.dashboard.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DashboardControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(Clock.systemUTC().instant(), Clock.systemUTC().getZone());
        DashboardStore store = new DashboardStore(tempDir, mapper);
        store.init();
        DashboardSchemaValidator validator = new DashboardSchemaValidator(mapper);
        JsonPatchApplier patchApplier = new JsonPatchApplier(mapper);
        DashboardArtifactService service = new DashboardArtifactService(store, validator, patchApplier, mapper, clock);
        mvc = standaloneSetup(new DashboardController(service)).build();
    }

    @Test
    void promoteCreatesDashboardAndReturnsId() throws Exception {
        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "Test Dashboard",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
                    "version": 999
                  }
                }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("dash_")))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchWith409OnStaleVersion() throws Exception {
        // First promote
        String response = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "Test Dashboard",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
                    "version": 999
                  }
                }
                """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(response).get("id").asText();

        // Patch with stale version
        mvc.perform(patch("/api/dashboards/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "baseVersion": 0,
                  "ops": [{ "op": "replace", "path": "/title", "value": "X" }]
                }
                """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("version_conflict"));
    }

    @Test
    void rejectsLargePayload() throws Exception {
        // Create a dashboard with a very large description
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300_000; i++) sb.append("x");
        String largeDesc = sb.toString();

        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "T",
                    "description": "%s",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12 },
                    "version": 999
                  }
                }
                """.formatted(largeDesc)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void getReturnsDashboard() throws Exception {
        // Promote first
        String response = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 1,
                    "id": "dash_placeholder",
                    "title": "My Dashboard",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
                    "version": 999
                  }
                }
                """))
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(response).get("id").asText();

        // GET
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("My Dashboard"))
            .andExpect(jsonPath("$.version").value(1));
    }
}
