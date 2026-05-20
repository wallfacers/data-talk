package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.DashboardPreviewRequest;
import com.datatalk.adapter.dto.DashboardPromoteRequest;
import com.datatalk.adapter.dto.DashboardUpdateRequest;
import com.datatalk.application.dashboard.DashboardArtifactService;
import com.datatalk.application.dashboard.DashboardCompiler;
import com.datatalk.application.dashboard.DashboardDiffer;
import com.datatalk.application.dashboard.WidgetDataService;
import com.datatalk.application.dashboard.PatternCatalog;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private static final Pattern WIDGET_URL_DASHBOARD_ID =
        Pattern.compile("(/api/dashboards/)[A-Za-z0-9_]+(/widgets/)");
    private static final Pattern CONFIG_DASHBOARD_ID =
        Pattern.compile("(\"dashboardId\"\\s*:\\s*\")[^\"]+(\")");

    private static final String ECHARTS_CDN_URL =
        "https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js";
    private static final String ECHARTS_LOCAL_PATH = "/bezel/echarts.min.js";

    public static final String SESSION_ID_HEADER = "X-DataTalk-Session-Id";

    private final DashboardArtifactService dashboardService;
    private final DashboardCompiler compiler;
    private final PatternCatalog catalog;
    private final WidgetDataService widgetDataService;
    private final SessionDataContextService sessionDataContextService;

    public DashboardController(DashboardArtifactService dashboardService,
                               DashboardCompiler compiler,
                               PatternCatalog catalog,
                               WidgetDataService widgetDataService,
                               SessionDataContextService sessionDataContextService) {
        this.dashboardService = dashboardService;
        this.compiler = compiler;
        this.catalog = catalog;
        this.widgetDataService = widgetDataService;
        this.sessionDataContextService = sessionDataContextService;
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promote(
        @RequestHeader(value = SESSION_ID_HEADER, required = false) String sessionId,
        @RequestBody DashboardPromoteRequest request
    ) {
        if (request.dashboard() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "dashboard payload is required"
            ));
        }
        JsonNode dashboard = enrichFromSessionContext(request.dashboard(), sessionId);
        try {
            DashboardArtifactService.PromoteResult result = dashboardService.promote(dashboard, sessionId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.id(),
                "version", result.version(),
                "html", result.html()
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

    @PostMapping("/{id}/update")
    public ResponseEntity<?> update(
        @PathVariable String id,
        @RequestBody DashboardUpdateRequest request
    ) {
        if (request.dashboard() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "dashboard payload is required"
            ));
        }
        try {
            JsonNode current = dashboardService.load(id);
            int currentVersion = current.path("version").asInt(0);
            if (request.baseVersion() != currentVersion) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "version_conflict",
                    "message", "Dashboard has been modified by another request",
                    "expected", request.baseVersion(),
                    "actual", currentVersion
                ));
            }

            JsonNode newJson = request.dashboard();
            boolean fullRebuild = DashboardDiffer.needsFullRebuild(current, newJson);

            // Compute incremental changes (against the pre-update state) before persisting.
            var changes = fullRebuild ? null
                : DashboardDiffer.computeIncrementalChanges(current, newJson, id, catalog);

            // Persist new JSON + bumped version + recompiled HTML (both paths) so the stored
            // dashboard and GET /html stay current; incremental responses still drive hot updates.
            DashboardArtifactService.UpdateResult result;
            try {
                result = dashboardService.update(id, newJson);
            } catch (DashboardArtifactService.ValidationException e) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", "compile_error",
                    "message", "Dashboard compilation failed",
                    "errors", e.getResult().errors()
                ));
            }

            if (fullRebuild) {
                return ResponseEntity.ok(Map.of(
                    "version", result.version(),
                    "html", result.html()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "version", result.version(),
                    "changes", changes
                ));
            }
        } catch (DashboardArtifactService.DashboardNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "not_found",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody DashboardPreviewRequest request) {
        if (request.dashboard() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", "dashboard payload is required"
            ));
        }
        var result = compiler.compile(request.dashboard());
        if (!result.ok()) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                "code", "compile_error",
                "message", "Dashboard compilation failed",
                "errors", result.errors()
            ));
        }
        return ResponseEntity.ok(Map.of("html", result.html()));
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

    @GetMapping(value = "/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> serveHtml(@PathVariable String id, HttpServletRequest req) {
        var maybe = dashboardService.loadHtml(id);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();
        String origin = "http://" + req.getServerName() + ":" + req.getServerPort();
        String body = new String(maybe.get(), StandardCharsets.UTF_8)
            .replace("__BEZEL_SERVER_ORIGIN__", origin)
            .replace("\"/api/dashboards/", "\"" + origin + "/api/dashboards/")
            .replace("'/api/dashboards/", "'" + origin + "/api/dashboards/")
            .replace(ECHARTS_CDN_URL, origin + ECHARTS_LOCAL_PATH);
        String replacement = Matcher.quoteReplacement(id);
        body = WIDGET_URL_DASHBOARD_ID.matcher(body).replaceAll("$1" + replacement + "$2");
        body = CONFIG_DASHBOARD_ID.matcher(body).replaceAll("$1" + replacement + "$2");
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .body(body);
    }

    private JsonNode enrichFromSessionContext(JsonNode dashboard, String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !(dashboard instanceof ObjectNode obj)) {
            return dashboard;
        }
        SessionDataContextRecord ctx;
        try {
            ctx = sessionDataContextService.get(sessionId);
        } catch (NoSuchElementException e) {
            return dashboard;
        }
        if (isBlankField(obj, "defaultConnectionId") && nonBlank(ctx.connectionId())) {
            obj.put("defaultConnectionId", ctx.connectionId());
        }
        if (isBlankField(obj, "defaultDatabase") && nonBlank(ctx.databaseName())) {
            obj.put("defaultDatabase", ctx.databaseName());
        }
        if (isBlankField(obj, "defaultSchema") && nonBlank(ctx.schemaName())) {
            obj.put("defaultSchema", ctx.schemaName());
        }
        return obj;
    }

    private static boolean isBlankField(ObjectNode obj, String field) {
        JsonNode node = obj.get(field);
        if (node == null || node.isNull()) return true;
        if (node.isTextual()) return node.asText().isBlank();
        return false;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    @CrossOrigin(origins = "null", allowCredentials = "false")
    @PostMapping("/{id}/widgets/{wid}/data")
    public ResponseEntity<?> fetchWidgetData(
        @PathVariable String id, @PathVariable String wid,
        @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
            WidgetDataService.WidgetData data = widgetDataService.fetchWidgetData(id, wid, params);
            return ResponseEntity.ok(Map.of(
                "columns", data.columns(),
                "rows", data.rows(),
                "executedAt", data.executedAt()
            ));
        } catch (DashboardArtifactService.DashboardNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "not_found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", "bad_request", "message", e.getMessage()));
        }
    }
}
