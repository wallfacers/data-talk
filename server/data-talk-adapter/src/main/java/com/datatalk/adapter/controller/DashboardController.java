package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.DashboardPatchRequest;
import com.datatalk.adapter.dto.DashboardPromoteRequest;
import com.datatalk.application.dashboard.DashboardArtifactService;
import com.datatalk.application.dashboard.JsonPatchApplier;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardArtifactService dashboardService;

    public DashboardController(DashboardArtifactService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promote(@RequestBody DashboardPromoteRequest request) {
        if (request.dashboard() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "dashboard payload is required"
            ));
        }
        try {
            DashboardArtifactService.PromoteResult result = dashboardService.promote(request.dashboard());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.id(),
                "version", result.version()
            ));
        } catch (DashboardArtifactService.PayloadTooLargeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "code", "payload_too_large",
                "message", e.getMessage(),
                "maxSize", e.getMaxSize()
            ));
        } catch (DashboardArtifactService.ValidationException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                "code", "validation_error",
                "message", "Dashboard validation failed",
                "errors", e.getResult().errors()
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            JsonNode dashboard = dashboardService.load(id);
            return ResponseEntity.ok(dashboard);
        } catch (DashboardArtifactService.DashboardNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "not_found",
                "message", e.getMessage()
            ));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(
        @PathVariable String id,
        @RequestBody DashboardPatchRequest request
    ) {
        if (request.ops() == null || request.ops().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "ops is required and must not be empty"
            ));
        }
        List<JsonPatchApplier.PatchOp> ops = request.ops().stream()
            .map(op -> new JsonPatchApplier.PatchOp(op.op(), op.path(), op.value()))
            .toList();

        try {
            DashboardArtifactService.PatchResult result = dashboardService.patch(id, request.baseVersion(), ops);
            return ResponseEntity.ok(Map.of(
                "version", result.version()
            ));
        } catch (DashboardArtifactService.DashboardNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "not_found",
                "message", e.getMessage()
            ));
        } catch (JsonPatchApplier.VersionConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "version_conflict",
                "message", e.getMessage(),
                "expected", e.getExpected(),
                "actual", e.getActual()
            ));
        } catch (JsonPatchApplier.PatchRejectException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                "code", "patch_rejected",
                "message", e.getMessage()
            ));
        } catch (DashboardArtifactService.ValidationException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                "code", "validation_error",
                "message", "Patched dashboard validation failed",
                "errors", e.getResult().errors()
            ));
        }
    }

    @GetMapping(value = "/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> serveHtml(@PathVariable String id, HttpServletRequest req) {
        var maybe = dashboardService.loadHtml(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();
        String origin = "http://" + req.getServerName() + ":" + req.getServerPort();
        String body = new String(maybe.get(), StandardCharsets.UTF_8)
            .replace("__BEZEL_SERVER_ORIGIN__", origin);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(body);
    }
}
