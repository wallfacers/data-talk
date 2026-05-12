package com.datatalk.application.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.ddl.IngestionDdlAdapter;
import com.datatalk.application.ingestion.parser.*;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.event.DtEvent;
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
    private final IngestionEventPublisher eventPublisher;
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
                             IngestionEventPublisher eventPublisher,
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
        this.eventPublisher = eventPublisher;
        this.om = om;
    }

    public record CreateTableResult(String jobId, String targetTable, String ddl) {}

    public CreateTableResult createTable(String jobId, String connectionId,
                                          String schema, String table,
                                          String mappingHash, String tokenId,
                                          String sessionId) {
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

        String targetTable = schema != null ? schema + "." + table : table;
        jobRepo.updateTargetTable(jobId, connectionId, schema, table, System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "writing", null, System.currentTimeMillis());

        eventPublisher.publish(sessionId, new DtEvent.IngestionJobConfirmed(jobId, tokenId));
        eventPublisher.publish(sessionId, new DtEvent.IngestionWriteStarted(jobId, targetTable));

        return new CreateTableResult(jobId, targetTable, ddl);
    }

    public IngestResult ingestPayload(String jobId, int batchSize, String sessionId) {
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
            PayloadParser parser = parsers.get(job.payloadFormat());
            if (parser == null) {
                throw new UnsupportedOperationException(
                    "unsupported payload format: " + job.payloadFormat());
            }
            rowsInserted = executeBatchInsert(cr, insertSql, payloadPath, parser, mapping.columns(), batchSize, jobId, sessionId);
        } catch (Exception e) {
            jobRepo.updateStatus(jobId, "failed", e.getMessage(), System.currentTimeMillis());
            eventPublisher.publish(sessionId, new DtEvent.IngestionFailed(jobId, "ingest", e.getMessage()));
            throw new RuntimeException("ingestion failed: " + e.getMessage(), e);
        }

        long durationMs = System.currentTimeMillis() - start;
        jobRepo.updateCompleted(jobId, rowsInserted, System.currentTimeMillis(), System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "completed", null, System.currentTimeMillis());

        String targetTable = job.targetSchema() != null
            ? job.targetSchema() + "." + job.targetTable()
            : job.targetTable();
        eventPublisher.publish(sessionId, new DtEvent.IngestionCompleted(
            jobId, targetTable, rowsInserted, durationMs));

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
                                    PayloadParser parser, List<MappingColumn> columns,
                                    int batchSize, String jobId, String sessionId) throws Exception {
        List<MappingColumn> active = columns.stream().filter(c -> !c.skip()).toList();
        if (active.isEmpty()) return 0;

        String url = JdbcUrlBuilder.build(cr);
        String password = connService.decryptPassword(cr.id());

        try (RowStream rs = parser.openRowStream(payloadPath);
             Connection conn = DriverManager.getConnection(url, cr.username(), password);
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            conn.setAutoCommit(false);
            int count = 0;
            while (rs.hasNext()) {
                Map<String, Object> row = rs.next();
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
                    eventPublisher.publish(sessionId, new DtEvent.IngestionWriteProgress(jobId, count, -1));
                }
            }
            ps.executeBatch();
            conn.commit();
            return count;
        }
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
