package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Lists, summarizes, and deletes resources across 5 resource directories.
 *
 * <p>This service sits in the application layer — it depends on domain types
 * and repository interfaces, not on infrastructure implementations directly.
 * File-system scanning uses {@link java.nio.file.Files} APIs; database lookups
 * use {@link FileArtifactRepository} and {@link JdbcTemplate} (for tables
 * without dedicated repository methods yet).
 *
 * <h3>Resource directories</h3>
 * <ol>
 *   <li><b>Dashboards</b> — {@code *.dashboard.json} files under {@code dashboards/}</li>
 *   <li><b>Reports</b> — subdirectories under {@code reports/}, each containing
 *       {@code report.html}, {@code report.pdf}, and/or {@code report.md}</li>
 *   <li><b>Exports</b> — subdirectories under {@code exports/}, data export files</li>
 *   <li><b>Semantic</b> — subdirectories under {@code semantic/}, YAML model files</li>
 *   <li><b>Uploads</b> — rows in {@code uploaded_file} table + files under {@code uploads/}</li>
 * </ol>
 */
@Service
public class ResourceDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(ResourceDirectoryService.class);
    private static final int MAX_LIMIT = 200;

    private final SessionWorkdirRoot workdirRoot;
    private final FileArtifactRepository fileArtifactRepo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ResourceDirectoryService(
            SessionWorkdirRoot workdirRoot,
            FileArtifactRepository fileArtifactRepo,
            @Qualifier("datatalkJdbc") JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.workdirRoot = workdirRoot;
        this.fileArtifactRepo = fileArtifactRepo;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────────
    //  Dashboards
    // ──────────────────────────────────────────────

    /**
     * List dashboards by scanning {@code dashboardsRoot()} for
     * {@code *.dashboard.json} files and joining with the
     * {@code file_artifact} table (kind=DASHBOARD, external=true).
     */
    public List<DashboardResourceDto> getDashboards(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        Path dashboardsDir = workdirRoot.dashboardsRoot();
        if (!Files.isDirectory(dashboardsDir)) {
            return List.of();
        }

        // Load DB rows for the dashboards directory
        Map<String, FileArtifact> dbByPhysicalPath = loadDashboardArtifacts(dashboardsDir);

        List<DashboardResourceDto> results = new ArrayList<>();
        try (Stream<Path> files = Files.list(dashboardsDir)) {
            List<Path> jsonFiles = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".dashboard.json"))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            for (Path jsonFile : jsonFiles) {
                if (results.size() >= effectiveLimit) break;
                try {
                    DashboardResourceDto dto = buildDashboardDto(jsonFile, dbByPhysicalPath);
                    if (dto != null) {
                        results.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Skipping dashboard file {}: {}", jsonFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list dashboards: {}", e.getMessage());
        }
        return results;
    }

    private Map<String, FileArtifact> loadDashboardArtifacts(Path dashboardsDir) {
        Map<String, FileArtifact> map = new LinkedHashMap<>();
        try {
            List<FileArtifact> artifacts = fileArtifactRepo.findExternalRowsByDir(
                    dashboardsDir.toAbsolutePath().normalize().toString());
            for (FileArtifact fa : artifacts) {
                if (fa.kind() == FileArtifactKind.DASHBOARD && fa.physicalPath() != null) {
                    String normalized = Path.of(fa.physicalPath()).toAbsolutePath().normalize().toString();
                    map.put(normalized, fa);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load dashboard file_artifact rows: {}", e.getMessage());
        }
        return map;
    }

    private DashboardResourceDto buildDashboardDto(Path jsonFile,
                                                   Map<String, FileArtifact> dbByPhysicalPath) throws IOException {
        String normalizedPath = jsonFile.toAbsolutePath().normalize().toString();
        String filename = jsonFile.getFileName().toString();
        String id = filename.substring(0, filename.length() - ".dashboard.json".length());

        // Parse dashboard JSON for title and widget count
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        String title = root.has("title") ? root.get("title").asText() : id;
        int widgetCount = 0;
        if (root.has("widgets") && root.get("widgets").isArray()) {
            widgetCount = root.get("widgets").size();
        }

        // File stats
        long jsonSize = Files.size(jsonFile);
        long updatedAt = Files.getLastModifiedTime(jsonFile).toMillis();

        // Also count companion .html file
        Path htmlFile = jsonFile.resolveSibling(id + ".html");
        long htmlSize = Files.exists(htmlFile) ? Files.size(htmlFile) : 0;
        long totalSize = jsonSize + htmlSize;

        // DB metadata
        FileArtifact artifact = dbByPhysicalPath.get(normalizedPath);
        String originSessionId = artifact != null ? artifact.sessionId() : null;
        long createdAt = artifact != null ? artifact.createdAt().toEpochMilli() : updatedAt;

        return new DashboardResourceDto(id, title, filename, totalSize,
                widgetCount, originSessionId, createdAt, updatedAt);
    }

    // ──────────────────────────────────────────────
    //  Reports
    // ──────────────────────────────────────────────

    /**
     * List reports by scanning subdirectories of {@code reportsRoot()}.
     * Each subdirectory is a report; available formats are determined by
     * which of {@code report.html}, {@code report.pdf}, {@code report.md} exist.
     */
    public List<ReportResourceDto> getReports(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        Path reportsDir = workdirRoot.reportsRoot();
        if (!Files.isDirectory(reportsDir)) {
            return List.of();
        }

        // Load DB rows keyed by report ID (parent dir name)
        Map<String, FileArtifact> dbByReportId = loadReportArtifacts(reportsDir);

        List<ReportResourceDto> results = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(reportsDir)) {
            List<Path> reportDirs = dirs
                    .filter(Files::isDirectory)
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            for (Path reportDir : reportDirs) {
                if (results.size() >= effectiveLimit) break;
                try {
                    ReportResourceDto dto = buildReportDto(reportDir, dbByReportId);
                    if (dto != null) {
                        results.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Skipping report dir {}: {}", reportDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list reports: {}", e.getMessage());
        }
        return results;
    }

    private Map<String, FileArtifact> loadReportArtifacts(Path reportsDir) {
        Map<String, FileArtifact> map = new LinkedHashMap<>();
        try {
            List<FileArtifact> artifacts = fileArtifactRepo.findExternalRowsByDir(
                    reportsDir.toAbsolutePath().normalize().toString());
            for (FileArtifact fa : artifacts) {
                if (fa.kind() == FileArtifactKind.REPORT && fa.physicalPath() != null) {
                    // physicalPath like ".../reports/{id}/report.html" — extract {id}
                    Path parent = Path.of(fa.physicalPath()).getParent();
                    if (parent != null) {
                        map.putIfAbsent(parent.getFileName().toString(), fa);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load report file_artifact rows: {}", e.getMessage());
        }
        return map;
    }

    private ReportResourceDto buildReportDto(Path reportDir,
                                             Map<String, FileArtifact> dbByReportId) throws IOException {
        String reportId = reportDir.getFileName().toString();

        // Check available formats
        List<String> formats = new ArrayList<>();
        List<String> formatFiles = List.of("report.html", "report.pdf", "report.md");
        long totalSize = 0;
        long latestModified = 0;

        try (Stream<Path> files = Files.list(reportDir)) {
            List<Path> allFiles = files.filter(Files::isRegularFile).toList();
            for (Path f : allFiles) {
                String name = f.getFileName().toString();
                if (formatFiles.contains(name)) {
                    formats.add(name.substring("report.".length()));
                }
                try {
                    totalSize += Files.size(f);
                } catch (IOException ignored) {
                    // skip unreadable files
                }
                try {
                    long mod = Files.getLastModifiedTime(f).toMillis();
                    if (mod > latestModified) latestModified = mod;
                } catch (IOException ignored) {
                    // skip
                }
            }
        }

        if (formats.isEmpty()) {
            return null; // no report files in this dir
        }

        // Always list formats in a canonical order
        formats.sort(Comparator.naturalOrder());

        // DB metadata
        FileArtifact artifact = dbByReportId.get(reportId);
        String title = artifact != null && artifact.title() != null
                ? artifact.title() : reportId;
        String originSessionId = artifact != null ? artifact.sessionId() : null;
        long createdAt = artifact != null ? artifact.createdAt().toEpochMilli() : latestModified;
        long updatedAt = artifact != null ? artifact.updatedAt().toEpochMilli() : latestModified;

        return new ReportResourceDto(reportId, title, List.copyOf(formats),
                totalSize, originSessionId, createdAt, updatedAt);
    }

    // ──────────────────────────────────────────────
    //  Exports
    // ──────────────────────────────────────────────

    /**
     * List exports by scanning subdirectories of {@code data-talk/exports/}.
     * Each subdirectory is one export. Row count is read from a metadata file
     * if present, or estimated from file size.
     * Expires 1 hour after creation.
     */
    public List<ExportResourceDto> getExports(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        Path exportsRoot = workdirRoot.dataTalkRoot().resolve("exports");
        if (!Files.isDirectory(exportsRoot)) {
            return List.of();
        }

        List<ExportResourceDto> results = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(exportsRoot)) {
            List<Path> exportDirs = dirs
                    .filter(Files::isDirectory)
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            for (Path exportDir : exportDirs) {
                if (results.size() >= effectiveLimit) break;
                try {
                    ExportResourceDto dto = buildExportDto(exportDir);
                    if (dto != null) {
                        results.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Skipping export dir {}: {}", exportDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list exports: {}", e.getMessage());
        }
        return results;
    }

    private ExportResourceDto buildExportDto(Path exportDir) throws IOException {
        String exportId = exportDir.getFileName().toString();

        // Find the main export file (skip metadata sidecars)
        String mainFile = null;
        long sizeBytes = 0;
        long createdAt = 0;

        List<Path> files;
        try (Stream<Path> s = Files.list(exportDir)) {
            files = s.filter(Files::isRegularFile).toList();
        }

        for (Path f : files) {
            String name = f.getFileName().toString();
            if (name.equals("metadata.json") || name.equals("_index.json")) continue;
            if (mainFile == null) {
                mainFile = name;
                sizeBytes = Files.size(f);
                createdAt = readCreationTime(f);
            } else {
                sizeBytes += Files.size(f);
            }
        }

        if (mainFile == null) {
            return null; // empty export dir
        }

        String filename = mainFile;
        String format = extractFormat(filename);
        long rowCount = readRowCount(exportDir, sizeBytes, format);
        long expiresAt = createdAt + (60 * 60 * 1000); // +1 hour

        return new ExportResourceDto(exportId, filename, format, sizeBytes,
                rowCount, null, createdAt, expiresAt);
    }

    private String extractFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".json") || lower.endsWith(".jsonl")) return "json";
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".sql")) return "sql_insert";
        if (lower.endsWith(".parquet")) return "parquet";
        // fallback: extension without dot
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "unknown";
    }

    private long readRowCount(Path exportDir, long sizeBytes, String format) {
        // Try metadata.json first
        Path metaFile = exportDir.resolve("metadata.json");
        if (Files.isRegularFile(metaFile)) {
            try {
                JsonNode meta = objectMapper.readTree(metaFile.toFile());
                if (meta.has("rowCount")) return meta.get("rowCount").asLong();
                if (meta.has("rows")) return meta.get("rows").asLong();
            } catch (Exception ignored) {
                // fall through to estimation
            }
        }

        // Estimate from file size
        return switch (format) {
            case "csv" -> Math.max(0, (sizeBytes / 200) - 1); // rough estimate ~200 bytes/row
            case "json" -> Math.max(0, (sizeBytes / 500) - 1);
            default -> 0L;
        };
    }

    private long readCreationTime(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return attrs.creationTime().toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    // ──────────────────────────────────────────────
    //  Semantic
    // ──────────────────────────────────────────────

    /**
     * List semantic models by scanning subdirectories of
     * {@code data-talk/semantic/} (one directory per connection).
     * Reads YAML model files and patch files to determine domain status.
     */
    public List<SemanticResourceDto> getSemantic(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        Path semanticRoot = workdirRoot.dataTalkRoot().resolve("semantic");
        if (!Files.isDirectory(semanticRoot)) {
            return List.of();
        }

        // Build connection name lookup
        Map<String, String> connectionNames = loadConnectionNames();

        List<SemanticResourceDto> results = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(semanticRoot)) {
            List<Path> connDirs = dirs
                    .filter(Files::isDirectory)
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            for (Path connDir : connDirs) {
                if (results.size() >= effectiveLimit) break;
                String connectionId = connDir.getFileName().toString();
                String connectionName = connectionNames.getOrDefault(connectionId, connectionId);
                try {
                    List<SemanticResourceDto> models = buildSemanticDtos(connDir, connectionId, connectionName);
                    for (SemanticResourceDto dto : models) {
                        if (results.size() >= effectiveLimit) break;
                        results.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Skipping semantic dir {}: {}", connDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list semantic dirs: {}", e.getMessage());
        }
        return results;
    }

    private Map<String, String> loadConnectionNames() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, name FROM connections");
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                String name = (String) row.get("name");
                if (id != null) map.put(id, name != null ? name : id);
            }
        } catch (Exception e) {
            log.warn("Failed to load connection names: {}", e.getMessage());
        }
        return map;
    }

    private List<SemanticResourceDto> buildSemanticDtos(Path connDir, String connectionId,
                                                        String connectionName) throws IOException {
        List<SemanticResourceDto> results = new ArrayList<>();

        // Find all *.model.yaml files
        List<Path> modelFiles;
        try (Stream<Path> files = Files.list(connDir)) {
            modelFiles = files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".model.yaml") && !name.startsWith("_");
                    })
                    .toList();
        }

        for (Path modelFile : modelFiles) {
            String filename = modelFile.getFileName().toString();
            String domain = filename.substring(0, filename.length() - ".model.yaml".length());

            // Check for pending version (in pending/ subdir)
            Path pendingFile = connDir.resolve("pending").resolve(domain + ".model.yaml");
            boolean hasPending = Files.isRegularFile(pendingFile);

            // Check for patches (uncompacted changes)
            Path patchFile = connDir.resolve(domain + ".model.yaml.patches.jsonl");
            boolean hasPatches = Files.isRegularFile(patchFile) && Files.size(patchFile) > 0;

            // Determine status
            String status;
            if (hasPending) {
                status = "pending";
            } else if (hasPatches) {
                status = "active"; // has uncompacted changes
            } else {
                status = "active";
            }

            // Calculate total size (model file + patches)
            long sizeBytes = Files.size(modelFile);
            if (hasPatches) {
                try { sizeBytes += Files.size(patchFile); } catch (IOException ignored) {}
            }
            if (hasPending) {
                try { sizeBytes += Files.size(pendingFile); } catch (IOException ignored) {}
            }

            long updatedAt = Files.getLastModifiedTime(modelFile).toMillis();

            results.add(new SemanticResourceDto(domain, connectionId, connectionName,
                    status, sizeBytes, updatedAt));
        }

        // Also check for pending models that don't have an active version yet
        Path pendingDir = connDir.resolve("pending");
        if (Files.isDirectory(pendingDir)) {
            try (Stream<Path> pendingFiles = Files.list(pendingDir)) {
                List<Path> pendingOnly = pendingFiles
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".model.yaml");
                        })
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            String domain = name.substring(0, name.length() - ".model.yaml".length());
                            return results.stream().noneMatch(dto -> dto.domain().equals(domain));
                        })
                        .toList();

                for (Path pf : pendingOnly) {
                    String name = pf.getFileName().toString();
                    String domain = name.substring(0, name.length() - ".model.yaml".length());
                    long sizeBytes = Files.size(pf);
                    long updatedAt = Files.getLastModifiedTime(pf).toMillis();
                    results.add(new SemanticResourceDto(domain, connectionId, connectionName,
                            "pending", sizeBytes, updatedAt));
                }
            }
        }

        return results;
    }

    // ──────────────────────────────────────────────
    //  Uploads
    // ──────────────────────────────────────────────

    /**
     * List uploaded files from the {@code uploaded_file} table, joined with
     * the physical file-system under {@code data-talk/uploads/}.
     * Expires 24 hours after creation.
     */
    public List<UploadResourceDto> getUploads(int limit) {
        int effectiveLimit = Math.min(limit, MAX_LIMIT);
        Path uploadsRoot = workdirRoot.dataTalkRoot().resolve("uploads");

        List<UploadResourceDto> results = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, session_id, filename, mime_type, size_bytes, created_at " +
                    "FROM uploaded_file ORDER BY created_at DESC LIMIT ?",
                    effectiveLimit);

            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                String sessionId = (String) row.get("session_id");
                String filename = (String) row.get("filename");
                String mimeType = (String) row.get("mime_type");
                long sizeBytes = toLong(row.get("size_bytes"));
                long createdAt = toLong(row.get("created_at"));
                long expiresAt = createdAt + (24 * 60 * 60 * 1000); // +24 hours

                results.add(new UploadResourceDto(id, filename, mimeType, sizeBytes,
                        sessionId, createdAt, expiresAt));
            }
        } catch (Exception e) {
            log.warn("Failed to query uploaded_file: {}", e.getMessage());
        }
        return results;
    }

    // ──────────────────────────────────────────────
    //  Deletion
    // ──────────────────────────────────────────────

    /**
     * Delete a dashboard: remove {@code {id}.dashboard.json} and
     * {@code {id}.html} from the filesystem, and delete the corresponding
     * {@code file_artifact} row.
     */
    public void deleteDashboard(String id) {
        Path dashboardsDir = workdirRoot.dashboardsRoot();
        Path jsonFile = dashboardsDir.resolve(id + ".dashboard.json");
        Path htmlFile = dashboardsDir.resolve(id + ".html");

        try {
            Files.deleteIfExists(jsonFile);
            Files.deleteIfExists(htmlFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete dashboard files for " + id, e);
        }

        // Delete file_artifact row (look up by physical path)
        try {
            List<FileArtifact> artifacts = fileArtifactRepo.findExternalRowsByDir(
                    dashboardsDir.toAbsolutePath().normalize().toString());
            FileArtifactKind targetKind = FileArtifactKind.DASHBOARD;
            for (FileArtifact fa : artifacts) {
                if (fa.kind() == targetKind && fa.physicalPath() != null) {
                    Path faPath = Path.of(fa.physicalPath());
                    if (faPath.getFileName().toString().equals(id + ".dashboard.json")
                            || faPath.getFileName().toString().equals(id + ".html")) {
                        fileArtifactRepo.deleteById(fa.id());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete file_artifact rows for dashboard {}: {}", id, e.getMessage());
        }
    }

    /**
     * Delete a report: recursively remove {@code reports/{id}/} and delete the
     * associated {@code file_artifact} row(s).
     */
    public void deleteReport(String id) {
        Path reportDir = workdirRoot.reportDir(id);
        try {
            deleteRecursively(reportDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete report dir " + id, e);
        }

        // Delete file_artifact rows for this report
        try {
            List<FileArtifact> artifacts = fileArtifactRepo.findExternalRowsByDir(
                    workdirRoot.reportsRoot().toAbsolutePath().normalize().toString());
            FileArtifactKind targetKind = FileArtifactKind.REPORT;
            for (FileArtifact fa : artifacts) {
                if (fa.kind() == targetKind && fa.physicalPath() != null) {
                    Path parent = Path.of(fa.physicalPath()).getParent();
                    if (parent != null && parent.getFileName().toString().equals(id)) {
                        fileArtifactRepo.deleteById(fa.id());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete file_artifact rows for report {}: {}", id, e.getMessage());
        }
    }

    /**
     * Delete an export: remove the {@code exports/{exportId}/} directory.
     */
    public void deleteExport(String exportId) {
        Path exportDir = workdirRoot.dataTalkRoot().resolve("exports").resolve(exportId);
        try {
            deleteRecursively(exportDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete export dir " + exportId, e);
        }
    }

    /**
     * Delete a semantic model for a given domain and connection:
     * removes the YAML model file and its patches file.
     */
    public void deleteSemantic(String domain, String connectionId) {
        Path connDir = workdirRoot.dataTalkRoot().resolve("semantic").resolve(connectionId);
        if (!Files.isDirectory(connDir)) {
            return;
        }
        Path modelFile = connDir.resolve(domain + ".model.yaml");
        Path patchFile = connDir.resolve(domain + ".model.yaml.patches.jsonl");
        try {
            Files.deleteIfExists(modelFile);
            Files.deleteIfExists(patchFile);
            log.info("Deleted semantic model {}/{}", connectionId, domain);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to delete semantic model " + connectionId + "/" + domain, e);
        }
    }

    /**
     * Delete an uploaded file: remove the {@code uploads/{id}/} directory
     * (if it exists) and delete the {@code uploaded_file} table row.
     */
    public void deleteUpload(String id) {
        Path uploadDir = workdirRoot.dataTalkRoot().resolve("uploads").resolve(id);
        try {
            if (Files.isDirectory(uploadDir)) {
                deleteRecursively(uploadDir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete upload dir " + id, e);
        }

        try {
            jdbc.update("DELETE FROM uploaded_file WHERE id = ?", id);
        } catch (Exception e) {
            log.warn("Failed to delete uploaded_file row {}: {}", id, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  Resource Overview
    // ──────────────────────────────────────────────

    /**
     * Build a summary of all 5 resource directories with file counts and
     * total size for each.
     *
     * @return map keyed by directory name:
     *         {@code dashboards}, {@code reports}, {@code exports},
     *         {@code semantic}, {@code uploads}
     */
    public Map<String, StorageOverviewDto.ResourceDirSummary> getResourceOverview() {
        Map<String, StorageOverviewDto.ResourceDirSummary> overview = new LinkedHashMap<>();

        // Dashboards
        Path dashDir = workdirRoot.dashboardsRoot();
        overview.put("dashboards", summarizeDir(dashDir));

        // Reports
        Path reportsDir = workdirRoot.reportsRoot();
        overview.put("reports", summarizeDir(reportsDir));

        // Exports
        Path exportsDir = workdirRoot.dataTalkRoot().resolve("exports");
        overview.put("exports", summarizeDir(exportsDir));

        // Semantic
        Path semanticDir = workdirRoot.dataTalkRoot().resolve("semantic");
        overview.put("semantic", summarizeDir(semanticDir));

        // Uploads (file count from DB, size from disk)
        Path uploadsDir = workdirRoot.dataTalkRoot().resolve("uploads");
        long uploadFileCount = 0;
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM uploaded_file", Integer.class);
            uploadFileCount = count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count uploaded_file rows: {}", e.getMessage());
        }
        long uploadSize = dirSize(uploadsDir);
        overview.put("uploads", new StorageOverviewDto.ResourceDirSummary(uploadFileCount, uploadSize));

        return overview;
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private StorageOverviewDto.ResourceDirSummary summarizeDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return new StorageOverviewDto.ResourceDirSummary(0, 0);
        }
        long count = 0;
        long size = 0;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    long subSize = dirSize(entry);
                    if (subSize > 0) {
                        count++;
                        size += subSize;
                    }
                } else if (Files.isRegularFile(entry)) {
                    count++;
                    size += Files.size(entry);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to summarize dir {}: {}", dir, e.getMessage());
        }
        return new StorageOverviewDto.ResourceDirSummary(count, size);
    }

    /**
     * Recursively compute the total size of a directory tree.
     * Returns 0 if the directory does not exist or is unreadable.
     */
    static long dirSize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        long total = 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p)) {
                    try {
                        total += Files.size(p);
                    } catch (IOException ignored) {
                        // skip unreadable file
                    }
                }
            }
        } catch (IOException e) {
            // directory not readable — treat as zero size
        }
        return total;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    deleteRecursively(file);
                }
            }
        }
        Files.deleteIfExists(dir);
    }

    private long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
