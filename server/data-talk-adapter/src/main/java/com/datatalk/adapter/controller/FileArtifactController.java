package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.domain.fileartifact.FileArtifact;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
