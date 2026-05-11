package com.datatalk.adapter.controller;

import com.datatalk.application.dashboard.*;
import com.datatalk.adapter.controller.doubles.FakeFileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.mockito.Mockito.*;
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
        SessionWorkdirRoot root = new SessionWorkdirRoot(tempDir, tempDir.resolve("opencode"));
        Files.createDirectories(root.dashboardsRoot());

        FakeFileArtifactRepository repo = new FakeFileArtifactRepository();
        FileArtifactService fileArtifactService = new FileArtifactService(
                repo,
                mock(com.datatalk.application.fileartifact.SessionWorkdirService.class),
                mock(com.datatalk.application.session.SessionBusRegistry.class),
                mapper,
                mock(com.datatalk.application.fileartifact.FileArtifactPhysicalMover.class),
                mock(com.datatalk.application.persistence.SessionRepository.class),
                mock(com.datatalk.application.persistence.ConnectionRepository.class));

        DashboardSchemaValidator validator = new DashboardSchemaValidator(mapper);
        JsonPatchApplier patchApplier = new JsonPatchApplier(mapper);
        DashboardArtifactService service = new DashboardArtifactService(fileArtifactService, root, validator, patchApplier, mapper, clock);
        mvc = standaloneSetup(new DashboardController(service)).build();
    }

    @Test
    void promoteCreatesDashboardAndReturnsId() throws Exception {
        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "Test Dashboard",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
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
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "Test Dashboard",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
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
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "T",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "description": "%s",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
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
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "My Dashboard",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
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
