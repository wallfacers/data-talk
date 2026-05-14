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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class V20MigrationIT {
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
        cfg.setPoolName("v20-sqlite");
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
    void ingestionJobTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_job'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void ingestionCredentialTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_credential'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void fileArtifactKindCheckAcceptsIngestionPayload() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-ip-1','workspace','temporary','ingestion_payload','p.json','/tmp/p.json',0,0,0,1)");
        assertThat(jdbc.queryForObject(
            "SELECT kind FROM file_artifact WHERE id='test-ip-1'", String.class))
            .isEqualTo("ingestion_payload");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-ip-1'");
    }

    @Test
    void fileArtifactKindCheckStillAcceptsDashboard() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-d-1','workspace','temporary','dashboard','d.json','/tmp/d.json',0,0,0,1)");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-d-1'");
    }

    @Test
    void fileArtifactExternalIndexExists() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_file_artifact_external'",
            Integer.class)).isEqualTo(1);
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
