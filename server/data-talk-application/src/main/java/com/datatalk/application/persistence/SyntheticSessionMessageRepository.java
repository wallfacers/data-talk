package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SyntheticSessionMessageRepository {

    private final JdbcTemplate jdbc;

    public SyntheticSessionMessageRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SyntheticSessionMessageRecord> MAPPER = (rs, i) -> new SyntheticSessionMessageRecord(
        rs.getString("id"),
        rs.getString("session_id"),
        rs.getString("kind"),
        rs.getString("text"),
        rs.getString("metadata_json"),
        rs.getLong("created_at")
    );

    public void insert(SyntheticSessionMessageRecord message) {
        jdbc.update("""
            INSERT INTO synthetic_session_messages(id, session_id, kind, text, metadata_json, created_at)
            VALUES(?, ?, ?, ?, ?, ?)
            """,
            message.id(), message.sessionId(), message.kind(), message.text(),
            message.metadataJson(), message.createdAt());
    }

    public List<SyntheticSessionMessageRecord> findBySession(String sessionId) {
        return jdbc.query("""
                SELECT * FROM synthetic_session_messages
                WHERE session_id = ?
                ORDER BY created_at ASC, id ASC
                """,
            MAPPER, sessionId);
    }
}
