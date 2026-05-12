package com.datatalk.adapter.controller;

import com.datatalk.application.ingestion.IngestionCredentialService;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final IngestionCredentialService credService;
    private final IngestionCredentialRepository credRepo;

    public IngestionController(IngestionCredentialService credService,
                               IngestionCredentialRepository credRepo) {
        this.credService = credService;
        this.credRepo = credRepo;
    }

    public record CredentialCreateRequest(
        String name, String authScheme, Map<String, String> configNonSecret, String secret) {}

    public record CredentialView(
        String id, String name, String authScheme, Map<String, String> configNonSecret,
        boolean hasSecret, long createdAt, long updatedAt) {

        public static CredentialView of(IngestionCredential c) {
            return new CredentialView(c.id(), c.name(),
                c.scheme().dbValue(), c.configNonSecret(),
                c.vaultId() != null, c.createdAt(), c.updatedAt());
        }
    }

    @PostMapping("/credentials")
    public Map<String, Object> create(@RequestBody CredentialCreateRequest req) {
        AuthScheme scheme = AuthScheme.valueOf(req.authScheme().toUpperCase());
        String id = credService.create(
            req.name(), scheme,
            req.configNonSecret() == null ? Map.of() : req.configNonSecret(),
            req.secret());
        return Map.of("id", id);
    }

    @GetMapping("/credentials")
    public Map<String, Object> list() {
        List<CredentialView> items = credRepo.findAll().stream()
            .map(CredentialView::of).toList();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/credentials/{id}")
    public ResponseEntity<CredentialView> get(@PathVariable String id) {
        return credRepo.findById(id)
            .map(CredentialView::of)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam(defaultValue = "false") boolean force) {
        try {
            credService.delete(id, force);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }
}
