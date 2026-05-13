package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.IngestionConfirmedTokenStore;
import com.datatalk.application.ingestion.IngestionCredentialService;
import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.application.ingestion.IngestionStopService;
import com.datatalk.application.ingestion.MappingHash;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.PayloadFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final IngestionCredentialService credService;
    private final IngestionCredentialRepository credRepo;
    private final IngestionJobRepository jobRepo;
    private final FileArtifactRepository artifactRepo;
    private final IngestionConfirmedTokenStore tokenStore;
    private final IngestionExecutor executor;
    private final IngestionStopService stopService;
    private final ObjectMapper om;

    public IngestionController(IngestionCredentialService credService,
                               IngestionCredentialRepository credRepo,
                               IngestionJobRepository jobRepo,
                               FileArtifactRepository artifactRepo,
                               IngestionConfirmedTokenStore tokenStore,
                               IngestionExecutor executor,
                               IngestionStopService stopService,
                               ObjectMapper om) {
        this.credService = credService;
        this.credRepo = credRepo;
        this.jobRepo = jobRepo;
        this.artifactRepo = artifactRepo;
        this.tokenStore = tokenStore;
        this.executor = executor;
        this.stopService = stopService;
        this.om = om;
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

    @PutMapping("/credentials/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
                                                       @RequestBody CredentialCreateRequest req) {
        var existing = credRepo.findById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        AuthScheme scheme = AuthScheme.valueOf(req.authScheme().toUpperCase());
        credService.update(id, req.name(), scheme,
            req.configNonSecret() == null ? Map.of() : req.configNonSecret(),
            req.secret());
        return ResponseEntity.ok(Map.of("id", id));
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

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable String id) {
        return jobRepo.findById(id)
            .map(j -> ResponseEntity.ok(jobToMap(j)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public Map<String, Object> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String connectionId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<Map<String, Object>> items = jobRepo.list(connectionId, status, null, limit, offset)
            .stream().map(this::jobToMap).toList();
        int total = jobRepo.count(connectionId, status, null);
        return Map.of("items", items, "total", total);
    }

    @GetMapping("/jobs/{id}/payload-preview")
    public ResponseEntity<Map<String, Object>> payloadPreview(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit) {
        var job = jobRepo.findById(id);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        IngestionJob j = job.get();
        if (j.payloadArtifactId() == null) return ResponseEntity.notFound().build();

        var artifact = artifactRepo.findById(j.payloadArtifactId());
        if (artifact.isEmpty()) return ResponseEntity.notFound().build();

        try {
            Path payloadPath = Path.of(artifact.get().physicalPath());
            String content = Files.readString(payloadPath);
            List<Map<String, Object>> rows = parsePreview(content, j.payloadFormat(), limit);
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
            return ResponseEntity.ok(Map.of("columns", columns, "rows", rows, "totalRows", rows.size()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<Map<String, Object>> parsePreview(String content, PayloadFormat format, int limit) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (format) {
            case JSON -> {
                JsonNode root = om.readTree(content);
                if (root.isArray()) {
                    for (int i = 0; i < Math.min(limit, root.size()); i++) {
                        JsonNode item = root.get(i);
                        Map<String, Object> row = new LinkedHashMap<>();
                        item.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                        rows.add(row);
                    }
                } else if (root.isObject()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    root.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                    rows.add(row);
                }
            }
            case JSONL -> {
                for (String line : content.split("\n")) {
                    if (line.isBlank() || rows.size() >= limit) continue;
                    JsonNode node = om.readTree(line);
                    Map<String, Object> row = new LinkedHashMap<>();
                    node.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                    rows.add(row);
                }
            }
            case CSV -> {
                String[] lines = content.split("\n");
                if (lines.length == 0) break;
                String[] headers = parseCsvLine(lines[0]);
                for (int i = 1; i < lines.length && rows.size() < limit; i++) {
                    if (lines[i].isBlank()) continue;
                    String[] vals = parseCsvLine(lines[i]);
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 0; c < headers.length; c++) {
                        row.put(headers[c].trim(), c < vals.length ? vals[c].trim() : null);
                    }
                    rows.add(row);
                }
            }
            case HTML -> {
                var doc = org.jsoup.Jsoup.parse(content);
                var table = doc.selectFirst("table");
                if (table == null) break;
                var headerCells = table.select("thead tr th");
                if (headerCells.isEmpty()) {
                    var firstRow = table.select("tr").first();
                    if (firstRow != null) headerCells = firstRow.select("td, th");
                }
                List<String> headers = new ArrayList<>();
                for (var cell : headerCells) headers.add(cell.text().trim());
                if (headers.isEmpty()) break;

                var dataRows = table.select("tbody tr");
                if (dataRows.isEmpty()) {
                    dataRows = table.select("tr");
                    if (!dataRows.isEmpty()) dataRows = new org.jsoup.select.Elements(dataRows.subList(1, dataRows.size()));
                }
                for (int i = 0; i < Math.min(limit, dataRows.size()); i++) {
                    var cells = dataRows.get(i).select("td");
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        String val = c < cells.size() ? cells.get(c).text().trim() : null;
                        row.put(headers.get(c), val != null && val.isEmpty() ? null : val);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(ch);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private Object convertNode(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isNull()) return null;
        return node.asText();
    }

    private Map<String, Object> jobToMap(IngestionJob j) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", j.id());
        map.put("name", j.name());
        map.put("sourceUrl", j.sourceUrl());
        map.put("status", j.status());
        map.put("payloadFormat", j.payloadFormat() != null ? j.payloadFormat().name() : null);
        map.put("payloadArtifactId", j.payloadArtifactId());
        map.put("connectionId", j.connectionId());
        map.put("targetSchema", j.targetSchema());
        map.put("targetTable", j.targetTable());
        map.put("mapping", mappingToMap(j.mapping()));
        map.put("mappingHash", j.mappingHash());
        map.put("rowCount", j.rowCount());
        map.put("rowsInserted", j.rowsInserted());
        map.put("bytesFetched", j.bytesFetched());
        Map<String, Object> createdBy = new LinkedHashMap<>();
        createdBy.put("kind", j.createdByKind() != null ? j.createdByKind() : "ai");
        createdBy.put("sessionId", j.createdBySessionId());
        createdBy.put("label", j.createdByLabel());
        map.put("createdBy", createdBy);
        map.put("heartbeatAt", j.heartbeatAt());
        map.put("createdAt", j.createdAt());
        map.put("updatedAt", j.updatedAt());
        map.put("completedAt", j.completedAt());
        map.put("errorMessage", j.errorMessage());
        return map;
    }

    private Map<String, Object> mappingToMap(IngestionMapping mapping) {
        if (mapping == null) return null;
        List<Map<String, Object>> cols = mapping.columns().stream().map(c -> {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("sourcePath", c.sourcePath());
            col.put("targetName", c.targetName());
            col.put("type", c.type() != null ? c.type().name() : null);
            col.put("skip", c.skip());
            col.put("sampleValues", c.sampleValues());
            col.put("nullable", c.nullable());
            return col;
        }).toList();
        return Map.of("mappingId", mapping.mappingId(), "columns", cols);
    }

    @PostMapping("/jobs/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable String id) {
        var job = jobRepo.findById(id);
        if (job.isEmpty()) return ResponseEntity.notFound().build();

        IngestionJob j = job.get();
        // BUG-0030: confirm requires (a) a populated mapping (infer must have run)
        // and (b) the job is not in a terminal state (cancelled/failed/completed/writing).
        // The status field never actually transitions to 'awaiting_confirm' in current
        // main code — infer just persists the mapping and leaves status at 'fetched',
        // so we gate on mapping presence + terminal-state exclusion.
        if (j.mapping() == null) {
            return ResponseEntity.status(409).body(Map.of(
                "error", Map.of(
                    "code", "INGESTION_JOB_NOT_CONFIRMABLE",
                    "reason", "job has no mapping; run infer_ingestion_schema first"
                ),
                "userHint", "Run infer_ingestion_schema first to build the mapping."
            ));
        }
        String s = j.status();
        if ("cancelled".equals(s) || "failed".equals(s) || "completed".equals(s) || "writing".equals(s)) {
            return ResponseEntity.status(409).body(Map.of(
                "error", Map.of(
                    "code", "INGESTION_JOB_NOT_CONFIRMABLE",
                    "reason", "job is in terminal state: " + s
                ),
                "userHint", "Job already terminated; start a new ingestion."
            ));
        }

        String hash = j.mappingHash() != null ? j.mappingHash() : MappingHash.compute(j.mapping());
        var token = tokenStore.issue(id, hash);

        executor.createTable(id, j.connectionId(), j.targetSchema(), j.targetTable(),
                             hash, token.tokenId(), null);
        var result = executor.ingestPayload(id, 1000, null);

        return ResponseEntity.ok(Map.of(
            "status", result.status(),
            "rowsInserted", result.rowsInserted()
        ));
    }

    /**
     * @deprecated since the {@code stop} endpoint provides true cancellation
     * (interrupts the worker, drops the target table if it has been created,
     * cleans the payload artifact). This legacy route is kept for backward
     * compatibility and internally forwards to {@code /stop}.
     */
    @Deprecated
    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        return stop(id, false);
    }

    @PostMapping("/jobs/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable String id,
                                      @RequestParam(defaultValue = "false") boolean force) {
        IngestionStopService.StopOutcome outcome = stopService.stop(id, force);
        return switch (outcome) {
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case ALREADY_TERMINAL -> ResponseEntity.status(409).build();
            case SIGNALLED -> ResponseEntity.accepted().build();
            case APPLIED -> ResponseEntity.noContent().build();
        };
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        var job = jobRepo.findById(id);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        jobRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record BatchDeleteRequest(List<String> ids) {}

    @DeleteMapping("/jobs")
    public ResponseEntity<Map<String, Object>> batchDeleteJobs(@RequestBody BatchDeleteRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids must not be empty"));
        }
        if (req.ids().size() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "batch size must not exceed 100"));
        }
        int deleted = jobRepo.deleteByIds(req.ids());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
