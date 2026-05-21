package com.datatalk.infra.stage;

import com.datatalk.application.stage.SessionTitleLookup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SessionTitleJdbcLookup implements SessionTitleLookup {

    private final JdbcTemplate jdbc;

    public SessionTitleJdbcLookup(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> titlesByIds(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(sessionIds.size(), "?"));
        String sql = "SELECT id, title FROM sessions WHERE id IN (" + placeholders + ")";
        Map<String, String> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            while (rs.next()) {
                result.put(rs.getString("id"), rs.getString("title"));
            }
            return null;
        }, sessionIds.toArray());
        return result;
    }
}
