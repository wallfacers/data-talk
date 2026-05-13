package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.PaginationSpec;
import com.datatalk.domain.ingestion.PayloadFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcIngestionJobRepository implements IngestionJobRepository {

    private static final String COLS = """
        id, source_url, source_method, source_headers_json, source_query_params_json,
        source_body_json, credential_id, pagination_json, payload_format,
        payload_artifact_id, status, connection_id, target_schema, target_table,
        mapping_json, row_count, rows_inserted, bytes_fetched, mapping_hash,
        created_at, updated_at, completed_at, error_message
        """;

    private static final TypeReference<HashMap<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcIngestionJobRepository(
            @Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(IngestionJob j) {
        String headersJson = writeJson(j.sourceHeaders());
        String queryParamsJson = writeJson(j.sourceQueryParams());
        String bodyJson = j.sourceBody();
        String paginationJson = writeJson(j.pagination());
        String mappingJson = writeJson(j.mapping());

        int updated = jdbc.update(
            "UPDATE ingestion_job SET source_url=?, source_method=?, source_headers_json=?, " +
            "source_query_params_json=?, source_body_json=?, credential_id=?, pagination_json=?, " +
            "payload_format=?, payload_artifact_id=?, status=?, connection_id=?, " +
            "target_schema=?, target_table=?, mapping_json=?, row_count=?, rows_inserted=?, " +
            "bytes_fetched=?, mapping_hash=?, updated_at=?, completed_at=?, error_message=? WHERE id=?",
            j.sourceUrl(), j.sourceMethod(), headersJson, queryParamsJson, bodyJson,
            j.credentialId(), paginationJson, j.payloadFormat().dbValue(),
            j.payloadArtifactId(), j.status(), j.connectionId(),
            j.targetSchema(), j.targetTable(), mappingJson,
            j.rowCount(), j.rowsInserted(), j.bytesFetched(),
            j.mappingHash(),
            j.updatedAt(), j.completedAt(), j.errorMessage(), j.id());

        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ingestion_job (" + COLS + ") VALUES (" +
                "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                j.id(), j.sourceUrl(), j.sourceMethod(), headersJson, queryParamsJson,
                bodyJson, j.credentialId(), paginationJson,
                j.payloadFormat().dbValue(), j.payloadArtifactId(),
                j.status(), j.connectionId(), j.targetSchema(), j.targetTable(),
                mappingJson, j.rowCount(), j.rowsInserted(), j.bytesFetched(),
                j.mappingHash(),
                j.createdAt(), j.updatedAt(), j.completedAt(), j.errorMessage());
        }
    }

    @Override
    public Optional<IngestionJob> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(
                "SELECT " + COLS + " FROM ingestion_job WHERE id=?",
                this::mapRow, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<IngestionJob> list(String connectionIdOrNull, String statusOrNull,
                                   Long createdAfterOrNull, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" FROM ingestion_job WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (connectionIdOrNull != null) {
            sql.append(" AND connection_id=?");
            args.add(connectionIdOrNull);
        }
        if (statusOrNull != null) {
            sql.append(" AND status=?");
            args.add(statusOrNull);
        }
        if (createdAfterOrNull != null) {
            sql.append(" AND created_at>?");
            args.add(createdAfterOrNull);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), this::mapRow, args.toArray());
    }

    @Override
    public int count(String connectionIdOrNull, String statusOrNull, Long createdAfterOrNull) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ingestion_job WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (connectionIdOrNull != null) {
            sql.append(" AND connection_id=?");
            args.add(connectionIdOrNull);
        }
        if (statusOrNull != null) {
            sql.append(" AND status=?");
            args.add(statusOrNull);
        }
        if (createdAfterOrNull != null) {
            sql.append(" AND created_at>?");
            args.add(createdAfterOrNull);
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public void updateStatus(String id, String newStatus, String errorMessageOrNull, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET status=?, error_message=?, updated_at=? WHERE id=?",
            newStatus, errorMessageOrNull, updatedAt, id);
    }

    @Override
    public void updatePayloadArtifact(String id, String artifactId, int rowCount, long bytesFetched, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET payload_artifact_id=?, row_count=?, bytes_fetched=?, updated_at=? WHERE id=?",
            artifactId, rowCount, bytesFetched, updatedAt, id);
    }

    @Override
    public void updateMapping(String id, String mappingJson, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET mapping_json=?, updated_at=? WHERE id=?",
            mappingJson, updatedAt, id);
    }

    @Override
    public void updateMappingHash(String id, String mappingHash, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET mapping_hash=?, updated_at=? WHERE id=?",
            mappingHash, updatedAt, id);
    }

    @Override
    public void updateTargetTable(String id, String connectionId, String schema, String table, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET connection_id=?, target_schema=?, target_table=?, updated_at=? WHERE id=?",
            connectionId, schema, table, updatedAt, id);
    }

    @Override
    public void updateProgress(String id, int rowsInserted, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET rows_inserted=?, updated_at=? WHERE id=?",
            rowsInserted, updatedAt, id);
    }

    @Override
    public void updateCompleted(String id, int finalRowCount, long completedAt, long updatedAt) {
        jdbc.update(
            "UPDATE ingestion_job SET status='completed', row_count=?, completed_at=?, updated_at=? WHERE id=?",
            finalRowCount, completedAt, updatedAt, id);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM ingestion_job WHERE id=?", id);
    }

    @Override
    public int deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("DELETE FROM ingestion_job WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(")");
        return jdbc.update(sql.toString(), ids.toArray());
    }

    private IngestionJob mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new IngestionJob(
            rs.getString("id"),
            rs.getString("source_url"),
            rs.getString("source_method"),
            readJsonMap(rs.getString("source_headers_json")),
            readJsonMap(rs.getString("source_query_params_json")),
            rs.getString("source_body_json"),
            rs.getString("credential_id"),
            readPaginationSpec(rs.getString("pagination_json")),
            PayloadFormat.valueOf(rs.getString("payload_format").toUpperCase()),
            rs.getString("payload_artifact_id"),
            rs.getString("status"),
            rs.getString("connection_id"),
            rs.getString("target_schema"),
            rs.getString("target_table"),
            readIngestionMapping(rs.getString("mapping_json")),
            readIntOrNull(rs, "row_count"),
            readIntOrNull(rs, "rows_inserted"),
            readLongOrNull(rs, "bytes_fetched"),
            rs.getString("mapping_hash"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"),
            readLongOrNull(rs, "completed_at"),
            rs.getString("error_message")
        );
    }

    private Integer readIntOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int val = rs.getInt(col);
        return rs.wasNull() ? null : val;
    }

    private Long readLongOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long val = rs.getLong(col);
        return rs.wasNull() ? null : val;
    }

    private String writeJson(Object v) {
        if (v == null) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readJsonMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(s, STRING_MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private PaginationSpec readPaginationSpec(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return objectMapper.readValue(s, PaginationSpec.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private IngestionMapping readIngestionMapping(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return objectMapper.readValue(s, IngestionMapping.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
