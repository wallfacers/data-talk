package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.ResourceDirectoryService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.housekeeping.HousekeepingScheduler;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final SessionWorkdirRoot workdirRoot;
    private final FileArtifactRepository fileArtifacts;
    private final HousekeepingScheduler scheduler;
    private final FileArtifactService fileArtifactService;
    private final ResourceDirectoryService resourceDirectoryService;

    public MaintenanceController(
            SessionWorkdirRoot workdirRoot,
            FileArtifactRepository fileArtifacts,
            HousekeepingScheduler scheduler,
            FileArtifactService fileArtifactService,
            ResourceDirectoryService resourceDirectoryService) {
        this.workdirRoot = workdirRoot;
        this.fileArtifacts = fileArtifacts;
        this.scheduler = scheduler;
        this.fileArtifactService = fileArtifactService;
        this.resourceDirectoryService = resourceDirectoryService;
    }

    @GetMapping("/storage-overview")
    public ResponseEntity<StorageOverviewDto> storageOverview() {
        Path root = workdirRoot.dataTalkRoot();
        Map<String, StorageOverviewDto.BreakdownItem> breakdown = new LinkedHashMap<>();
        long total = 0;

        // OpenCode infra
        long ocSize = dirSize(root.resolve("opencode"));
        total += ocSize;
        breakdown.put("opencodeInfra", new StorageOverviewDto.BreakdownItem(ocSize, "OpenCode 基础设施"));

        // Sessions
        long sessionsSize = dirSize(workdirRoot.sessionsRoot());
        total += sessionsSize;
        breakdown.put("sessions", new StorageOverviewDto.BreakdownItem(sessionsSize, "Sessions"));

        // Workspaces (assets)
        long wsSize = dirSize(workdirRoot.workspacesRoot());
        total += wsSize;
        int orphanedCount = fileArtifacts.countOrphanedArchived();
        breakdown.put("workspaces", new StorageOverviewDto.BreakdownItem(wsSize, "Workspaces (资产)"));

        // Trash
        Path trashDir = workdirRoot.trashRoot();
        long trashSize = dirSize(trashDir);
        total += trashSize;
        breakdown.put("trash", new StorageOverviewDto.BreakdownItem(trashSize, "_trash"));

        // Legacy
        Path legacyDir = workdirRoot.legacyRoot();
        long legacySize = dirSize(legacyDir);
        total += legacySize;
        breakdown.put("legacy", new StorageOverviewDto.BreakdownItem(legacySize, "_legacy"));

        Map<String, StorageOverviewDto.ResourceDirSummary> resourceDirectories = resourceDirectoryService.getResourceOverview();
        return ResponseEntity.ok(new StorageOverviewDto(
                root.toAbsolutePath().normalize().toString(),
                total,
                breakdown,
                null, // lastHousekeepingRunAt — read from housekeeping.log if exists
                resourceDirectories));
    }

    @PostMapping("/cleanup-trash")
    public ResponseEntity<CleanupStatsDto> cleanupTrash() {
        int removed = scheduler.cleanupTrashNow();
        return ResponseEntity.ok(new CleanupStatsDto(removed, removed));
    }

    @PostMapping("/cleanup-legacy")
    public ResponseEntity<CleanupStatsDto> cleanupLegacy() {
        int removed = scheduler.cleanupLegacyNow();
        return ResponseEntity.ok(new CleanupStatsDto(removed, 0));
    }

    @GetMapping("/orphaned-files")
    public ResponseEntity<List<OrphanedFileDto>> orphanedFiles() {
        List<FileArtifact> rows = fileArtifacts.findOrphanedArchived(200);
        List<OrphanedFileDto> dtos = rows.stream().map(r -> {
            Map<String, Object> meta = r.metadata();
            return new OrphanedFileDto(
                    r.id(),
                    r.filename(),
                    r.kind().dbValue(),
                    r.sizeBytes(),
                    r.title(),
                    r.summary(),
                    meta.getOrDefault("orphanedFromConnection", "").toString(),
                    meta.getOrDefault("orphanedFromConnectionId", "").toString(),
                    meta.containsKey("orphanedAt") ? ((Number) meta.get("orphanedAt")).longValue() : 0L,
                    r.archivedAt() != null ? r.archivedAt().toString() : null);
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/files/{fileArtifactId}/reattach")
    public ResponseEntity<?> reattach(
            @PathVariable String fileArtifactId,
            @RequestBody ReattachRequest req) {
        var out = fileArtifactService.reattach(fileArtifactId, req.connectionId());
        return switch (out) {
            case FileArtifactService.ReattachOutcome.Success s -> ResponseEntity.ok(s.artifact());
            case FileArtifactService.ReattachOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.ReattachOutcome.NotArchived na -> ResponseEntity.status(409)
                    .body(Map.of("error", "not_archived", "actual", na.actual().dbValue()));
            case FileArtifactService.ReattachOutcome.ConnectionNotFound cn ->
                    ResponseEntity.status(404).body(Map.of("error", "connection_not_found"));
            case FileArtifactService.ReattachOutcome.MvFailed mf ->
                    ResponseEntity.status(503).body(Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }

    // ── Resource directory list endpoints (task 2.3) ──

    @GetMapping("/dashboards")
    public ResponseEntity<List<DashboardResourceDto>> dashboards() {
        return ResponseEntity.ok(resourceDirectoryService.getDashboards(200));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportResourceDto>> reports() {
        return ResponseEntity.ok(resourceDirectoryService.getReports(200));
    }

    @GetMapping("/exports")
    public ResponseEntity<List<ExportResourceDto>> exports() {
        return ResponseEntity.ok(resourceDirectoryService.getExports(200));
    }

    @GetMapping("/semantic")
    public ResponseEntity<List<SemanticResourceDto>> semantic() {
        return ResponseEntity.ok(resourceDirectoryService.getSemantic(200));
    }

    @GetMapping("/uploads")
    public ResponseEntity<List<UploadResourceDto>> uploads() {
        return ResponseEntity.ok(resourceDirectoryService.getUploads(200));
    }

    // ── Resource directory delete endpoints (task 2.4) ──

    @DeleteMapping("/dashboards/{id}")
    public ResponseEntity<Void> deleteDashboard(@PathVariable String id) {
        resourceDirectoryService.deleteDashboard(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable String id) {
        resourceDirectoryService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/exports/{exportId}")
    public ResponseEntity<Void> deleteExport(@PathVariable String exportId) {
        resourceDirectoryService.deleteExport(exportId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/semantic/{domain}")
    public ResponseEntity<Void> deleteSemantic(
            @PathVariable String domain,
            @RequestParam String connectionId) {
        resourceDirectoryService.deleteSemantic(domain, connectionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/uploads/{id}")
    public ResponseEntity<Void> deleteUpload(@PathVariable String id) {
        resourceDirectoryService.deleteUpload(id);
        return ResponseEntity.noContent().build();
    }

    // ── Resource directory preview endpoints (task 3.2) ──
    // TODO: wire up ResourcePreviewService when implemented

    private long dirSize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }
}
