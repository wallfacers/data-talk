package com.datatalk.infra.stage;

import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTabJdbcRepositoryTest {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+).*");

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private HikariDataSource ds;
    private StageTabJdbcRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("repo_test.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("stage-repo-test");
        ds = new HikariDataSource(cfg);
        jdbc = new JdbcTemplate(ds);
        applyMigrations(jdbc);
        repo = new StageTabJdbcRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void upsertMetadataAssignsPayloadVersionOne() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t1", "query_editor", StageTabScope.WORKSPACE,
            "Test Tab", null, null, null, null, 1,
            false, false, null, now, now);

        int version = repo.upsertMetadata(tab, null);

        assertThat(version).isEqualTo(1);
        Optional<StageTab> loaded = repo.findById("t1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().title()).isEqualTo("Test Tab");
        assertThat(loaded.get().scope()).isEqualTo(StageTabScope.WORKSPACE);
    }

    @Test
    void upsertPayloadBumpsPayloadVersionAndSyncsFts() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t2", "query_editor", StageTabScope.WORKSPACE,
            "Payload Tab", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);

        repo.upsertPayload("t2", "{\"sql\":\"SELECT 1\"}", "SELECT 1 FROM users", 0, now);

        Optional<StageTabContent> content = repo.findContent("t2");
        assertThat(content).isPresent();
        assertThat(content.get().payloadJson()).isEqualTo("{\"sql\":\"SELECT 1\"}");
        assertThat(content.get().contentText()).isEqualTo("SELECT 1 FROM users");
        assertThat(content.get().contentVersion()).isEqualTo(1);

        // Verify FTS sync via indexer
        StageTabIndexer indexer = new StageTabIndexer(jdbc);
        List<StageTabIndexer.RowidScore> results = indexer.ftsMatch("users", false, 10);
        assertThat(results).isNotEmpty();
    }

    @Test
    void upsertPayloadRejectsStaleVersion() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t3", "query_editor", StageTabScope.WORKSPACE,
            "Concurrency Tab", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);
        repo.upsertPayload("t3", "{\"v\":1}", "text v1", 0, now);

        // Try to update with wrong expected version
        assertThatThrownBy(() -> repo.upsertPayload("t3", "{\"v\":2}", "text v2", 0, now))
            .isInstanceOf(StageTabConcurrencyException.class);
    }

    @Test
    void sessionScopeCascadesOnSessionDelete() {
        long now = System.currentTimeMillis();
        // Seed session
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES('sess-cascade', NULL, 'cascade-test', 0, NULL, ?, ?, 0)
            """, now, now);

        StageTab tab = new StageTab("t-cascade", "query_editor", StageTabScope.SESSION,
            "Session Tab", null, null, null, "sess-cascade", 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);

        assertThat(repo.findById("t-cascade")).isPresent();

        // Delete the session — tab should cascade
        jdbc.update("DELETE FROM sessions WHERE id = 'sess-cascade'");
        assertThat(repo.findById("t-cascade")).isEmpty();
    }

    @Test
    void archiveStaleSinceFlipsExpiredTabs() {
        long now = System.currentTimeMillis();
        long staleTime = now - 100_000;

        StageTab active = new StageTab("t-active", "query_editor", StageTabScope.WORKSPACE,
            "Active", null, null, null, null, 1,
            false, false, null, now, now);
        StageTab stale = new StageTab("t-stale", "query_editor", StageTabScope.WORKSPACE,
            "Stale", null, null, null, null, 1,
            false, false, null, staleTime, staleTime);
        repo.upsertMetadata(active, null);
        repo.upsertMetadata(stale, null);

        int archived = repo.archiveStaleSince(now - 50_000);

        assertThat(archived).isEqualTo(1);
        assertThat(repo.findById("t-stale").orElseThrow().archived()).isTrue();
        assertThat(repo.findById("t-active").orElseThrow().archived()).isFalse();
    }

    @Test
    void listFilterAppliesIncludeArchivedAndType() {
        long now = System.currentTimeMillis();

        StageTab wsTab = new StageTab("ws-1", "query_editor", StageTabScope.WORKSPACE,
            "WS Tab", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(wsTab, null);
        repo.setArchived("ws-1", true, now);

        StageTab wsTab2 = new StageTab("ws-2", "chart", StageTabScope.WORKSPACE,
            "WS Chart", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(wsTab2, null);

        // Default filter excludes archived
        StageTabRepository.ListFilter noArchived = new StageTabRepository.ListFilter(
            StageTabScope.WORKSPACE, null, null, null, false, null, null, null, 100);
        List<StageTab> results = repo.list(noArchived);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("ws-2");

        // With archived included
        StageTabRepository.ListFilter withArchived = new StageTabRepository.ListFilter(
            StageTabScope.WORKSPACE, null, null, null, true, null, null, null, 100);
        List<StageTab> allResults = repo.list(withArchived);
        assertThat(allResults).hasSize(2);

        // Filter by type
        StageTabRepository.ListFilter chartOnly = new StageTabRepository.ListFilter(
            StageTabScope.WORKSPACE, "chart", null, null, false, null, null, null, 100);
        List<StageTab> chartResults = repo.list(chartOnly);
        assertThat(chartResults).hasSize(1);
        assertThat(chartResults.get(0).type()).isEqualTo("chart");
    }

    @Test
    void findContentsReturnsRequestedIdsOnly() {
        long now = System.currentTimeMillis();

        StageTab tab1 = new StageTab("fc-1", "query_editor", StageTabScope.WORKSPACE,
            "FC1", null, null, null, null, 1, false, false, null, now, now);
        StageTab tab2 = new StageTab("fc-2", "query_editor", StageTabScope.WORKSPACE,
            "FC2", null, null, null, null, 1, false, false, null, now, now);
        repo.upsertMetadata(tab1, null);
        repo.upsertMetadata(tab2, null);
        repo.upsertPayload("fc-1", "{\"a\":1}", "text-a", 0, now);
        repo.upsertPayload("fc-2", "{\"b\":2}", "text-b", 0, now);

        List<StageTabContent> contents = repo.findContents(List.of("fc-1"));
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).tabId()).isEqualTo("fc-1");
        assertThat(contents.get(0).contentText()).isEqualTo("text-a");
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
                    String[] statements = sql.split(";");
                    for (String stmt : statements) {
                        String trimmed = stmt.trim();
                        if (!trimmed.isEmpty()) {
                            jdbc.execute(trimmed);
                        }
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
