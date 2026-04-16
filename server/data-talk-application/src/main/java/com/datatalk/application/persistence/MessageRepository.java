package com.datatalk.application.persistence;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public MessageRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public void save(Message m) {
        String partsJson;
        try {
            partsJson = om.writeValueAsString(m.parts());
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize parts", e);
        }
        jdbc.update("""
            INSERT INTO messages(id, session_id, role, parts_json, created_at)
            VALUES(?, ?, ?, ?, ?)
            """,
            m.id(), m.sessionId(), m.role().name(), partsJson, m.createdAt()
        );
    }

    public List<Message> findBySession(String sessionId) {
        return jdbc.query("""
            SELECT id, session_id, role, parts_json, created_at
            FROM messages
            WHERE session_id = ?
            ORDER BY created_at ASC, id ASC
            """,
            (rs, i) -> {
                try {
                    List<Part> parts = om.readValue(
                        rs.getString("parts_json"),
                        new TypeReference<List<Part>>() {}
                    );
                    return new Message(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        Message.Role.valueOf(rs.getString("role")),
                        parts,
                        rs.getLong("created_at")
                    );
                } catch (Exception e) {
                    throw new IllegalStateException("cannot deserialize parts", e);
                }
            },
            sessionId
        );
    }
}
