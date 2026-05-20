package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.report.LedgerSkillResolver;
import com.datatalk.application.report.ReportSystemStatus;
import com.datatalk.domain.report.Report;
import com.datatalk.domain.report.ReportDerivativeStatus;
import com.datatalk.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportRepository reportRepo;
    private final ReportSystemStatus systemStatus;
    private final LedgerSkillResolver ledgerSkill;
    private final ObjectMapper mapper;
    private final SessionWorkdirRoot workdirRoot;

    public ReportController(
            ReportRepository reportRepo,
            ReportSystemStatus systemStatus,
            LedgerSkillResolver ledgerSkill,
            ObjectMapper mapper,
            SessionWorkdirRoot workdirRoot) {
        this.reportRepo = reportRepo;
        this.systemStatus = systemStatus;
        this.ledgerSkill = ledgerSkill;
        this.mapper = mapper;
        this.workdirRoot = workdirRoot;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @RequestParam(value = "groupId", required = false) String groupId) {
        String effectiveWs = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : null;
        List<Report> rows = reportRepo.findByWorkspaceId(effectiveWs, groupId);
        if (!rows.isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Report r : rows) {
                Map<String, Object> dto = toListItem(r);
                if (groupId == null) {
                    dto.put("groupSize", reportRepo.countInGroup(
                            effectiveWs != null ? effectiveWs : r.workspaceId(), r.groupId()));
                } else {
                    dto.put("groupSize", rows.size());
                }
                items.add(dto);
            }
            return ResponseEntity.ok(Map.of("items", items));
        }
        // Fallback: scan filesystem when report table is empty
        return ResponseEntity.ok(Map.of("items", scanFilesystemReports()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable("id") String id) {
        Report r = reportRepo.findById(id).orElse(null);
        if (r == null) {
            // Fallback: try filesystem
            Path reportDir = workdirRoot.reportDir(id);
            if (Files.isDirectory(reportDir)) {
                return ResponseEntity.ok(scanSingleReport(reportDir, id));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "report not found"));
        }
        return ResponseEntity.ok(toDetail(r));
    }

    @GetMapping("/{id}/download/{format}")
    public ResponseEntity<?> download(@PathVariable("id") String id, @PathVariable("format") String format) {
        Optional<Report> opt = reportRepo.findById(id);
        if (opt.isEmpty()) {
            // Fallback: serve directly from filesystem
            Path reportDir = workdirRoot.reportDir(id);
            if (!Files.isDirectory(reportDir)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "report not found"));
            }
            Path file = reportDir.resolve("report." + format);
            if (!Files.isRegularFile(file)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "artifact missing on disk"));
            }
            MediaType contentType = switch (format) {
                case "html" -> MediaType.TEXT_HTML;
                case "pdf" -> MediaType.APPLICATION_PDF;
                case "md" -> MediaType.TEXT_PLAIN;
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"report-" + id + "." + format + "\"")
                    .body(new FileSystemResource(file));
        }
        Report r = opt.get();
        Path target = artifactPathForFormat(r, format);
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported format: " + format));
        }

        // status gating for pdf / md
        switch (format) {
            case "pdf" -> {
                if (r.pdfStatus() == ReportDerivativeStatus.PROCESSING) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                            "status", "processing", "pdfStatus", "processing", "retryAfterSec", 3));
                }
                if (r.pdfStatus() == ReportDerivativeStatus.FAILED) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                            "status", "failed", "pdfFailReason", r.pdfFailReason()));
                }
            }
            case "md" -> {
                if (r.mdStatus() == ReportDerivativeStatus.PROCESSING) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                            "status", "processing", "mdStatus", "processing", "retryAfterSec", 3));
                }
                if (r.mdStatus() == ReportDerivativeStatus.FAILED) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                            "status", "failed", "mdFailReason", r.mdFailReason()));
                }
            }
            default -> { /* html / json — no status gating */ }
        }

        if (!Files.exists(target)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "artifact missing on disk"));
        }
        MediaType contentType = switch (format) {
            case "html" -> MediaType.TEXT_HTML;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "md" -> MediaType.TEXT_PLAIN;
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        Resource resource = new FileSystemResource(target);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"report-" + r.id() + "." + format + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        Optional<Report> opt = reportRepo.findById(id);
        if (opt.isPresent()) {
            reportRepo.deleteById(id);
        }
        // Always clean filesystem dir
        Path reportDir = workdirRoot.reportDir(id);
        try {
            if (Files.isDirectory(reportDir)) {
                try (var stream = Files.walk(reportDir)) {
                    stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete report dir for {}: {}", id, e.getMessage());
        }
        if (opt.isEmpty() && !Files.isDirectory(reportDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "report not found"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> systemStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chromiumReady", systemStatus.chromiumReady());
        body.put("fontsReady", ledgerSkill.fontsReady());
        body.put("skillReady", ledgerSkill.skillReady());
        if (systemStatus.message() != null) body.put("message", systemStatus.message());
        // proactively refresh font/skill ready flags
        systemStatus.setFontsReady(ledgerSkill.fontsReady());
        systemStatus.setSkillReady(ledgerSkill.skillReady());
        return ResponseEntity.ok(body);
    }

    /** Static asset endpoint — serve ledger skill assets (fonts / styles / scripts) under /_assets/**. */
    @GetMapping("/_assets/{*subPath}")
    public ResponseEntity<?> serveAsset(@PathVariable("subPath") String subPath) {
        if (subPath == null) return ResponseEntity.badRequest().build();
        // strip leading slash
        String safe = subPath.startsWith("/") ? subPath.substring(1) : subPath;
        // Disallow path traversal
        if (safe.contains("..")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "path traversal forbidden"));
        }
        Path target;
        if (safe.startsWith("scripts/echarts.min.js")) {
            // ECharts is bundled by the bezel/charts pipeline at classpath:/static/bezel/echarts.min.js
            // ReportController serves it via a classpath resource fallback (read in-process).
            Resource cp = new org.springframework.core.io.ClassPathResource("static/bezel/echarts.min.js");
            if (!cp.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/javascript"))
                    .body(cp);
        }
        target = ledgerSkill.skillRoot().resolve("assets").resolve(safe).normalize();
        if (!target.startsWith(ledgerSkill.skillRoot().resolve("assets"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "outside assets root"));
        }
        if (!Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        MediaType ct;
        if (safe.endsWith(".css")) ct = MediaType.parseMediaType("text/css");
        else if (safe.endsWith(".otf")) ct = MediaType.parseMediaType("font/otf");
        else if (safe.endsWith(".ttf")) ct = MediaType.parseMediaType("font/ttf");
        else if (safe.endsWith(".js")) ct = MediaType.parseMediaType("application/javascript");
        else ct = MediaType.APPLICATION_OCTET_STREAM;
        // CORS headers are handled by CorsFilter (iframeConfig) for /api/reports/_assets/**
        return ResponseEntity.ok()
                .contentType(ct)
                .body(new FileSystemResource(target));
    }

    private Path artifactPathForFormat(Report r, String format) {
        try {
            JsonNode tree = mapper.readTree(r.artifactPathsJson());
            String key = switch (format) {
                case "html" -> "html";
                case "pdf" -> "pdf";
                case "md" -> "md";
                case "json" -> "json";
                default -> null;
            };
            if (key == null) return null;
            String p = tree.path(key).asText("");
            return p.isBlank() ? null : Path.of(p);
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, Object> toListItem(Report r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("workspaceId", r.workspaceId());
        m.put("groupId", r.groupId());
        m.put("version", r.version());
        m.put("title", r.title());
        m.put("subtitle", r.subtitle());
        m.put("templateId", r.templateId());
        m.put("generatedAt", r.generatedAt().toEpochMilli());
        m.put("pdfStatus", r.pdfStatus().dbValue());
        m.put("mdStatus", r.mdStatus().dbValue());
        return m;
    }

    private Map<String, Object> toDetail(Report r) {
        Map<String, Object> m = new LinkedHashMap<>(toListItem(r));
        m.put("accentColor", r.accentColor());
        m.put("templateVersion", r.templateVersion());
        m.put("generatedBySessionId", r.generatedBySessionId());
        m.put("userPrompt", r.userPrompt());
        m.put("pdfFailReason", r.pdfFailReason());
        m.put("mdFailReason", r.mdFailReason());
        m.put("artifactPaths", parseArtifacts(r));
        return m;
    }

    private Object parseArtifacts(Report r) {
        try {
            return mapper.readTree(r.artifactPathsJson());
        } catch (IOException e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> scanFilesystemReports() {
        Path reportsDir = workdirRoot.reportsRoot();
        if (!Files.isDirectory(reportsDir)) return List.of();

        List<Map<String, Object>> items = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(reportsDir)) {
            List<Path> reportDirs = dirs
                    .filter(Files::isDirectory)
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) { return 0; }
                    })
                    .toList();

            for (Path reportDir : reportDirs) {
                String reportId = reportDir.getFileName().toString();
                boolean hasHtml = Files.isRegularFile(reportDir.resolve("report.html"));
                boolean hasPdf = Files.isRegularFile(reportDir.resolve("report.pdf"));
                boolean hasMd = Files.isRegularFile(reportDir.resolve("report.md"));
                if (!hasHtml && !hasPdf && !hasMd) continue;

                long createdAt;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(reportDir, BasicFileAttributes.class);
                    createdAt = attrs.creationTime().toMillis();
                } catch (IOException e) {
                    createdAt = System.currentTimeMillis();
                }

                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", reportId);
                dto.put("workspaceId", "");
                dto.put("groupId", reportId);
                dto.put("version", 1);
                dto.put("title", reportId);
                dto.put("subtitle", (String) null);
                dto.put("templateId", "unknown");
                dto.put("generatedAt", createdAt);
                dto.put("pdfStatus", hasPdf ? "ready" : "failed");
                dto.put("mdStatus", hasMd ? "ready" : "failed");
                dto.put("groupSize", 1);
                items.add(dto);
            }
        } catch (IOException e) {
            log.warn("Failed to scan filesystem reports: {}", e.getMessage());
        }
        return items;
    }

    private Map<String, Object> scanSingleReport(Path reportDir, String reportId) {
        boolean hasHtml = Files.isRegularFile(reportDir.resolve("report.html"));
        boolean hasPdf = Files.isRegularFile(reportDir.resolve("report.pdf"));
        boolean hasMd = Files.isRegularFile(reportDir.resolve("report.md"));

        long createdAt;
        try {
            BasicFileAttributes attrs = Files.readAttributes(reportDir, BasicFileAttributes.class);
            createdAt = attrs.creationTime().toMillis();
        } catch (IOException e) {
            createdAt = System.currentTimeMillis();
        }

        Map<String, Object> artifactPaths = new LinkedHashMap<>();
        artifactPaths.put("html", hasHtml ? reportDir.resolve("report.html").toString() : null);
        artifactPaths.put("pdf", hasPdf ? reportDir.resolve("report.pdf").toString() : null);
        artifactPaths.put("md", hasMd ? reportDir.resolve("report.md").toString() : null);

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", reportId);
        dto.put("workspaceId", "");
        dto.put("groupId", reportId);
        dto.put("version", 1);
        dto.put("title", reportId);
        dto.put("subtitle", null);
        dto.put("templateId", "unknown");
        dto.put("generatedAt", createdAt);
        dto.put("pdfStatus", hasPdf ? "ready" : "failed");
        dto.put("mdStatus", hasMd ? "ready" : "failed");
        dto.put("groupSize", 1);
        dto.put("accentColor", "#4F46E5");
        dto.put("templateVersion", "1");
        dto.put("generatedBySessionId", null);
        dto.put("userPrompt", null);
        dto.put("pdfFailReason", null);
        dto.put("mdFailReason", null);
        dto.put("artifactPaths", artifactPaths);
        return dto;
    }
}
