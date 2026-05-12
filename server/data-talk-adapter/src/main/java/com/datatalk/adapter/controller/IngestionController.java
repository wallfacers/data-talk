package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.IngestionCredentialService;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import com.datatalk.domain.ingestion.IngestionJob;
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
    private final ObjectMapper om;

    public IngestionController(IngestionCredentialService credService,
                               IngestionCredentialRepository credRepo,
                               IngestionJobRepository jobRepo,
                               FileArtifactRepository artifactRepo,
                               ObjectMapper om) {
        this.credService = credService;
        this.credRepo = credRepo;
        this.jobRepo = jobRepo;
        this.artifactRepo = artifactRepo;
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
        map.put("sourceUrl", j.sourceUrl());
        map.put("status", j.status());
        map.put("payloadFormat", j.payloadFormat() != null ? j.payloadFormat().name() : null);
        map.put("payloadArtifactId", j.payloadArtifactId());
        map.put("connectionId", j.connectionId());
        map.put("targetSchema", j.targetSchema());
        map.put("targetTable", j.targetTable());
        map.put("rowCount", j.rowCount());
        map.put("rowsInserted", j.rowsInserted());
        map.put("bytesFetched", j.bytesFetched());
        map.put("createdAt", j.createdAt());
        map.put("updatedAt", j.updatedAt());
        map.put("completedAt", j.completedAt());
        map.put("errorMessage", j.errorMessage());
        return map;
    }
}
