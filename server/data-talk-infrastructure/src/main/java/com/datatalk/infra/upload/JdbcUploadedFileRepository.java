package com.datatalk.infra.upload;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
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
public class JdbcUploadedFileRepository implements UploadedFileRepository {

    private static final TypeReference<Map<String, Object>> ANALYSIS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcUploadedFileRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(UploadedFile file) {
        jdbc.update(
                "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, analysis_json, created_at) VALUES (?,?,?,?,?,?,?,?)",
                file.id(), file.sessionId(), file.filename(), file.mimeType(),
                file.sizeBytes(), file.physicalPath(), writeAnalysis(file.analysis()),
                file.createdAt().toEpochMilli());
    }

    @Override
    public Optional<UploadedFile> findById(String id) {
        List<UploadedFile> results = jdbc.query("SELECT * FROM uploaded_file WHERE id = ?", mapper(), id);
        return results.stream().findFirst();
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM uploaded_file WHERE id = ?", id);
    }

    @Override
    public List<String> findIdsBySessionId(String sessionId) {
        return jdbc.queryForList("SELECT id FROM uploaded_file WHERE session_id = ?", String.class, sessionId);
    }

    @Override
    public List<UploadedFile> findOlderThan(Instant cutoff) {
        return jdbc.query("SELECT * FROM uploaded_file WHERE created_at < ?", mapper(), cutoff.toEpochMilli());
    }

    private RowMapper<UploadedFile> mapper() {
        return (ResultSet rs, int rowNum) -> new UploadedFile(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("filename"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("physical_path"),
                readAnalysis(rs.getString("analysis_json")),
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }

    private String writeAnalysis(Map<String, Object> analysis) {
        if (analysis == null) return null;
        try { return json.writeValueAsString(analysis); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private Map<String, Object> readAnalysis(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return json.readValue(raw, ANALYSIS_TYPE); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
