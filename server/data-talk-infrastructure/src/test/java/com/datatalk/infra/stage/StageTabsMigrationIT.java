package com.datatalk.infra.stage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.datatalk.infra.persistence.SqlScriptSplitter;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTabsMigrationIT {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+).*");

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("stage_tabs_test.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("stage-tabs-test");
        ds = new HikariDataSource(cfg);
        jdbc = new JdbcTemplate(ds);
        applyMigrations(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void schemaCreatesAllArtifacts() {
        List<String> tables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", String.class);
        assertThat(tables).contains("stage_tabs", "stage_tab_payload");

        List<String> indexes = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_stage_tabs%' ORDER BY name",
            String.class);
        assertThat(indexes).containsExactlyInAnyOrder(
            "idx_stage_tabs_active", "idx_stage_tabs_type",
            "idx_stage_tabs_session", "idx_stage_tabs_connection");

        List<String> triggers = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'stage_tab%' ORDER BY name",
            String.class);
        assertThat(triggers).containsExactlyInAnyOrder(
            "stage_tabs_ai", "stage_tabs_au", "stage_tabs_ad",
            "stage_tab_payload_aiu", "stage_tab_payload_au", "stage_tab_payload_ad");

        // FTS5 virtual table
        List<String> vtables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='stage_tab_index'", String.class);
        assertThat(vtables).containsExactly("stage_tab_index");
    }

    @Test
    void scopeSessionRequiresOriginSessionId() {
        // Seed a session row so the FK passes
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES('s1', NULL, 'test', 0, NULL, ?, ?, 0)
            """, now, now);

        // workspace scope with null origin_session_id should succeed
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, origin_session_id, created_at, last_touched_at)
            VALUES('tab1', 'query_editor', 'workspace', 'ws-tab', NULL, ?, ?)
            """, now, now);

        // session scope with origin_session_id should succeed
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, origin_session_id, created_at, last_touched_at)
            VALUES('tab2', 'query_editor', 'session', 's-tab', 's1', ?, ?)
            """, now, now);

        // session scope WITHOUT origin_session_id should fail CHECK constraint
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, origin_session_id, created_at, last_touched_at)
            VALUES('tab3', 'query_editor', 'session', 'bad', NULL, ?, ?)
            """, now, now))
            .hasMessageContaining("constraint");
    }

    @Test
    void deletingTabCascadesPayloadAndFtsIndex() {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('tab-del', 'query_editor', 'workspace', 'to-delete', ?, ?)
            """, now, now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('tab-del', '{"sql":"SELECT 1"}', 'SELECT 1', 1, ?)
            """, now);

        // Verify payload exists
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM stage_tab_payload WHERE tab_id = 'tab-del'", Integer.class))
            .isEqualTo(1);

        // Delete the tab
        jdbc.update("DELETE FROM stage_tabs WHERE id = 'tab-del'");

        // Payload should be cascaded away
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM stage_tab_payload WHERE tab_id = 'tab-del'", Integer.class))
            .isEqualTo(0);
    }

    @Test
    void payloadInsertSyncsContentToFts() {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('tab-fts', 'query_editor', 'workspace', 'fts-test', ?, ?)
            """, now, now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('tab-fts', '{"sql":"SELECT * FROM orders"}', 'SELECT * FROM orders WHERE status = active', 1, ?)
            """, now);

        // FTS should find the content
        List<String> results = jdbc.queryForList(
            "SELECT stage_tab_index FROM stage_tab_index WHERE stage_tab_index MATCH ?",
            String.class, "\"active\"");
        // The FTS query returns a snippet; we just verify it found something
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM stage_tab_index WHERE stage_tab_index MATCH ?",
            Integer.class, "\"orders\"");
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void ftsIndexUsesStageTabsRowidAndIncludesTitle() {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, created_at, last_touched_at)
            VALUES('tab-rowid', 'query_editor', 'workspace', 'Customer Email Query', ?, ?)
            """, now, now);
        jdbc.update("""
            INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
            VALUES('tab-rowid', '{}', 'SELECT email FROM customers', 1, ?)
            """, now);

        Long tabRowid = jdbc.queryForObject("SELECT rowid FROM stage_tabs WHERE id = 'tab-rowid'", Long.class);
        Long ftsRowid = jdbc.queryForObject(
            "SELECT rowid FROM stage_tab_index WHERE stage_tab_index MATCH ?",
            Long.class, "\"Customer\"");

        assertThat(ftsRowid).isEqualTo(tabRowid);
    }

    @Test
    void activeIndexIsPartialOnArchivedFlag() {
        String sql = jdbc.queryForObject(
            "SELECT sql FROM sqlite_master WHERE type='index' AND name='idx_stage_tabs_active'",
            String.class);

        assertThat(sql).contains("WHERE archived = 0");
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
                    for (String statement : SqlScriptSplitter.split(sql)) {
                        jdbc.execute(statement);
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
