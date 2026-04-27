package com.datatalk.adapter.controller;

import com.datatalk.application.stage.StageTabPayloadTooLargeException;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.application.stage.StageTabService;
import com.datatalk.application.stage.StageTabConcurrencyException;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public StageTabController(StageTabService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
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
        return ResponseEntity.ok(Map.of("items", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> upsert(
        @PathVariable String id,
        @RequestBody UpsertBody body,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        Integer expectedVersion = ifMatch != null ? Integer.parseInt(ifMatch) : null;
        StageTabScope scope = StageTabScope.fromWire(body.scope());
        long now = System.currentTimeMillis();

        StageTab tab = new StageTab(
            id, body.type(), scope, body.title(),
            body.connectionId(), body.effectiveDatabase(), body.effectiveSchema(),
            body.originSessionId(), 1,
            body.pinned() != null ? body.pinned() : false,
            body.archived() != null ? body.archived() : false, null,
            body.createdAt() != null ? body.createdAt() : now,
            body.lastTouchedAt() != null ? body.lastTouchedAt() : now
        );

        boolean hasPayload = body.hasPayload();
        boolean existing = service.find(id).isPresent();
        int newVersion;
        if (hasPayload && existing) {
            newVersion = service.savePayload(id, body.payloadJson(objectMapper), body.contentTextOrEmpty(), expectedVersion);
        } else {
            newVersion = service.upsert(tab, expectedVersion);

            if (hasPayload) {
                newVersion = service.savePayload(id, body.payloadJson(objectMapper), body.contentTextOrEmpty(), null);
            }
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
                body.put("payload", parsePayload(content.payloadJson()));
                body.put("payloadJson", content.payloadJson());
                body.put("contentText", content.contentText());
                body.put("contentVersion", content.contentVersion());
                service.find(id).ifPresent(tab -> body.put("payloadVersion", tab.payloadVersion()));
                body.put("updatedAt", content.updatedAt());
                return ResponseEntity.ok(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/payload-beacon")
    public ResponseEntity<Void> savePayloadBeacon(@PathVariable String id, @RequestBody BeaconPayload body) {
        service.savePayload(id,
            body.payloadJson(objectMapper),
            body.contentText() != null ? body.contentText() : "",
            body.expectedVersion());
        return ResponseEntity.accepted().build();
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
        m.put("tabId", tab.id());
        m.put("objectId", tab.id());
        m.put("type", tab.type());
        m.put("scope", tab.scope().wire());
        m.put("title", tab.title());
        if (tab.connectionId() != null) m.put("connectionId", tab.connectionId());
        if (tab.databaseName() != null) {
            m.put("database", tab.databaseName());
            m.put("databaseName", tab.databaseName());
        }
        if (tab.schemaName() != null) {
            m.put("schema", tab.schemaName());
            m.put("schemaName", tab.schemaName());
        }
        if (tab.originSessionId() != null) m.put("originSessionId", tab.originSessionId());
        m.put("payloadVersion", tab.payloadVersion());
        m.put("pinned", tab.pinned());
        m.put("archived", tab.archived());
        if (tab.archivedAt() != null) m.put("archivedAt", tab.archivedAt());
        m.put("createdAt", tab.createdAt());
        m.put("lastTouchedAt", tab.lastTouchedAt());
        return m;
    }

    private Object parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Object.class);
        } catch (JsonProcessingException e) {
            return payloadJson;
        }
    }

    public record UpsertBody(
        String type,
        String scope,
        String title,
        String connectionId,
        String database,
        String databaseName,
        String schema,
        String schemaName,
        String originSessionId,
        Boolean pinned,
        Boolean archived,
        Long createdAt,
        Long lastTouchedAt,
        Object payload,
        String payloadJson,
        String contentText
    ) {
        String effectiveDatabase() {
            return database != null ? database : databaseName;
        }

        String effectiveSchema() {
            return schema != null ? schema : schemaName;
        }

        boolean hasPayload() {
            return payload != null || payloadJson != null || contentText != null;
        }

        String contentTextOrEmpty() {
            return contentText != null ? contentText : "";
        }

        String payloadJson(ObjectMapper objectMapper) {
            if (payloadJson != null) {
                return payloadJson;
            }
            if (payload == null) {
                return "{}";
            }
            try {
                return objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("payload is not JSON serializable", e);
            }
        }
    }

    public record ArchiveBody(boolean archived) {}

    public record BeaconPayload(
        Object payload,
        String payloadJson,
        String contentText,
        Integer expectedVersion
    ) {
        String payloadJson(ObjectMapper objectMapper) {
            if (payloadJson != null) return payloadJson;
            if (payload == null) return "{}";
            try {
                return objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("payload is not JSON serializable", e);
            }
        }
    }
}
