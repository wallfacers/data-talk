package com.datatalk.infra.ai;

import com.datatalk.application.ai.AiModelPrefsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

@Repository
public class AiModelPrefsRepositoryJdbc implements AiModelPrefsRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiModelPrefsRepositoryJdbc(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Set<String> disabledSet() {
        Set<String> out = new HashSet<>();
        jdbc.query("SELECT provider_id, model_id FROM ai_model_prefs WHERE enabled = 0",
            rs -> {
                out.add(rs.getString("provider_id") + "/" + rs.getString("model_id"));
            });
        return out;
    }

    @Override
    public void setEnabled(String providerId, String modelId, boolean enabled) {
        if (enabled) {
            jdbc.update("DELETE FROM ai_model_prefs WHERE provider_id = ? AND model_id = ?",
                providerId, modelId);
        } else {
            long now = clock.millis();
            jdbc.update("""
                INSERT INTO ai_model_prefs(provider_id, model_id, enabled, updated_at)
                VALUES(?, ?, 0, ?)
                ON CONFLICT(provider_id, model_id) DO UPDATE SET enabled = 0, updated_at = ?
                """, providerId, modelId, now, now);
        }
    }
}
