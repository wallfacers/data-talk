package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.ResourceDirectoryService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.housekeeping.HousekeepingScheduler;
import com.datatalk.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MaintenanceControllerResourceTest {

    @TempDir
    Path tempDir;

    private ResourceDirectoryService resourceDirectoryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        resourceDirectoryService = mock(ResourceDirectoryService.class);

        // Use a real SessionWorkdirRoot backed by @TempDir so storageOverview()
        // can resolve its dataTalkRoot() without NPE
        SessionWorkdirRoot workdirRoot = new SessionWorkdirRoot(tempDir, tempDir.resolve("opencode"));

        MaintenanceController controller = new MaintenanceController(
                workdirRoot,
                mock(FileArtifactRepository.class),
                mock(HousekeepingScheduler.class),
                mock(FileArtifactService.class),
                resourceDirectoryService);

        mvc = standaloneSetup(controller).build();
    }

    @Nested
    class ListEndpoints {

        @Test
        void getDashboardsReturns200WithDtoList() throws Exception {
            when(resourceDirectoryService.getDashboards(200)).thenReturn(List.of(
                    new DashboardResourceDto("d1", "Dash One", "d1.dashboard.json",
                            2048L, 3, "sess-1", 1000L, 2000L)
            ));

            mvc.perform(get("/api/maintenance/dashboards"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("d1"))
                    .andExpect(jsonPath("$[0].title").value("Dash One"))
                    .andExpect(jsonPath("$[0].widgetCount").value(3));
        }

        @Test
        void getReportsReturns200WithDtoList() throws Exception {
            when(resourceDirectoryService.getReports(200)).thenReturn(List.of(
                    new ReportResourceDto("r1", "Monthly Report", List.of("html", "pdf"),
                            4096L, "sess-2", 1000L, 2000L)
            ));

            mvc.perform(get("/api/maintenance/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("r1"))
                    .andExpect(jsonPath("$[0].availableFormats[0]").value("html"))
                    .andExpect(jsonPath("$[0].availableFormats[1]").value("pdf"));
        }

        @Test
        void getExportsReturns200WithDtoList() throws Exception {
            when(resourceDirectoryService.getExports(200)).thenReturn(List.of(
                    new ExportResourceDto("e1", "data.csv", "csv", 1024L, 100L,
                            null, 1000L, 4600000L)
            ));

            mvc.perform(get("/api/maintenance/exports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].exportId").value("e1"))
                    .andExpect(jsonPath("$[0].format").value("csv"));
        }

        @Test
        void getSemanticReturns200WithDtoList() throws Exception {
            when(resourceDirectoryService.getSemantic(200)).thenReturn(List.of(
                    new SemanticResourceDto("orders", "conn-1", "MyDB", "active", 512L, 2000L)
            ));

            mvc.perform(get("/api/maintenance/semantic"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].domain").value("orders"))
                    .andExpect(jsonPath("$[0].connectionName").value("MyDB"));
        }

        @Test
        void getUploadsReturns200WithDtoList() throws Exception {
            when(resourceDirectoryService.getUploads(200)).thenReturn(List.of(
                    new UploadResourceDto("u1", "report.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            20480L, "sess-3", 1000L, 87400000L)
            ));

            mvc.perform(get("/api/maintenance/uploads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("u1"))
                    .andExpect(jsonPath("$[0].filename").value("report.xlsx"));
        }

        @Test
        void dashboardsReturnsEmptyList() throws Exception {
            when(resourceDirectoryService.getDashboards(200)).thenReturn(List.of());

            mvc.perform(get("/api/maintenance/dashboards"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class DeleteEndpoints {

        @Test
        void deleteDashboardReturns204() throws Exception {
            mvc.perform(delete("/api/maintenance/dashboards/d1"))
                    .andExpect(status().isNoContent());

            verify(resourceDirectoryService).deleteDashboard("d1");
        }

        @Test
        void deleteReportReturns204() throws Exception {
            mvc.perform(delete("/api/maintenance/reports/r1"))
                    .andExpect(status().isNoContent());

            verify(resourceDirectoryService).deleteReport("r1");
        }

        @Test
        void deleteExportReturns204() throws Exception {
            mvc.perform(delete("/api/maintenance/exports/e1"))
                    .andExpect(status().isNoContent());

            verify(resourceDirectoryService).deleteExport("e1");
        }

        @Test
        void deleteSemanticReturns204() throws Exception {
            mvc.perform(delete("/api/maintenance/semantic/orders")
                    .param("connectionId", "conn-1"))
                    .andExpect(status().isNoContent());

            verify(resourceDirectoryService).deleteSemantic("orders", "conn-1");
        }

        @Test
        void deleteUploadReturns204() throws Exception {
            mvc.perform(delete("/api/maintenance/uploads/u1"))
                    .andExpect(status().isNoContent());

            verify(resourceDirectoryService).deleteUpload("u1");
        }
    }

    @Nested
    class StorageOverview {

        @Test
        void includesResourceDirectoriesInResponse() throws Exception {
            when(resourceDirectoryService.getResourceOverview()).thenReturn(Map.of(
                    "dashboards", new StorageOverviewDto.ResourceDirSummary(3, 4096),
                    "uploads", new StorageOverviewDto.ResourceDirSummary(5, 10240)
            ));

            String responseBody = mvc.perform(get("/api/maintenance/storage-overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceDirectories.dashboards.count").value(3))
                    .andExpect(jsonPath("$.resourceDirectories.dashboards.sizeBytes").value(4096))
                    .andExpect(jsonPath("$.resourceDirectories.uploads.count").value(5))
                    .andExpect(jsonPath("$.resourceDirectories.uploads.sizeBytes").value(10240))
                    .andReturn().getResponse().getContentAsString();

            // Verify workdir is not null
            assertThat(responseBody).contains("workdir");
        }
    }
}
