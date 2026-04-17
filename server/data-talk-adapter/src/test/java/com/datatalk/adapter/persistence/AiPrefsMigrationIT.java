package com.datatalk.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiPrefsMigrationIT {

    @Autowired @Qualifier("datatalkJdbc")
    JdbcTemplate jdbc;

    @Test
    void ai_user_prefs_table_exists_with_default_row() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_user_prefs WHERE id = 'default'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ai_model_prefs_table_exists_empty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_prefs", Integer.class);
        assertThat(count).isZero();
    }
}
