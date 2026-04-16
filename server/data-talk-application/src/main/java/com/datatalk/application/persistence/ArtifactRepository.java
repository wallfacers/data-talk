package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ArtifactRepository {

    private final JdbcTemplate jdbc;

    public ArtifactRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ArtifactRecord> MAPPER = (rs, i) -> new ArtifactRecord(
        rs.getString("id"),
        rs.getInt("version"),
        rs.getString("session_id"),
        rs.getString("kind"),
        rs.getString("produced_by"),
        rs.getString("payload_ref"),
        rs.getInt("payload_size"),
        rs.getString("supersedes_id"),
        (Integer) rs.getObject("supersedes_ver"),
        rs.getInt("pinned") == 1,
        rs.getLong("created_at")
    );

    public void insert(ArtifactRecord a) {
        jdbc.update("""
            INSERT INTO artifacts(id, version, session_id, kind, produced_by, payload_ref, payload_size,
              supersedes_id, supersedes_ver, pinned, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            a.id(), a.version(), a.sessionId(), a.kind(), a.producedBy(),
            a.payloadRef(), a.payloadSize(), a.supersedesId(), a.supersedesVersion(),
            a.pinned() ? 1 : 0, a.createdAt()
        );
    }

    public void updatePinned(String id, int version, boolean pinned) {
        jdbc.update("UPDATE artifacts SET pinned = ? WHERE id = ? AND version = ?",
            pinned ? 1 : 0, id, version);
    }

    public void updateSupersedes(String oldId, int oldVersion, String newId) {
        jdbc.update("UPDATE artifacts SET supersedes_id = ?, supersedes_ver = ? WHERE id = ? AND version = ?",
            newId, oldVersion, oldId, oldVersion);
    }

    public Optional<ArtifactRecord> findLatestById(String id) {
        var list = jdbc.query(
            "SELECT * FROM artifacts WHERE id = ? ORDER BY version DESC LIMIT 1", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<ArtifactRecord> findBySession(String sessionId) {
        return jdbc.query(
            "SELECT * FROM artifacts WHERE session_id = ? ORDER BY created_at ASC, version ASC",
            MAPPER, sessionId);
    }
}
