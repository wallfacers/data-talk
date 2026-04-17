package com.datatalk.adapter.persistence;

import com.datatalk.application.ai.AiModelPrefsRepository;
import com.datatalk.application.ai.AiUserPrefsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiPrefsRepositoryIT {

    @Autowired AiUserPrefsRepository userPrefs;
    @Autowired AiModelPrefsRepository modelPrefs;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE ai_user_prefs SET current_model = NULL WHERE id = 'default'");
        jdbc.update("DELETE FROM ai_model_prefs");
    }

    @Test
    void get_returns_null_initially() {
        assertThat(userPrefs.getCurrentModel()).isNull();
    }

    @Test
    void set_then_get_round_trips() {
        userPrefs.setCurrentModel("anthropic/claude-3-5-sonnet");
        assertThat(userPrefs.getCurrentModel()).isEqualTo("anthropic/claude-3-5-sonnet");
    }

    @Test
    void set_null_clears_value() {
        userPrefs.setCurrentModel("openai/gpt-5");
        userPrefs.setCurrentModel(null);
        assertThat(userPrefs.getCurrentModel()).isNull();
    }

    @Test
    void disabled_prefs_retrieved_as_set() {
        modelPrefs.setEnabled("openai", "gpt-5", false);
        modelPrefs.setEnabled("openai", "gpt-5-nano", false);
        var disabled = modelPrefs.disabledSet();
        assertThat(disabled).containsExactlyInAnyOrder(
            "openai/gpt-5", "openai/gpt-5-nano");
    }

    @Test
    void enabling_removes_row_from_disabled_set() {
        modelPrefs.setEnabled("openai", "gpt-5", false);
        modelPrefs.setEnabled("openai", "gpt-5", true);
        assertThat(modelPrefs.disabledSet()).isEmpty();
    }

    @Test
    void enabling_already_enabled_is_idempotent() {
        modelPrefs.setEnabled("openai", "gpt-5", true);
        assertThat(modelPrefs.disabledSet()).isEmpty();
    }
}
