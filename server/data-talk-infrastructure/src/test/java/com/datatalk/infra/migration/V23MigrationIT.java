package com.datatalk.infra.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V23MigrationIT {
    @Autowired JdbcTemplate jdbc;

    @Test void nameColumnExists() {
        assertThat(columnExists("name")).isTrue();
    }

    @Test void creatorColumnsExist() {
        assertThat(columnExists("created_by_kind")).isTrue();
        assertThat(columnExists("created_by_session_id")).isTrue();
        assertThat(columnExists("created_by_label")).isTrue();
    }

    @Test void heartbeatColumnExists() {
        assertThat(columnExists("heartbeat_at")).isTrue();
    }

    @Test void heartbeatIndexExists() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_ingestion_job_heartbeat'",
            Integer.class);
        assertThat(cnt).isEqualTo(1);
    }

    @Test void createdByKindDefaultsToAi() {
        // Insert without explicit created_by_kind — DEFAULT 'ai' should apply
        jdbc.update("INSERT INTO ingestion_job " +
            "(id, source_url, source_method, source_headers_json, source_query_params_json, " +
            " payload_format, status, created_at, updated_at) VALUES " +
            "('v23_default', 'https://x', 'GET', '{}', '{}', 'JSON', 'pending', 0, 0)");

        String kind = jdbc.queryForObject(
            "SELECT created_by_kind FROM ingestion_job WHERE id='v23_default'", String.class);
        assertThat(kind).isEqualTo("ai");

        jdbc.update("DELETE FROM ingestion_job WHERE id='v23_default'");
    }

    private boolean columnExists(String column) {
        List<Map<String, Object>> rows = jdbc.queryForList("PRAGMA table_info(ingestion_job)");
        return rows.stream().anyMatch(r -> column.equals(r.get("name")));
    }
}
