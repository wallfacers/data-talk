package com.datatalk.application.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.ddl.IngestionDdlAdapter;
import com.datatalk.application.ingestion.parser.*;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.*;

@Service
public class IngestionExecutor {

    private final IngestionJobRepository jobRepo;
    private final IngestionConfirmedTokenStore tokenStore;
    private final List<IngestionDdlAdapter> adapters;
    private final ConnectionRepository connRepo;
    private final ConnectionService connService;
    private final FileArtifactRepository artifactRepo;
    private final Map<PayloadFormat, PayloadParser> parsers;
    private final ObjectMapper om;

    public IngestionExecutor(IngestionJobRepository jobRepo,
                             IngestionConfirmedTokenStore tokenStore,
                             List<IngestionDdlAdapter> adapters,
                             ConnectionRepository connRepo,
                             ConnectionService connService,
                             FileArtifactRepository artifactRepo,
                             JsonPayloadParser j, JsonlPayloadParser jl,
                             CsvPayloadParser c, HtmlTablePayloadParser h,
                             ObjectMapper om) {
        this.jobRepo = jobRepo;
        this.tokenStore = tokenStore;
        this.adapters = adapters;
        this.connRepo = connRepo;
        this.connService = connService;
        this.artifactRepo = artifactRepo;
        this.parsers = Map.of(
            PayloadFormat.JSON, j, PayloadFormat.JSONL, jl,
            PayloadFormat.CSV, c, PayloadFormat.HTML, h);
        this.om = om;
    }

    public record CreateTableResult(String jobId, String targetTable, String ddl) {}

    public CreateTableResult createTable(String jobId, String connectionId,
                                          String schema, String table,
                                          String mappingHash, String tokenId) {
        var job = jobRepo.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));
        tokenStore.consume(tokenId, jobId, mappingHash);

        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("connection not found: " + connectionId));
        var adapter = resolveAdapter(cr.kind());

        var mapping = job.mapping();
        if (mapping == null) throw new IllegalStateException("job has no mapping: " + jobId);

        String ddl = adapter.generateCreateTable(schema, table, mapping.columns());
        executeDdl(cr, ddl);

        jobRepo.updateTargetTable(jobId, connectionId, schema, table, System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "writing", null, System.currentTimeMillis());

        return new CreateTableResult(jobId, schema != null ? schema + "." + table : table, ddl);
    }

    public IngestResult ingestPayload(String jobId, int batchSize) {
        var job = jobRepo.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));

        ConnectionRecord cr = connRepo.findById(job.connectionId())
            .orElseThrow(() -> new IllegalArgumentException("connection not found: " + job.connectionId()));
        var adapter = resolveAdapter(cr.kind());

        var mapping = job.mapping();
        if (mapping == null || job.payloadArtifactId() == null)
            throw new IllegalStateException("job not ready for ingestion: " + jobId);

        String insertSql = adapter.generateInsert(job.targetSchema(), job.targetTable(), mapping.columns());
        FileArtifact artifact = artifactRepo.findById(job.payloadArtifactId())
            .orElseThrow(() -> new IllegalArgumentException("artifact not found"));

        Path payloadPath = Path.of(artifact.physicalPath());
        int rowsInserted = 0;
        long start = System.currentTimeMillis();

        try {
            rowsInserted = executeBatchInsert(cr, insertSql, payloadPath, job.payloadFormat(), mapping.columns(), batchSize);
        } catch (Exception e) {
            jobRepo.updateStatus(jobId, "failed", e.getMessage(), System.currentTimeMillis());
            throw new RuntimeException("ingestion failed: " + e.getMessage(), e);
        }

        long durationMs = System.currentTimeMillis() - start;
        jobRepo.updateCompleted(jobId, rowsInserted, System.currentTimeMillis(), System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "completed", null, System.currentTimeMillis());

        return new IngestResult(jobId, "completed", rowsInserted, durationMs);
    }

    public record IngestResult(String jobId, String status, int rowsInserted, long durationMs) {}

    private IngestionDdlAdapter resolveAdapter(String kind) {
        return adapters.stream()
            .filter(a -> a.supports(kind))
            .findFirst()
            .orElseThrow(() -> new IngestionDialectUnsupportedException(kind));
    }

    private void executeDdl(ConnectionRecord cr, String ddl) {
        String url = JdbcUrlBuilder.build(cr);
        String password = connService.decryptPassword(cr.id());
        try (Connection conn = DriverManager.getConnection(url, cr.username(), password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("DDL execution failed: " + e.getMessage(), e);
        }
    }

    private int executeBatchInsert(ConnectionRecord cr, String insertSql, Path payloadPath,
                                    PayloadFormat format, List<MappingColumn> columns, int batchSize) throws Exception {
        List<MappingColumn> active = columns.stream().filter(c -> !c.skip()).toList();
        if (active.isEmpty()) return 0;

        String url = JdbcUrlBuilder.build(cr);
        String password = connService.decryptPassword(cr.id());

        String content = java.nio.file.Files.readString(payloadPath);
        List<Map<String, Object>> rows = parseRows(content, format);

        try (Connection conn = DriverManager.getConnection(url, cr.username(), password);
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            conn.setAutoCommit(false);
            int count = 0;
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                for (int c = 0; c < active.size(); c++) {
                    MappingColumn col = active.get(c);
                    Object val = row.get(col.targetName());
                    if (val == null) val = row.get(col.sourcePath());
                    ps.setObject(c + 1, val);
                }
                ps.addBatch();
                count++;
                if (count % batchSize == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
            return count;
        }
    }

    private List<Map<String, Object>> parseRows(String content, PayloadFormat format) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (format) {
            case JSON -> {
                JsonNode root = om.readTree(content);
                if (root.isArray()) {
                    for (JsonNode item : root) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        item.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                        rows.add(row);
                    }
                }
            }
            case JSONL -> {
                for (String line : content.split("\n")) {
                    if (line.isBlank()) continue;
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
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].isBlank()) continue;
                    String[] vals = parseCsvLine(lines[i]);
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 0; c < headers.length; c++) {
                        row.put(headers[c].trim(), c < vals.length ? vals[c].trim() : null);
                    }
                    rows.add(row);
                }
            }
            default -> throw new UnsupportedOperationException("unsupported format for ingestion: " + format);
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
                if (ch == '"') inQuotes = true;
                else if (ch == ',') { fields.add(current.toString()); current = new StringBuilder(); }
                else current.append(ch);
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
}
