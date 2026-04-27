package com.datatalk.adapter.controller;

import com.datatalk.application.stage.StageTabPayloadTooLargeException;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.application.stage.StageTabService;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
import com.datatalk.infra.stage.StageTabConcurrencyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stage/tabs")
public class StageTabController {

    private final StageTabService service;

    public StageTabController(StageTabService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String connectionId,
        @RequestParam(required = false) String originSessionId,
        @RequestParam(required = false, defaultValue = "false") boolean archived,
        @RequestParam(required = false, defaultValue = "100") int limit
    ) {
        StageTabScope scopeEnum = scope != null ? StageTabScope.fromWire(scope) : null;
        StageTabRepository.ListFilter filter = new StageTabRepository.ListFilter(
            scopeEnum, type, connectionId, originSessionId, archived, null, null, null, limit);
        List<StageTab> tabs = service.list(filter);
        List<Map<String, Object>> result = tabs.stream().map(this::toJson).toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> upsert(
        @PathVariable String id,
        @RequestBody UpsertBody body,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        Integer expectedVersion = ifMatch != null ? Integer.parseInt(ifMatch) : null;
        StageTabScope scope = StageTabScope.fromWire(body.scope());

        StageTab tab = new StageTab(
            id, body.type(), scope, body.title(),
            body.connectionId(), body.databaseName(), body.schemaName(),
            body.originSessionId(), 1,
            body.pinned() != null ? body.pinned() : false,
            false, null,
            System.currentTimeMillis(), System.currentTimeMillis()
        );

        int newVersion = service.upsert(tab, expectedVersion);

        // Also save payload if provided
        if (body.payloadJson() != null || body.contentText() != null) {
            service.savePayload(id,
                body.payloadJson() != null ? body.payloadJson() : "{}",
                body.contentText() != null ? body.contentText() : "",
                0);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("payloadVersion", newVersion);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/payload")
    public ResponseEntity<Map<String, Object>> getPayload(@PathVariable String id) {
        return service.findContent(id)
            .map(content -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("tabId", content.tabId());
                body.put("payloadJson", content.payloadJson());
                body.put("contentText", content.contentText());
                body.put("contentVersion", content.contentVersion());
                body.put("updatedAt", content.updatedAt());
                return ResponseEntity.ok(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<Void> setArchived(@PathVariable String id, @RequestBody ArchiveBody body) {
        service.setArchived(id, body.archived());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        boolean deleted = service.delete(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(StageTabConcurrencyException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrency(StageTabConcurrencyException e) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(Map.of(
            "error", "CONCURRENCY_CONFLICT",
            "message", e.getMessage(),
            "tabId", e.tabId(),
            "expectedVersion", e.expectedVersion(),
            "actualVersion", e.actualVersion()
        ));
    }

    @ExceptionHandler(StageTabPayloadTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handlePayloadTooLarge(StageTabPayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
            "error", "PAYLOAD_TOO_LARGE",
            "message", e.getMessage(),
            "actualBytes", e.actualBytes(),
            "maxBytes", e.maxBytes()
        ));
    }

    private Map<String, Object> toJson(StageTab tab) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tab.id());
        m.put("type", tab.type());
        m.put("scope", tab.scope().wire());
        m.put("title", tab.title());
        if (tab.connectionId() != null) m.put("connectionId", tab.connectionId());
        if (tab.databaseName() != null) m.put("databaseName", tab.databaseName());
        if (tab.schemaName() != null) m.put("schemaName", tab.schemaName());
        if (tab.originSessionId() != null) m.put("originSessionId", tab.originSessionId());
        m.put("payloadVersion", tab.payloadVersion());
        m.put("pinned", tab.pinned());
        m.put("archived", tab.archived());
        if (tab.archivedAt() != null) m.put("archivedAt", tab.archivedAt());
        m.put("createdAt", tab.createdAt());
        m.put("lastTouchedAt", tab.lastTouchedAt());
        return m;
    }

    public record UpsertBody(
        String type,
        String scope,
        String title,
        String connectionId,
        String databaseName,
        String schemaName,
        String originSessionId,
        Boolean pinned,
        String payloadJson,
        String contentText
    ) {}

    public record ArchiveBody(boolean archived) {}
}
