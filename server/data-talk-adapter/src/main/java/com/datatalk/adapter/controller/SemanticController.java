package com.datatalk.adapter.controller;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.application.semantic.VerifiedQueryRouter;
import com.datatalk.domain.semantic.VerifiedQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/semantic")
public class SemanticController {

    private final SemanticModelRepository repository;
    private final VerifiedQueryRouter router;

    public SemanticController(SemanticModelRepository repository, VerifiedQueryRouter router) {
        this.repository = repository;
        this.router = router;
    }

    @GetMapping("/{connectionId}/domains")
    public ResponseEntity<?> listDomains(@PathVariable String connectionId) {
        return ResponseEntity.ok(Map.of("domains", repository.listDomains(connectionId)));
    }

    @GetMapping("/{connectionId}/domain/{name}")
    public ResponseEntity<?> getDomain(@PathVariable String connectionId, @PathVariable String name) {
        return repository.loadDomain(connectionId, name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{connectionId}/pending")
    public ResponseEntity<?> listPending(@PathVariable String connectionId) {
        List<String> pending = repository.listPending(connectionId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String domain : pending) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("domain", domain);
            items.add(item);
        }
        return ResponseEntity.ok(Map.of("pending", items));
    }

    @PostMapping("/{connectionId}/pending/{domain}/accept")
    public ResponseEntity<?> acceptPending(@PathVariable String connectionId, @PathVariable String domain) {
        try {
            repository.acceptPending(connectionId, domain);
            return ResponseEntity.ok(Map.of("domain", domain, "status", "accepted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{connectionId}/pending/{domain}/reject")
    public ResponseEntity<?> rejectPending(@PathVariable String connectionId, @PathVariable String domain) {
        try {
            repository.rejectPending(connectionId, domain);
            return ResponseEntity.ok(Map.of("domain", domain, "status", "rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{connectionId}/verified-query")
    public ResponseEntity<?> recordVerifiedQuery(@PathVariable String connectionId, @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String sql = (String) body.get("sql");
        String modelRef = (String) body.get("modelRef");
        if (question == null || sql == null || modelRef == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_REQUIRED_FIELDS"));
        }
        String id = "vq_" + modelRef + "_" + System.currentTimeMillis();
        VerifiedQuery vq = new VerifiedQuery(id, question, sql, modelRef, 1,
            java.time.Instant.now(), "user", java.time.Instant.now(), false);
        String vqId = repository.recordVerifiedQuery(connectionId, vq);
        return ResponseEntity.ok(Map.of("id", vqId, "question", question));
    }

    @GetMapping("/{connectionId}/verified-queries")
    public ResponseEntity<?> listVerifiedQueries(
        @PathVariable String connectionId,
        @RequestParam(defaultValue = "20") int topK
    ) {
        List<VerifiedQuery> vqs = router.topKByHits(connectionId, topK);
        return ResponseEntity.ok(Map.of("queries", vqs));
    }

    @PostMapping("/{connectionId}/from-template")
    public ResponseEntity<?> fromTemplate(@PathVariable String connectionId, @RequestBody Map<String, Object> body) {
        String templateName = (String) body.get("templateName");
        if (templateName == null || templateName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_TEMPLATE_NAME"));
        }
        try {
            String yamlContent = loadTemplate(templateName);
            if (yamlContent == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "TEMPLATE_NOT_FOUND"));
            }
            repository.savePending(connectionId, templateName, yamlContent);
            repository.acceptPending(connectionId, templateName);
            return ResponseEntity.ok(Map.of("domain", templateName, "status", "created_from_template"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String loadTemplate(String templateName) {
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("semantic-models/builtin/_templates/" + templateName + ".model.template.yaml")) {
            if (in == null) return null;
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
