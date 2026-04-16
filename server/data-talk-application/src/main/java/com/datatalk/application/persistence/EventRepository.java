package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbc;

    public EventRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String sessionId, long eventId, String eventType, String payloadJson, long ts) {
        jdbc.update("""
            INSERT INTO events(event_id, session_id, event_type, payload_json, ts)
            VALUES(?, ?, ?, ?, ?)
            """,
            eventId, sessionId, eventType, payloadJson, ts
        );
    }

    public long maxEventId(String sessionId) {
        Long max = jdbc.queryForObject(
            "SELECT COALESCE(MAX(event_id), 0) FROM events WHERE session_id = ?",
            Long.class, sessionId);
        return max == null ? 0L : max;
    }
}
