package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SessionDataContextRepository {

    private final JdbcTemplate jdbc;

    public SessionDataContextRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SessionDataContextRecord> MAPPER = (rs, i) -> new SessionDataContextRecord(
        rs.getString("session_id"),
        rs.getString("connection_id"),
        rs.getString("connection_name_snapshot"),
        rs.getString("database_name"),
        rs.getString("schema_name"),
        rs.getString("selected_level"),
        rs.getLong("updated_at")
    );

    public Optional<SessionDataContextRecord> findBySessionId(String sessionId) {
        var list = jdbc.query(
            "SELECT * FROM session_data_contexts WHERE session_id = ?",
            MAPPER,
            sessionId
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<SessionDataContextRecord> findByConnectionId(String connectionId) {
        return jdbc.query(
            "SELECT * FROM session_data_contexts WHERE connection_id = ?",
            MAPPER,
            connectionId
        );
    }

    public void upsert(SessionDataContextRecord record) {
        jdbc.update("""
            INSERT INTO session_data_contexts(
              session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              connection_id = excluded.connection_id,
              connection_name_snapshot = excluded.connection_name_snapshot,
              database_name = excluded.database_name,
              schema_name = excluded.schema_name,
              selected_level = excluded.selected_level,
              updated_at = excluded.updated_at
            """,
            record.sessionId(),
            record.connectionId(),
            record.connectionNameSnapshot(),
            record.databaseName(),
            record.schemaName(),
            record.selectedLevel(),
            record.updatedAt()
        );
    }

    public int deleteBySessionId(String sessionId) {
        return jdbc.update("DELETE FROM session_data_contexts WHERE session_id = ?", sessionId);
    }
}
