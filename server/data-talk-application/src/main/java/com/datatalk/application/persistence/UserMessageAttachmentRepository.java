package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persists DataTalk-local file_upload parts that travel alongside user
 * messages (see BUG-0056). OpenCode's wire protocol cannot carry these
 * parts, so they are echoed locally on send and reloaded from this table
 * during {@code GET /api/sessions/{id}/messages} replays.
 */
@Repository
public class UserMessageAttachmentRepository {

    private final JdbcTemplate jdbc;

    public UserMessageAttachmentRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<UserMessageAttachmentRecord> MAPPER = (rs, i) -> new UserMessageAttachmentRecord(
        rs.getString("id"),
        rs.getString("session_id"),
        rs.getString("message_id"),
        rs.getInt("position"),
        rs.getString("part_json"),
        rs.getLong("created_at")
    );

    public void insert(UserMessageAttachmentRecord record) {
        jdbc.update("""
            INSERT INTO user_message_attachments(id, session_id, message_id, position, part_json, created_at)
            VALUES(?, ?, ?, ?, ?, ?)
            """,
            record.id(), record.sessionId(), record.messageId(), record.position(),
            record.partJson(), record.createdAt());
    }

    public List<UserMessageAttachmentRecord> findBySession(String sessionId) {
        return jdbc.query("""
                SELECT * FROM user_message_attachments
                WHERE session_id = ?
                ORDER BY message_id ASC, position ASC, id ASC
                """,
            MAPPER, sessionId);
    }
}
