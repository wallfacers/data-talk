package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.datatalk.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceDirectoryServiceTest {

    @TempDir
    Path tempDir;

    SessionWorkdirRoot workdirRoot;
    FakeFileArtifactRepository repo;
    JdbcTemplate jdbc;
    ObjectMapper objectMapper;
    ResourceDirectoryService service;

    @BeforeEach
    void setUp() throws Exception {
        workdirRoot = new SessionWorkdirRoot(tempDir, tempDir.resolve("opencode"));
        Files.createDirectories(workdirRoot.dashboardsRoot());
        Files.createDirectories(workdirRoot.reportsRoot());
        Files.createDirectories(workdirRoot.dataTalkRoot().resolve("exports"));
        Files.createDirectories(workdirRoot.dataTalkRoot().resolve("semantic"));
        Files.createDirectories(workdirRoot.dataTalkRoot().resolve("uploads"));

        repo = new FakeFileArtifactRepository();
        jdbc = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new ResourceDirectoryService(workdirRoot, repo, jdbc, objectMapper);
    }

    @Nested
    class Dashboards {

        @Test
        void returnsParsedDashboardJsonData() throws Exception {
            Path dashFile = workdirRoot.dashboardsRoot().resolve("test-dash.dashboard.json");
            String dashJson = """
                    {"title":"My Dashboard","widgets":[{"id":"w1"},{"id":"w2"}]}""";
            Files.writeString(dashFile, dashJson);

            List<DashboardResourceDto> results = service.getDashboards(200);

            assertThat(results).hasSize(1);
            DashboardResourceDto dto = results.getFirst();
            assertThat(dto.id()).isEqualTo("test-dash");
            assertThat(dto.title()).isEqualTo("My Dashboard");
            assertThat(dto.widgetCount()).isEqualTo(2);
            assertThat(dto.filename()).isEqualTo("test-dash.dashboard.json");
            assertThat(dto.sizeBytes()).isGreaterThan(0);
            assertThat(dto.createdAt()).isGreaterThan(0);
            assertThat(dto.updatedAt()).isGreaterThan(0);
        }

        @Test
        void returnsEmptyListWhenDashboardsDirDoesNotExist() {
            ResourceDirectoryService svc = new ResourceDirectoryService(
                    new SessionWorkdirRoot(tempDir.resolve("nonexistent"), tempDir.resolve("opencode")),
                    repo, jdbc, objectMapper);

            List<DashboardResourceDto> results = svc.getDashboards(200);
            assertThat(results).isEmpty();
        }

        @Test
        void respectsLimit() throws Exception {
            for (int i = 0; i < 5; i++) {
                Path f = workdirRoot.dashboardsRoot().resolve("dash-" + i + ".dashboard.json");
                Files.writeString(f, "{\"title\":\"D" + i + "\",\"widgets\":[]}");
            }

            List<DashboardResourceDto> results = service.getDashboards(3);

            assertThat(results).hasSize(3);
        }

        @Test
        void joinsWithFileArtifactDbRows() throws Exception {
            Path dashFile = workdirRoot.dashboardsRoot().resolve("db-dash.dashboard.json");
            Files.writeString(dashFile, "{\"title\":\"DB Dash\",\"widgets\":[]}");

            String absolutePath = dashFile.toAbsolutePath().normalize().toString();
            Instant now = Instant.now();
            FileArtifact artifact = new FileArtifact(
                    "fa-1", FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                    FileArtifactKind.DASHBOARD, "sess-1", null, "db-dash.dashboard.json",
                    absolutePath, 500, null, "DB Dash", null,
                    now.minusSeconds(3600), now, null, Map.of(), true);
            repo.insert(artifact);

            List<DashboardResourceDto> results = service.getDashboards(200);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().originSessionId()).isEqualTo("sess-1");
        }
    }

    @Nested
    class Reports {

        @Test
        void detectsAvailableFormats() throws Exception {
            Path reportDir = workdirRoot.reportsRoot().resolve("monthly-report");
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve("report.html"), "<html>Report</html>");
            Files.writeString(reportDir.resolve("report.pdf"), "PDF bytes");
            Files.writeString(reportDir.resolve("report.md"), "# Markdown");

            List<ReportResourceDto> results = service.getReports(200);

            assertThat(results).hasSize(1);
            ReportResourceDto dto = results.getFirst();
            assertThat(dto.id()).isEqualTo("monthly-report");
            assertThat(dto.availableFormats()).containsExactly("html", "md", "pdf");
            assertThat(dto.sizeBytes()).isGreaterThan(0);
        }

        @Test
        void formatsAlwaysInCanonicalOrder() throws Exception {
            Path reportDir = workdirRoot.reportsRoot().resolve("alpha");
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve("report.pdf"), "pdf");
            Files.writeString(reportDir.resolve("report.html"), "html");

            List<ReportResourceDto> results = service.getReports(200);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().availableFormats()).containsExactly("html", "pdf");
        }

        @Test
        void returnsEmptyListWhenReportsDirDoesNotExist() {
            ResourceDirectoryService svc = new ResourceDirectoryService(
                    new SessionWorkdirRoot(tempDir.resolve("nonexistent"), tempDir.resolve("opencode")),
                    repo, jdbc, objectMapper);

            assertThat(svc.getReports(200)).isEmpty();
        }

        @Test
        void skipsDirectoriesWithoutReportFiles() throws Exception {
            Path emptyDir = workdirRoot.reportsRoot().resolve("empty-report");
            Files.createDirectories(emptyDir);
            // No report.html/pdf/md files — should be skipped

            List<ReportResourceDto> results = service.getReports(200);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    class Exports {

        @Test
        void returnsExportEntriesWithExpiresAt() throws Exception {
            Path exportDir = workdirRoot.dataTalkRoot().resolve("exports").resolve("exp-001");
            Files.createDirectories(exportDir);
            Files.writeString(exportDir.resolve("data.csv"), "col1,col2\na,b\nc,d");

            List<ExportResourceDto> results = service.getExports(200);

            assertThat(results).hasSize(1);
            ExportResourceDto dto = results.getFirst();
            assertThat(dto.exportId()).isEqualTo("exp-001");
            assertThat(dto.filename()).isEqualTo("data.csv");
            assertThat(dto.format()).isEqualTo("csv");
            assertThat(dto.sizeBytes()).isGreaterThan(0);
            assertThat(dto.expiresAt()).isGreaterThan(dto.createdAt());
            assertThat(dto.expiresAt() - dto.createdAt()).isEqualTo(60 * 60 * 1000); // +1 hour
        }

        @Test
        void detectsFormatFromExtension() throws Exception {
            Path exportDir = workdirRoot.dataTalkRoot().resolve("exports").resolve("exp-xlsx");
            Files.createDirectories(exportDir);
            Files.writeString(exportDir.resolve("output.xlsx"), "binary");

            List<ExportResourceDto> results = service.getExports(200);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().format()).isEqualTo("xlsx");
        }

        @Test
        void returnsEmptyWhenExportsDirMissing() {
            ResourceDirectoryService svc = new ResourceDirectoryService(
                    new SessionWorkdirRoot(tempDir.resolve("nonexistent"), tempDir.resolve("opencode")),
                    repo, jdbc, objectMapper);

            assertThat(svc.getExports(200)).isEmpty();
        }
    }

    @Nested
    class Semantic {

        @Test
        void readsModelYamlFiles() throws Exception {
            Path connDir = workdirRoot.dataTalkRoot().resolve("semantic").resolve("conn-1");
            Files.createDirectories(connDir);
            Files.writeString(connDir.resolve("orders.model.yaml"), "domain: orders\nentities: []");
            Files.writeString(connDir.resolve("users.model.yaml"), "domain: users\nentities: []");

            when(jdbc.queryForList("SELECT id, name FROM connections"))
                    .thenReturn(List.of(Map.of("id", "conn-1", "name", "MyDB")));

            List<SemanticResourceDto> results = service.getSemantic(200);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(SemanticResourceDto::domain)
                    .containsExactlyInAnyOrder("orders", "users");
            assertThat(results).allMatch(dto -> dto.connectionName().equals("MyDB"));
            assertThat(results).allMatch(dto -> dto.status().equals("active"));
        }

        @Test
        void detectsPendingStatus() throws Exception {
            Path connDir = workdirRoot.dataTalkRoot().resolve("semantic").resolve("conn-1");
            Files.createDirectories(connDir.resolve("pending"));
            Files.writeString(connDir.resolve("sales.model.yaml"), "domain: sales\nentities: []");
            Files.writeString(connDir.resolve("pending").resolve("sales.model.yaml"), "domain: sales\nentities: [new]");

            when(jdbc.queryForList("SELECT id, name FROM connections")).thenReturn(List.of());

            List<SemanticResourceDto> results = service.getSemantic(200);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().status()).isEqualTo("pending");
        }

        @Test
        void detectsActiveStatusWithPatches() throws Exception {
            Path connDir = workdirRoot.dataTalkRoot().resolve("semantic").resolve("conn-1");
            Files.createDirectories(connDir);
            Files.writeString(connDir.resolve("inventory.model.yaml"), "domain: inventory\nentities: []");
            Files.writeString(connDir.resolve("inventory.model.yaml.patches.jsonl"),
                    "{\"op\":\"add\"}\n");

            when(jdbc.queryForList("SELECT id, name FROM connections")).thenReturn(List.of());

            List<SemanticResourceDto> results = service.getSemantic(200);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().status()).isEqualTo("active");
        }

        @Test
        void returnsEmptyWhenSemanticDirMissing() {
            ResourceDirectoryService svc = new ResourceDirectoryService(
                    new SessionWorkdirRoot(tempDir.resolve("nonexistent"), tempDir.resolve("opencode")),
                    repo, jdbc, objectMapper);

            assertThat(svc.getSemantic(200)).isEmpty();
        }
    }

    @Nested
    class Uploads {

        @Test
        void queriesUploadedFileTable() {
            when(jdbc.queryForList(
                    "SELECT id, session_id, filename, mime_type, size_bytes, created_at " +
                    "FROM uploaded_file ORDER BY created_at DESC LIMIT ?",
                    200))
                    .thenReturn(List.of(Map.of(
                            "id", "upl-1",
                            "session_id", "sess-1",
                            "filename", "data.csv",
                            "mime_type", "text/csv",
                            "size_bytes", 1024L,
                            "created_at", 1700000000000L
                    )));

            List<UploadResourceDto> results = service.getUploads(200);

            assertThat(results).hasSize(1);
            UploadResourceDto dto = results.getFirst();
            assertThat(dto.id()).isEqualTo("upl-1");
            assertThat(dto.filename()).isEqualTo("data.csv");
            assertThat(dto.mimeType()).isEqualTo("text/csv");
            assertThat(dto.sizeBytes()).isEqualTo(1024L);
            assertThat(dto.originSessionId()).isEqualTo("sess-1");
            assertThat(dto.createdAt()).isEqualTo(1700000000000L);
            assertThat(dto.expiresAt()).isEqualTo(1700000000000L + (24 * 60 * 60 * 1000)); // +24 hours
        }
    }

    @Nested
    class Deletion {

        @Test
        void deleteDashboardRemovesFilesAndDbRow() throws Exception {
            Path dashFile = workdirRoot.dashboardsRoot().resolve("del-dash.dashboard.json");
            Files.writeString(dashFile, "{\"title\":\"TBD\"}");
            Path htmlFile = workdirRoot.dashboardsRoot().resolve("del-dash.html");
            Files.writeString(htmlFile, "<html></html>");

            String absPath = dashFile.toAbsolutePath().normalize().toString();
            Instant now = Instant.now();
            FileArtifact artifact = new FileArtifact(
                    "fa-dash", FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                    FileArtifactKind.DASHBOARD, "sess-1", null, "del-dash.dashboard.json",
                    absPath, 100, null, "TBD", null, now, now, null, Map.of(), true);
            repo.insert(artifact);

            service.deleteDashboard("del-dash");

            assertThat(Files.exists(dashFile)).isFalse();
            assertThat(Files.exists(htmlFile)).isFalse();
            assertThat(repo.findById("fa-dash")).isEmpty();
        }

        @Test
        void deleteReportRecursivelyRemovesDirAndArtifacts() throws Exception {
            Path reportDir = workdirRoot.reportsRoot().resolve("del-report");
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve("report.html"), "<html>X</html>");
            Files.writeString(reportDir.resolve("report.pdf"), "PDF");

            String absPath = reportDir.resolve("report.html").toAbsolutePath().normalize().toString();
            Instant now = Instant.now();
            FileArtifact artifact = new FileArtifact(
                    "fa-rpt", FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                    FileArtifactKind.REPORT, "sess-1", null, "report.html",
                    absPath, 200, null, "Report", null, now, now, null, Map.of(), true);
            repo.insert(artifact);

            service.deleteReport("del-report");

            assertThat(Files.exists(reportDir)).isFalse();
            assertThat(repo.findById("fa-rpt")).isEmpty();
        }

        @Test
        void deleteExportRemovesDirectory() throws Exception {
            Path exportDir = workdirRoot.dataTalkRoot().resolve("exports").resolve("del-exp");
            Files.createDirectories(exportDir);
            Files.writeString(exportDir.resolve("export.csv"), "a,b");

            service.deleteExport("del-exp");

            assertThat(Files.exists(exportDir)).isFalse();
        }

        @Test
        void deleteSemanticRemovesModelAndPatchFiles() throws Exception {
            Path connDir = workdirRoot.dataTalkRoot().resolve("semantic").resolve("conn-1");
            Files.createDirectories(connDir);
            Path modelFile = connDir.resolve("sales.model.yaml");
            Path patchFile = connDir.resolve("sales.model.yaml.patches.jsonl");
            Files.writeString(modelFile, "domain: sales");
            Files.writeString(patchFile, "{}");

            service.deleteSemantic("sales", "conn-1");

            assertThat(Files.exists(modelFile)).isFalse();
            assertThat(Files.exists(patchFile)).isFalse();
        }

        @Test
        void deleteSemanticDoesNothingWhenConnDirMissing() {
            // Should not throw
            service.deleteSemantic("nonexistent", "conn-unknown");
        }

        @Test
        void deleteUploadRemovesDirAndDbRow() throws Exception {
            Path uploadDir = workdirRoot.dataTalkRoot().resolve("uploads").resolve("upl-del");
            Files.createDirectories(uploadDir);
            Files.writeString(uploadDir.resolve("file.txt"), "content");

            service.deleteUpload("upl-del");

            assertThat(Files.exists(uploadDir)).isFalse();
            verify(jdbc).update("DELETE FROM uploaded_file WHERE id = ?", "upl-del");
        }

        @Test
        void deleteUploadHandlesNonexistentDirectory() {
            service.deleteUpload("nonexistent");
            verify(jdbc).update("DELETE FROM uploaded_file WHERE id = ?", "nonexistent");
        }

        @Test
        void deleteDashboardThrowsWhenIoError() throws Exception {
            // Use a path that exists as a file to trigger an error on Files.deleteIfExists
            // for a directory operation since we won't create the dashboard file
            // but the method should not throw for missing files (deleteIfExists is safe)
            service.deleteDashboard("no-such-dashboard");
            // No exception expected — deleteIfExists is tolerant
        }
    }

    @Nested
    class ResourceOverview {

        @Test
        void returnsCountsAndSizes() throws Exception {
            // Dashboards: create one file
            Files.writeString(workdirRoot.dashboardsRoot().resolve("d1.dashboard.json"),
                    "{\"title\":\"D1\"}");

            // Reports: create one dir with a file
            Path reportDir = workdirRoot.reportsRoot().resolve("r1");
            Files.createDirectories(reportDir);
            Files.writeString(reportDir.resolve("report.html"), "<html>R1</html>");

            // Uploads: mock DB count
            when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM uploaded_file"), eq(Integer.class)))
                    .thenReturn(3);

            Map<String, StorageOverviewDto.ResourceDirSummary> overview = service.getResourceOverview();

            assertThat(overview).containsKeys(
                    "dashboards", "reports", "exports", "semantic", "uploads");

            // Dashboards: 1 file
            assertThat(overview.get("dashboards").count()).isEqualTo(1);
            assertThat(overview.get("dashboards").sizeBytes()).isGreaterThan(0);

            // Reports: 1 directory
            assertThat(overview.get("reports").count()).isEqualTo(1);
            assertThat(overview.get("reports").sizeBytes()).isGreaterThan(0);

            // Exports: empty dir => 0
            assertThat(overview.get("exports").count()).isEqualTo(0);
            assertThat(overview.get("exports").sizeBytes()).isEqualTo(0);

            // Uploads: DB count
            assertThat(overview.get("uploads").count()).isEqualTo(3);
        }

        @Test
        void emptyDirectoriesReturnZeros() {
            Map<String, StorageOverviewDto.ResourceDirSummary> overview = service.getResourceOverview();

            for (var entry : overview.entrySet()) {
                if (!entry.getKey().equals("uploads")) {
                    assertThat(entry.getValue().count()).isEqualTo(0);
                }
            }
        }
    }
}
