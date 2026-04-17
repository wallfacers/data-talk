package com.datatalk.infra.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Provisions a dedicated SQLite datasource for DataTalk's ontology + channel
 * storage and runs SQL migrations against it at startup.
 *
 * <p>This is kept separate from the existing {@code DataSourcesConfig} so the
 * legacy {@code /api/query} demo flow remains undisturbed.</p>
 */
@Configuration
@ConditionalOnProperty(name = "datatalk.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationConfig.class);

    @Bean("datatalkDataSource")
    public DataSource datatalkDataSource(
        @Value("${datatalk.persistence.sqlite-path:./data/datatalk.db}") String path
    ) throws Exception {
        Path p = Paths.get(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + path);
        cfg.setMaximumPoolSize(1); // SQLite serializes writes
        cfg.setPoolName("datatalk-sqlite");
        cfg.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return new HikariDataSource(cfg);
    }

    @Bean
    public JdbcTemplate datatalkJdbc(@Qualifier("datatalkDataSource") DataSource datatalkDataSource) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(datatalkDataSource);
        applyMigrations(jdbc);
        return jdbc;
    }

    private void applyMigrations(JdbcTemplate jdbc) throws Exception {
        // Create schema_version tracking table
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS schema_version (" +
            "  version TEXT PRIMARY KEY, applied_at INTEGER NOT NULL" +
            ")");

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/V*.sql");
        if (resources.length == 0) {
            log.info("No migration files found in classpath:db/migration");
            return;
        }

        Arrays.stream(resources)
            .sorted(Comparator.comparing(Resource::getFilename))
            .forEach(resource -> {
                try {
                    String version = extractVersion(resource.getFilename());
                    Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM schema_version WHERE version = ?",
                        Integer.class, version);
                    if (count != null && count > 0) {
                        log.debug("Migration {} already applied, skipping", version);
                        return;
                    }

                    log.info("Applying migration: {}", resource.getFilename());
                    String sql = readResource(resource);
                    String[] statements = sql.split(";");
                    for (String stmt : statements) {
                        String trimmed = stmt.trim();
                        if (!trimmed.isEmpty()) {
                            jdbc.execute(trimmed);
                        }
                    }
                    jdbc.update("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)",
                        version, System.currentTimeMillis());
                    log.info("Migration {} applied successfully", version);
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

    private String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
