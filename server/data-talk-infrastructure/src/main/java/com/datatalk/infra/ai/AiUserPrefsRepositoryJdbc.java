package com.datatalk.infra.ai;

import com.datatalk.application.ai.AiUserPrefsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;

@Repository
public class AiUserPrefsRepositoryJdbc implements AiUserPrefsRepository {

    private static final String ID = "default";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiUserPrefsRepositoryJdbc(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String getCurrentModel() {
        var list = jdbc.query(
            "SELECT current_model FROM ai_user_prefs WHERE id = ?",
            (rs, i) -> rs.getString("current_model"), ID);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void setCurrentModel(String modelId) {
        jdbc.update("UPDATE ai_user_prefs SET current_model = ?, updated_at = ? WHERE id = ?",
            modelId, clock.millis(), ID);
    }
}
