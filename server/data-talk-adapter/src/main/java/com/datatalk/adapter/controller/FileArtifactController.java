package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.domain.fileartifact.FileArtifact;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileArtifactController {

    private final FileArtifactService svc;

    public FileArtifactController(FileArtifactService svc) {
        this.svc = svc;
    }

    @GetMapping("/sessions/{sessionId}/files")
    public List<FileArtifact> listForSession(@PathVariable String sessionId) {
        return svc.listForSession(sessionId);
    }

    @GetMapping("/connections/{connectionId}/files")
    public List<FileArtifact> listForConnection(@PathVariable String connectionId) {
        return svc.listArchivedForConnection(connectionId);
    }

    @PostMapping("/files/{fileArtifactId}/mark-candidate")
    public ResponseEntity<Void> markCandidate(@PathVariable String fileArtifactId) {
        svc.markCandidate(fileArtifactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/files/{fileArtifactId}/archive")
    public ResponseEntity<?> archive(
            @PathVariable String sessionId,
            @PathVariable String fileArtifactId) {
        var out = svc.archive(sessionId, fileArtifactId);
        return switch (out) {
            case FileArtifactService.ArchiveOutcome.Success s -> ResponseEntity.ok(s.artifact());
            case FileArtifactService.ArchiveOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.ArchiveOutcome.WrongStatus ws -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "wrong_status", "actual", ws.actual().dbValue()));
            case FileArtifactService.ArchiveOutcome.SessionMissing sm -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "session_missing"));
            case FileArtifactService.ArchiveOutcome.ConnectionMissing cm -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "connection_missing"));
            case FileArtifactService.ArchiveOutcome.TocTou tt -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "toctou_changed"));
            case FileArtifactService.ArchiveOutcome.DiskFull df -> ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .body(Map.of("error", "disk_full"));
            case FileArtifactService.ArchiveOutcome.MvFailed mf -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }

    @PostMapping("/files/{fileArtifactId}/discard")
    public ResponseEntity<?> discard(@PathVariable String fileArtifactId) {
        var out = svc.discard(fileArtifactId);
        return switch (out) {
            case FileArtifactService.DiscardOutcome.Success s -> ResponseEntity.noContent().build();
            case FileArtifactService.DiscardOutcome.AlreadyDiscarded a -> ResponseEntity.noContent().build();
            case FileArtifactService.DiscardOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.DiscardOutcome.TocTou tt -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "toctou_changed"));
            case FileArtifactService.DiscardOutcome.DiskFull df -> ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .body(Map.of("error", "disk_full"));
            case FileArtifactService.DiscardOutcome.MvFailed mf -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }
}
