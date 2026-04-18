package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SessionRepository {

    private final JdbcTemplate jdbc;

    public SessionRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SessionRecord> MAPPER = (rs, i) -> new SessionRecord(
        rs.getString("id"),
        rs.getString("connection_id"),
        rs.getString("title"),
        rs.getInt("has_ever_sent") == 1,
        rs.getString("opencode_sid"),
        rs.getLong("created_at"),
        rs.getLong("updated_at"),
        rs.getInt("title_locked") == 1
    );

    public void upsert(SessionRecord s) {
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              connection_id = excluded.connection_id,
              title         = excluded.title,
              has_ever_sent = excluded.has_ever_sent,
              opencode_sid  = excluded.opencode_sid,
              updated_at    = excluded.updated_at,
              title_locked  = excluded.title_locked
            """,
            s.id(), s.connectionId(), s.title(), s.hasEverSent() ? 1 : 0,
            s.openCodeSid(), s.createdAt(), s.updatedAt(), s.titleLocked() ? 1 : 0
        );
    }

    public Optional<SessionRecord> findById(String id) {
        var list = jdbc.query("SELECT * FROM sessions WHERE id = ?", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void markHasEverSent(String id, long now) {
        jdbc.update("UPDATE sessions SET has_ever_sent = 1, updated_at = ? WHERE id = ?", now, id);
    }

    public int updateOpenCodeSid(String id, String openCodeSid, long now) {
        return jdbc.update(
            "UPDATE sessions SET opencode_sid = ?, updated_at = ? WHERE id = ?",
            openCodeSid, now, id);
    }

    public int updateTitle(String id, String title, long now) {
        return jdbc.update(
            "UPDATE sessions SET title = ?, updated_at = ? WHERE id = ?",
            title, now, id);
    }

    /** Only updates title when title_locked=0; returns affected rows (0 means locked/skipped). */
    public int applyAutoTitle(String id, String title, long now) {
        return jdbc.update(
            "UPDATE sessions SET title = ?, updated_at = ? WHERE id = ? AND title_locked = 0",
            title, now, id);
    }

    /** Atomically sets title + lock; avoids race condition of separate updateTitle + lockTitle. */
    public int updateTitleAndLock(String id, String title, long now) {
        return jdbc.update(
            "UPDATE sessions SET title = ?, title_locked = 1, updated_at = ? WHERE id = ?",
            title, now, id);
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM sessions WHERE id = ?", id);
    }

    public java.util.List<SessionRecord> listAll() {
        return jdbc.query("SELECT * FROM sessions ORDER BY updated_at DESC", MAPPER);
    }

    public java.util.List<SessionRecord> listByConnection(String connectionId) {
        return jdbc.query(
            "SELECT * FROM sessions WHERE connection_id = ? ORDER BY updated_at DESC",
            MAPPER, connectionId);
    }
}
