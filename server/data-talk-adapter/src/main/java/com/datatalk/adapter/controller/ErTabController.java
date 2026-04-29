package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.ErGraphResponse;
import com.datatalk.adapter.dto.SeedInspectorRequest;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.domain.er.ErErrors;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/er")
public class ErTabController {

    private final ErRelationDiscoveryService discovery;

    public ErTabController(ErRelationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @PostMapping("/seed-inspector")
    public ResponseEntity<?> seedInspector(@Valid @RequestBody SeedInspectorRequest request) {
        try {
            return ResponseEntity.ok(ErGraphResponse.from(discovery.discover(
                request.connectionId(),
                request.tables(),
                request.neighborDepth()
            )));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("connection not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "connection_unavailable",
                    "message", "Target connection no longer exists.",
                    "aiHint", "The target connection no longer exists. Call datatalk_list_connections and ask the user to pick a valid one."
                ));
            }
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", e.getMessage() == null ? "Invalid ER inspector request." : e.getMessage()
            ));
        } catch (ErErrors.DialectUnsupportedException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "dialect_unsupported",
                "kind", e.kind(),
                "message", "ER does not support dialect: " + e.kind(),
                "aiHint", "ER does not support " + e.kind() + ". Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts."
            ));
        } catch (ErErrors.TablesNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "tables_not_found",
                "missing", e.missing(),
                "message", "Tables not found: " + String.join(", ", e.missing()),
                "aiHint", "Tables " + e.missing() + " were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names."
            ));
        } catch (ErErrors.ErPayloadOversizedException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "code", "er_payload_oversized",
                "seenTables", e.seenTables(),
                "limit", e.limit(),
                "message", "ER payload exceeds limit.",
                "aiHint", "Payload exceeds limit (" + e.seenTables() + " > " + e.limit() + "). Narrow down using read_schema with pattern/limit, or split into multiple ER tabs by domain."
            ));
        }
    }
}
