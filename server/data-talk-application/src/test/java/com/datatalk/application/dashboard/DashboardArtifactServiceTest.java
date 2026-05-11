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
import java.util.List;

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
          "schemaVersion": 2,
          "id": "dash_placeholder",
          "title": "Test Dashboard",
          "theme": "industry-neutral",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 30000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [
            {
              "id": "chart_w_abc12345",
              "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 8 },
              "patternId": "generic.echarts-card",
              "options": { "title": "Chart A" }
            }
          ],
          "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
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
        JsonPatchApplier patchApplier = new JsonPatchApplier(mapper);
        service = new DashboardArtifactService(fileArtifactService, root, validator, patchApplier, mapper, clock);
    }

    @Test
    void promoteCreatesDashboardWithVersionOneAndLoadRoundTrips() throws Exception {
        JsonNode payload = mapper.readTree(VALID_DASHBOARD);
        DashboardArtifactService.PromoteResult result = service.promote(payload);

        assertThat(result.id()).startsWith("dash_");
        assertThat(result.version()).isEqualTo(1);

        // Load round-trip
        JsonNode loaded = service.load(result.id());
        assertThat(loaded.get("id").asText()).isEqualTo(result.id());
        assertThat(loaded.get("version").asInt()).isEqualTo(1);
        assertThat(loaded.get("title").asText()).isEqualTo("Test Dashboard");
    }

    @Test
    void patchAppliesWithBaseVersionAndBumpsVersion() throws Exception {
        JsonNode payload = mapper.readTree(VALID_DASHBOARD);
        DashboardArtifactService.PromoteResult promoted = service.promote(payload);

        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("replace", "/title", mapper.readValue("\"Updated Title\"", JsonNode.class))
        );

        DashboardArtifactService.PatchResult patchResult = service.patch(promoted.id(), 1, ops);
        assertThat(patchResult.version()).isEqualTo(2);

        // Verify persisted
        JsonNode loaded = service.load(promoted.id());
        assertThat(loaded.get("title").asText()).isEqualTo("Updated Title");
        assertThat(loaded.get("version").asInt()).isEqualTo(2);
    }

    @Test
    void patchWithStaleBaseVersionThrows() throws Exception {
        JsonNode payload = mapper.readTree(VALID_DASHBOARD);
        DashboardArtifactService.PromoteResult promoted = service.promote(payload);

        // Patch once to bump version to 2
        List<JsonPatchApplier.PatchOp> ops = List.of(
            new JsonPatchApplier.PatchOp("replace", "/title", mapper.readValue("\"X\"", JsonNode.class))
        );
        service.patch(promoted.id(), 1, ops);

        // Try to patch with stale baseVersion=1
        assertThatThrownBy(() -> service.patch(promoted.id(), 1, ops))
            .isInstanceOf(JsonPatchApplier.VersionConflictException.class);
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
          "schemaVersion": 2,
          "id": "dash_test",
          "title": "",
          "theme": "industry-neutral",
          "renderer": "bezel",
          "refresh": { "defaultIntervalMs": 30000, "pauseOnHidden": true },
          "parameters": [],
          "widgets": [],
          "layout": { "engine": "grid", "cols": 12 },
          "version": 1
        }
        """;
        JsonNode payload = mapper.readTree(invalid);

        assertThatThrownBy(() -> service.promote(payload))
            .isInstanceOf(DashboardArtifactService.ValidationException.class);
    }
}
