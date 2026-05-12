package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
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
public class JdbcIngestionCredentialRepository implements IngestionCredentialRepository {

    private static final String COLS = "id, name, auth_scheme, config_json, vault_id, created_at, updated_at";
    private static final TypeReference<HashMap<String, String>> CONFIG_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcIngestionCredentialRepository(
            @Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(IngestionCredential c) {
        String configJson = writeJson(c.configNonSecret());
        int updated = jdbc.update(
            "UPDATE ingestion_credential SET name=?, auth_scheme=?, config_json=?, vault_id=?, updated_at=? WHERE id=?",
            c.name(), c.scheme().dbValue(), configJson, c.vaultId(), c.updatedAt(), c.id());
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ingestion_credential (" + COLS + ") VALUES (?,?,?,?,?,?,?)",
                c.id(), c.name(), c.scheme().dbValue(),
                configJson, c.vaultId(), c.createdAt(), c.updatedAt());
        }
    }

    @Override
    public Optional<IngestionCredential> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(
                "SELECT " + COLS + " FROM ingestion_credential WHERE id=?",
                this::mapRow, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<IngestionCredential> findByName(String name) {
        try {
            return Optional.of(jdbc.queryForObject(
                "SELECT " + COLS + " FROM ingestion_credential WHERE name=?",
                this::mapRow, name));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<IngestionCredential> findAll() {
        return jdbc.query(
            "SELECT " + COLS + " FROM ingestion_credential ORDER BY created_at DESC",
            this::mapRow);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM ingestion_credential WHERE id=?", id);
    }

    @Override
    public int countReferencingJobs(String credentialId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ingestion_job WHERE credential_id=?", Integer.class, credentialId);
        return n == null ? 0 : n;
    }

    @Override
    public void nullifyCredentialOnJobs(String credentialId) {
        jdbc.update("UPDATE ingestion_job SET credential_id=NULL WHERE credential_id=?", credentialId);
    }

    private IngestionCredential mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new IngestionCredential(
            rs.getString("id"),
            rs.getString("name"),
            AuthScheme.valueOf(rs.getString("auth_scheme").toUpperCase()),
            readJsonMap(rs.getString("config_json")),
            rs.getString("vault_id"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));
    }

    private String writeJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readJsonMap(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(s, CONFIG_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
