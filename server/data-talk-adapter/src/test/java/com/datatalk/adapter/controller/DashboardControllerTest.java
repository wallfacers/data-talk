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
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DashboardControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String V3_DASHBOARD = """
        {
          "schemaVersion": 3,
          "id": "dash_placeholder",
          "title": "Test Dashboard",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 999
        }
        """;

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
        DashboardCompiler compiler = mock(DashboardCompiler.class);
        when(compiler.compile(any())).thenReturn(
            new DashboardCompiler.CompileResult("<html>mock</html>", List.of()));
        DashboardArtifactService service = new DashboardArtifactService(
            fileArtifactService, root, validator, compiler, mapper, clock);

        PatternCatalog catalog = mock(PatternCatalog.class);
        WidgetDataService widgetDataService = mock(WidgetDataService.class);
        var sessionDataContextService = mock(com.datatalk.application.session.SessionDataContextService.class);
        mvc = standaloneSetup(new DashboardController(
            service, compiler, catalog, widgetDataService, sessionDataContextService)).build();
    }

    @Test
    void promoteCreatesDashboardAndReturnsId() throws Exception {
        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_DASHBOARD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("dash_")))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchEndpointRemoved() throws Exception {
        mvc.perform(patch("/api/dashboards/dash_test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsLargePayload() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300_000; i++) sb.append("x");
        String largeDesc = sb.toString();

        mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 3,
                    "id": "dash_placeholder",
                    "title": "T",
                    "theme": "industry-ecommerce",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "description": "%s",
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free", "template": "grid-equal" },
                    "version": 999
                  }
                }
                """.formatted(largeDesc)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void getReturnsDashboard() throws Exception {
        String response = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_DASHBOARD.replace("Test Dashboard", "My Dashboard"))))
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(response).get("id").asText();

        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("My Dashboard"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateReturnsConflictOnVersionMismatch() throws Exception {
        String response = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_DASHBOARD)))
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(response).get("id").asText();

        mvc.perform(post("/api/dashboards/" + id + "/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s, \"baseVersion\": 999}".formatted(V3_DASHBOARD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("version_conflict"));
    }

    @Test
    void previewCompilesWithoutPersisting() throws Exception {
        mvc.perform(post("/api/dashboards/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_DASHBOARD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.html").value("<html>mock</html>"));
    }

    @Test
    void previewRejectsInvalidCompilation() throws Exception {
        DashboardCompiler failingCompiler = mock(DashboardCompiler.class);
        when(failingCompiler.compile(any())).thenReturn(
            new DashboardCompiler.CompileResult(null, List.of(
                new DashboardCompiler.CompileError("widget-compile", "/widgets/0", "bad widget"))));
        DashboardArtifactService svc = new DashboardArtifactService(
            mock(FileArtifactService.class),
            mock(SessionWorkdirRoot.class),
            mock(DashboardSchemaValidator.class),
            failingCompiler,
            mapper, Clock.systemUTC());
        MockMvc previewMvc = standaloneSetup(new DashboardController(
            svc, failingCompiler, mock(PatternCatalog.class),
            mock(WidgetDataService.class),
            mock(com.datatalk.application.session.SessionDataContextService.class))).build();

        previewMvc.perform(post("/api/dashboards/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s}".formatted(V3_DASHBOARD)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("compile_error"));
    }

    @Test
    void updateReturns404ForUnknownDashboard() throws Exception {
        mvc.perform(post("/api/dashboards/dash_nonexistent/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dashboard\": %s, \"baseVersion\": 1}".formatted(V3_DASHBOARD)))
            .andExpect(status().isNotFound());
    }
}
