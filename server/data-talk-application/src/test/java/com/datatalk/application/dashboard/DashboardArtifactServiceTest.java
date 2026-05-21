package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.FakeFileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DashboardArtifactServiceTest {

    @TempDir
    Path tempDir;

    private DashboardArtifactService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Clock.systemUTC().instant(), Clock.systemUTC().getZone());

    private static final String VALID_DASHBOARD = """
        {
          "schemaVersion": 3,
          "id": "dash_placeholder",
          "title": "Test Dashboard",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 30000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_sales01",
              "type": "chart",
              "slot": "hero",
              "title": "Chart A",
              "patternId": "generic.echarts-card",
              "options": {}
            }
          ],
          "layout": { "engine": "free", "template": "single-focus" },
          "version": 999
        }
        """;

    @BeforeEach
    void setUp() throws Exception {
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
            new DashboardCompiler.CompileResult("<html>mock</html>", java.util.List.of()));
        service = new DashboardArtifactService(fileArtifactService, root, validator, compiler, mapper, clock);
    }

    @Test
    void promoteCreatesDashboardWithVersionOneAndLoadRoundTrips() throws Exception {
        JsonNode payload = mapper.readTree(VALID_DASHBOARD);
        DashboardArtifactService.PromoteResult result = service.promote(payload);

        assertThat(result.id()).startsWith("dash_");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.html()).isNotNull();

        JsonNode loaded = service.load(result.id());
        assertThat(loaded.get("id").asText()).isEqualTo(result.id());
        assertThat(loaded.get("version").asInt()).isEqualTo(1);
        assertThat(loaded.get("title").asText()).isEqualTo("Test Dashboard");
    }

    @Test
    void loadNonexistentThrows() {
        assertThatThrownBy(() -> service.load("dash_nonexistent"))
            .isInstanceOf(DashboardArtifactService.DashboardNotFoundException.class);
    }

    @Test
    void promoteInvalidDashboardThrows() throws Exception {
        String invalid = """
        {
          "schemaVersion": 3,
          "id": "dash_test",
          "title": "",
          "theme": "industry-ecommerce",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 30000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "free", "template": "grid-equal" },
          "version": 1
        }
        """;
        JsonNode payload = mapper.readTree(invalid);

        assertThatThrownBy(() -> service.promote(payload))
            .isInstanceOf(DashboardArtifactService.ValidationException.class);
    }
}
