package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.ErGraphResponse;
import com.datatalk.adapter.dto.DiffResponse;
import com.datatalk.adapter.dto.GenerateDdlRequest;
import com.datatalk.adapter.dto.GenerateDdlResponse;
import com.datatalk.adapter.dto.SeedInspectorRequest;
import com.datatalk.adapter.dto.SyncFromDbResponse;
import com.datatalk.application.er.ErDdlGeneratorService;
import com.datatalk.application.er.ErDesignerSyncService;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.domain.er.ErErrors;
import com.datatalk.domain.er.ErDesignerPayload;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/er")
public class ErTabController {

    private final ErRelationDiscoveryService discovery;
    private final ErDdlGeneratorService ddlService;
    private final ErDesignerSyncService syncService;

    public ErTabController(
        ErRelationDiscoveryService discovery,
        ErDdlGeneratorService ddlService,
        ErDesignerSyncService syncService
    ) {
        this.discovery = discovery;
        this.ddlService = ddlService;
        this.syncService = syncService;
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

    @PostMapping("/generate-ddl")
    public ResponseEntity<?> generateDdl(@RequestBody GenerateDdlRequest request) {
        if (request.connectionId() == null || request.connectionId().isBlank()) {
            return targetRequired();
        }
        try {
            var result = ddlService.generate(request.payload(), request.connectionId(), request.includeDrops());
            return ResponseEntity.ok(GenerateDdlResponse.from(result));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return badApplyRequest(e);
        } catch (ErErrors.DialectUnsupportedException e) {
            return dialectUnsupported(e);
        }
    }

    @PostMapping("/diff")
    public ResponseEntity<?> diff(@RequestBody DiffRequest request) {
        if (request.connectionId() == null || request.connectionId().isBlank()) {
            return targetRequired();
        }
        try {
            return ResponseEntity.ok(new DiffResponse(ddlService.diff(request.payload(), request.connectionId())));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return badApplyRequest(e);
        } catch (ErErrors.DialectUnsupportedException e) {
            return dialectUnsupported(e);
        }
    }

    @PostMapping("/sync-from-db")
    public ResponseEntity<?> syncFromDb(@RequestBody SyncFromDbRequest request) {
        if (request.connectionId() == null || request.connectionId().isBlank()) {
            return targetRequired();
        }
        if (request.payload() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "sync_from_db requires the current designer payload.",
                "aiHint", "Pass the designer payload returned by ui_read(mode='state') so the server can merge real-DB tables back into the draft."
            ));
        }
        try {
            ErDesignerPayload merged = syncService.sync(
                request.payload(),
                request.connectionId(),
                request.tables() == null ? List.of() : request.tables()
            );
            return ResponseEntity.ok(new SyncFromDbResponse(merged));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "sync_failed",
                "message", e.getMessage() == null ? "ER sync failed." : e.getMessage(),
                "aiHint", "Bind the designer to a target connection and pass either an explicit `tables` list or have at least one draft table before calling sync_from_db."
            ));
        } catch (ErErrors.DialectUnsupportedException e) {
            return dialectUnsupported(e);
        } catch (ErErrors.TablesNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "tables_not_found",
                "missing", e.missing(),
                "message", "Tables not found: " + String.join(", ", e.missing()),
                "aiHint", "Tables " + e.missing() + " were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names before sync_from_db."
            ));
        }
    }

    private static ResponseEntity<Map<String, String>> targetRequired() {
        return ResponseEntity.badRequest().body(Map.of(
            "code", "target_required_for_apply",
            "message", "ER Designer requires a target connection.",
            "aiHint", "generate_ddl requires bind_target first. Call ui_exec(designer, bind_target, {connectionId, database, schema}) and retry."
        ));
    }

    private static ResponseEntity<Map<String, String>> badApplyRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "code", "target_required_for_apply",
            "message", e.getMessage() == null ? "ER Designer target is required." : e.getMessage(),
            "aiHint", "Bind a target connection first via ui_exec(designer, bind_target)."
        ));
    }

    private static ResponseEntity<Map<String, String>> dialectUnsupported(ErErrors.DialectUnsupportedException e) {
        String kind = e.kind() == null || e.kind().isBlank() ? "unknown" : e.kind();
        return ResponseEntity.badRequest().body(Map.of(
            "code", "dialect_unsupported",
            "kind", kind,
            "message", "ER does not support dialect: " + kind,
            "aiHint", "ER does not support " + kind + ". Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts."
        ));
    }

    public record DiffRequest(ErDesignerPayload payload, String connectionId) {}

    public record SyncFromDbRequest(ErDesignerPayload payload, String connectionId, List<String> tables) {}
}
