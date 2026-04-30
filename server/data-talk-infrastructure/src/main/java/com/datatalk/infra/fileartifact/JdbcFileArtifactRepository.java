package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcFileArtifactRepository implements FileArtifactRepository {

    private static final String COLS = """
            id, scope, status, kind, session_id, connection_id, filename, physical_path,
            size_bytes, mime_type, title, summary, created_at, updated_at, archived_at, metadata_json
            """;

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcFileArtifactRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(FileArtifact artifact) {
        jdbc.update(
                "INSERT INTO file_artifact (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                artifact.id(),
                artifact.scope().dbValue(),
                artifact.status().dbValue(),
                artifact.kind().dbValue(),
                artifact.sessionId(),
                artifact.connectionId(),
                artifact.filename(),
                artifact.physicalPath(),
                artifact.sizeBytes(),
                artifact.mimeType(),
                artifact.title(),
                artifact.summary(),
                artifact.createdAt().toEpochMilli(),
                artifact.updatedAt().toEpochMilli(),
                instantToMillis(artifact.archivedAt()),
                writeMetadata(artifact.metadata()));
    }

    @Override
    public Optional<FileArtifact> findById(String id) {
        var rows = jdbc.query("SELECT " + COLS + " FROM file_artifact WHERE id = ?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
        var rows = jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE physical_path = ? LIMIT 1",
                mapper(),
                physicalPath);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<FileArtifact> findBySession(String sessionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE session_id = ? ORDER BY created_at DESC",
                mapper(),
                sessionId);
    }

    @Override
    public List<FileArtifact> findArchivedByConnection(String connectionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact "
                        + "WHERE connection_id = ? AND scope = 'workspace' AND status = 'archived' "
                        + "ORDER BY archived_at DESC",
                mapper(),
                connectionId);
    }

    @Override
    public List<FileArtifact> findCandidatesBySession(String sessionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE session_id = ? AND status = 'candidate'",
                mapper(),
                sessionId);
    }

    @Override
    public List<FileArtifact> findAllSessionScoped() {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE scope = 'session'",
                mapper());
    }

    @Override
    public List<FileArtifact> findAllWorkspaceScopedArchived() {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE scope = 'workspace' AND status = 'archived'",
                mapper());
    }

    @Override
    public void updateStatus(String id, FileArtifactStatus newStatus) {
        jdbc.update(
                "UPDATE file_artifact SET status = ?, updated_at = ? WHERE id = ?",
                newStatus.dbValue(),
                Instant.now().toEpochMilli(),
                id);
    }

    @Override
    public void updateLocation(
            String id,
            FileArtifactStatus newStatus,
            String newScope,
            String newPhysicalPath,
            String newConnectionId) {
        jdbc.update(
                "UPDATE file_artifact SET status = ?, scope = ?, physical_path = ?, connection_id = ?, "
                        + "updated_at = ? WHERE id = ?",
                newStatus.dbValue(),
                newScope,
                newPhysicalPath,
                newConnectionId,
                Instant.now().toEpochMilli(),
                id);
    }

    @Override
    public void markArchived(String id, String connectionId, String newPhysicalPath) {
        long now = Instant.now().toEpochMilli();
        jdbc.update(
                "UPDATE file_artifact SET status = 'archived', scope = 'workspace', connection_id = ?, "
                        + "physical_path = ?, archived_at = ?, updated_at = ? WHERE id = ?",
                connectionId,
                newPhysicalPath,
                now,
                now,
                id);
    }

    @Override
    public void deleteTransientByForSession(String sessionId) {
        jdbc.update(
                "DELETE FROM file_artifact WHERE session_id = ? AND status IN ('temporary', 'candidate')",
                sessionId);
    }

    @Override
    public void detachArchivedFromSession(String sessionId) {
        jdbc.update(
                "UPDATE file_artifact SET session_id = NULL WHERE session_id = ? AND status = 'archived'",
                sessionId);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM file_artifact WHERE id = ?", id);
    }

    @Override
    public void updateMetadata(String id, long sizeBytes, long updatedAtMillis) {
        jdbc.update(
                "UPDATE file_artifact SET size_bytes = ?, updated_at = ? WHERE id = ?",
                sizeBytes,
                updatedAtMillis,
                id);
    }

    private RowMapper<FileArtifact> mapper() {
        return (ResultSet rs, int rowNum) -> new FileArtifact(
                rs.getString("id"),
                FileArtifactScope.fromDb(rs.getString("scope")),
                FileArtifactStatus.fromDb(rs.getString("status")),
                FileArtifactKind.fromDb(rs.getString("kind")),
                rs.getString("session_id"),
                rs.getString("connection_id"),
                rs.getString("filename"),
                rs.getString("physical_path"),
                rs.getLong("size_bytes"),
                rs.getString("mime_type"),
                rs.getString("title"),
                rs.getString("summary"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("updated_at")),
                instantOrNull(rs, "archived_at"),
                readMetadata(rs.getString("metadata_json")));
    }

    private static Long instantToMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instantOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize file artifact metadata", e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(metadataJson, METADATA_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
