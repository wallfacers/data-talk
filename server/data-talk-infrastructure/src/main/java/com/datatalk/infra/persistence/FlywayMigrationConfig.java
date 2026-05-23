package com.datatalk.infra.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+).*");

    @Lazy
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

    @Lazy
    @Bean
    public JdbcTemplate datatalkJdbc(@Lazy @Qualifier("datatalkDataSource") DataSource datatalkDataSource) {
        // Migration deferred to applyMigrationsAfterReady() to keep the SQLite
        // pool's first connect (which can take ~8s on a fat jar with 30+ JDBC
        // drivers) off the main startup path.  See
        // openspec/changes/backend-startup-fast-path/design.md.
        return new JdbcTemplate(datatalkDataSource);
    }

    /**
     * Run schema migrations after Spring is fully ready and Tomcat is already
     * accepting requests.  This keeps the "Started in X.X seconds" milestone
     * fast (drives the Tauri welcome→main-page transition) while still
     * guaranteeing migrations complete before any persistence-using feature is
     * exercised by the user.  Subsequent JDBC operations from controllers
     * benefit from the now-warm pool.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void applyMigrationsAfterReady(ApplicationReadyEvent event) {
        try {
            JdbcTemplate jdbc = event.getApplicationContext().getBean("datatalkJdbc", JdbcTemplate.class);
            applyMigrations(jdbc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply post-ready migrations", e);
        }
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
            .sorted(Comparator
                .comparingInt(this::migrationOrder)
                .thenComparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)))
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
                    for (String statement : SqlScriptSplitter.split(sql)) {
                        try {
                            jdbc.execute(statement);
                        } catch (Exception e) {
                            if (isAlreadyExistsError(e)) {
                                log.info("Skipping already-applied DDL in migration {}: {}",
                                    resource.getFilename(), e.getMessage());
                            } else {
                                throw e;
                            }
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

    /**
     * Detect SQLite "duplicate column name" errors so idempotent DDL
     * (e.g. ADD COLUMN on an already-migrated table) does not abort startup.
     */
    private boolean isAlreadyExistsError(Throwable t) {
        String msg = t.getMessage();
        return msg != null && (
            msg.contains("duplicate column name") ||
            msg.contains("already exists") ||
            msg.contains("table ") && msg.contains("already exists")
        );
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
