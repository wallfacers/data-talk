package com.datatalk.adapter.ingestion.migration;

import com.datatalk.infra.persistence.SqlScriptSplitter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class V23MigrationIT {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+).*");

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("datatalk.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("v23-sqlite");
        HikariDataSource ds = new HikariDataSource(cfg);
        jdbc = new JdbcTemplate(ds);
        applyMigrations(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            ((HikariDataSource) jdbc.getDataSource()).close();
        }
    }

    @Test
    void nameColumnExists() {
        assertThat(columnExists("name")).isTrue();
    }

    @Test
    void creatorColumnsExist() {
        assertThat(columnExists("created_by_kind")).isTrue();
        assertThat(columnExists("created_by_session_id")).isTrue();
        assertThat(columnExists("created_by_label")).isTrue();
    }

    @Test
    void heartbeatColumnExists() {
        assertThat(columnExists("heartbeat_at")).isTrue();
    }

    @Test
    void heartbeatIndexExists() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_ingestion_job_heartbeat'",
            Integer.class);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void createdByKindDefaultsToAi() {
        jdbc.update("INSERT INTO ingestion_job " +
            "(id, source_url, source_method, source_headers_json, source_query_params_json, " +
            " payload_format, status, created_at, updated_at) VALUES " +
            "('v23_default', 'https://x', 'GET', '{}', '{}', 'json', 'pending', 0, 0)");

        String kind = jdbc.queryForObject(
            "SELECT created_by_kind FROM ingestion_job WHERE id='v23_default'", String.class);
        assertThat(kind).isEqualTo("ai");

        jdbc.update("DELETE FROM ingestion_job WHERE id='v23_default'");
    }

    private boolean columnExists(String column) {
        List<Map<String, Object>> rows = jdbc.queryForList("PRAGMA table_info(ingestion_job)");
        return rows.stream().anyMatch(r -> column.equals(r.get("name")));
    }

    private void applyMigrations(JdbcTemplate jdbc) throws Exception {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS schema_version (" +
            "  version TEXT PRIMARY KEY, applied_at INTEGER NOT NULL" +
            ")");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*.sql");
        Arrays.stream(resources)
            .sorted(Comparator
                .comparingInt(this::migrationOrder)
                .thenComparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)))
            .forEach(resource -> {
                try {
                    String version = extractVersion(resource.getFilename());
                    Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM schema_version WHERE version = ?",
                        Integer.class, version);
                    if (count != null && count > 0) return;

                    String sql = readResource(resource);
                    for (String stmt : SqlScriptSplitter.split(sql)) {
                        jdbc.execute(stmt);
                    }
                    jdbc.update("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)",
                        version, System.currentTimeMillis());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to apply migration: " + resource.getFilename(), e);
                }
            });
    }

    private String extractVersion(String filename) {
        if (filename == null) return "unknown";
        int dot = filename.indexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private int migrationOrder(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) return Integer.MAX_VALUE;
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        if (!matcher.matches()) return Integer.MAX_VALUE;
        return Integer.parseInt(matcher.group(1));
    }

    private String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
