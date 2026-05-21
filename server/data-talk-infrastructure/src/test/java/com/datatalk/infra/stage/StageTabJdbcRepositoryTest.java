package com.datatalk.infra.stage;

import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.application.stage.StageTabConcurrencyException;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTabJdbcRepositoryTest {
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
        createSchema(jdbc);
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
        StageTab tab = new StageTab("t1", "query_editor", "Test Tab", null, null, null, null, 1,
            false, false, null, now, now);

        int version = repo.upsertMetadata(tab, null);

        assertThat(version).isEqualTo(1);
        Optional<StageTab> loaded = repo.findById("t1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().title()).isEqualTo("Test Tab");
        assertThat(loaded.get().originSessionId()).isNull();
    }

    @Test
    void upsertPayloadBumpsPayloadVersionAndSyncsFts() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t2", "query_editor", "Payload Tab", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);

        repo.upsertPayload("t2", "{\"sql\":\"SELECT 1\"}", "SELECT 1 FROM users", null, now);

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
        StageTab tab = new StageTab("t3", "query_editor", "Concurrency Tab", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);
        repo.upsertPayload("t3", "{\"v\":1}", "text v1", null, now);

        // Try to update with wrong expected version
        assertThatThrownBy(() -> repo.upsertPayload("t3", "{\"v\":2}", "text v2", 0, now))
            .isInstanceOf(StageTabConcurrencyException.class);
    }

    @Test
    void sessionDeleteClearsOriginSessionIdAndKeepsTab() {
        long now = System.currentTimeMillis();
        // Seed session
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES('sess-cascade', NULL, 'cascade-test', 0, NULL, ?, ?, 0)
            """, now, now);

        StageTab tab = new StageTab("t-cascade", "query_editor", "Session Tab", null, null, null, "sess-cascade", 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab, null);

        assertThat(repo.findById("t-cascade")).isPresent();

        // Delete the session — tab should remain with a cleared soft label
        jdbc.update("DELETE FROM sessions WHERE id = 'sess-cascade'");
        Optional<StageTab> loaded = repo.findById("t-cascade");
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().originSessionId()).isNull();
    }

    @Test
    void archiveStaleSinceFlipsExpiredTabs() {
        long now = System.currentTimeMillis();
        long staleTime = now - 100_000;

        StageTab active = new StageTab("t-active", "query_editor", "Active", null, null, null, null, 1,
            false, false, null, now, now);
        StageTab stale = new StageTab("t-stale", "query_editor", "Stale", null, null, null, null, 1,
            false, false, null, staleTime, staleTime);
        repo.upsertMetadata(active, null);
        repo.upsertMetadata(stale, null);

        int archived = repo.archiveStaleSince(now - 50_000);

        assertThat(archived).isEqualTo(1);
        assertThat(repo.findById("t-stale").orElseThrow().archived()).isTrue();
        assertThat(repo.findById("t-active").orElseThrow().archived()).isFalse();
    }

    @Test
    void listFilterAppliesIncludeArchivedTypeAndConnection() {
        long now = System.currentTimeMillis();

        StageTab archived = new StageTab("ws-1", "query_editor", "Archived", "conn-a", null, null, null, 1,
            false, true, now, now, now);
        StageTab activeChart = new StageTab("ws-2", "chart", "Chart", "conn-b", null, null, null, 1,
            false, false, null, now, now);
        StageTab activeQuery = new StageTab("ws-3", "query_editor", "Query", "conn-a", null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(archived, null);
        repo.upsertMetadata(activeChart, null);
        repo.upsertMetadata(activeQuery, null);

        StageTabRepository.ListFilter noArchived = new StageTabRepository.ListFilter(
            null, null, null, false, null, null, null, 100);
        assertThat(repo.list(noArchived)).extracting(StageTab::id)
            .containsExactlyInAnyOrder("ws-2", "ws-3");

        StageTabRepository.ListFilter withArchived = new StageTabRepository.ListFilter(
            null, null, null, true, null, null, null, 100);
        assertThat(repo.list(withArchived)).extracting(StageTab::id)
            .containsExactlyInAnyOrder("ws-1", "ws-2", "ws-3");

        StageTabRepository.ListFilter chartOnly = new StageTabRepository.ListFilter(
            "chart", null, null, false, null, null, null, 100);
        assertThat(repo.list(chartOnly)).extracting(StageTab::id)
            .containsExactly("ws-2");

        StageTabRepository.ListFilter connectionOnly = new StageTabRepository.ListFilter(
            null, "conn-a", null, true, null, null, null, 100);
        assertThat(repo.list(connectionOnly)).extracting(StageTab::id)
            .containsExactlyInAnyOrder("ws-1", "ws-3");
    }

    @Test
    void findContentsReturnsRequestedIdsOnly() {
        long now = System.currentTimeMillis();

        StageTab tab1 = new StageTab("fc-1", "query_editor", "FC1", null, null, null, null, 1,
            false, false, null, now, now);
        StageTab tab2 = new StageTab("fc-2", "query_editor", "FC2", null, null, null, null, 1,
            false, false, null, now, now);
        repo.upsertMetadata(tab1, null);
        repo.upsertMetadata(tab2, null);
        repo.upsertPayload("fc-1", "{\"a\":1}", "text-a", null, now);
        repo.upsertPayload("fc-2", "{\"b\":2}", "text-b", null, now);

        List<StageTabContent> contents = repo.findContents(List.of("fc-1"));
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).tabId()).isEqualTo("fc-1");
        assertThat(contents.get(0).contentText()).isEqualTo("text-a");
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("PRAGMA foreign_keys = ON");
        jdbc.execute("""
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                connection_id TEXT,
                title TEXT NOT NULL,
                has_ever_sent INTEGER NOT NULL DEFAULT 0,
                opencode_sid TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                title_locked INTEGER NOT NULL DEFAULT 0
            )
            """);
        jdbc.execute("""
            CREATE TABLE stage_tabs (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                connection_id TEXT,
                database_name TEXT,
                schema_name TEXT,
                origin_session_id TEXT,
                payload_version INTEGER NOT NULL DEFAULT 1,
                pinned INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0,
                archived_at INTEGER,
                created_at INTEGER NOT NULL,
                last_touched_at INTEGER NOT NULL,
                FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE SET NULL
            )
            """);
        jdbc.execute("""
            CREATE INDEX idx_stage_tabs_active
                ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0
            """);
        jdbc.execute("""
            CREATE INDEX idx_stage_tabs_type
                ON stage_tabs(type, archived)
            """);
        jdbc.execute("""
            CREATE INDEX idx_stage_tabs_origin
                ON stage_tabs(origin_session_id)
            """);
        jdbc.execute("""
            CREATE TABLE stage_tab_payload (
                tab_id TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                content_text TEXT NOT NULL,
                content_version INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (tab_id) REFERENCES stage_tabs(id) ON DELETE CASCADE
            )
            """);
        jdbc.execute("""
            CREATE VIRTUAL TABLE stage_tab_index USING fts5(
                title,
                content,
                type UNINDEXED,
                archived UNINDEXED,
                tokenize = 'trigram'
            )
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
                INSERT INTO stage_tab_index(rowid, title, content, type, archived)
                    VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
            END
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
                UPDATE stage_tab_index
                    SET title = NEW.title, archived = NEW.archived
                    WHERE rowid = NEW.rowid;
            END
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
                DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
            END
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
                UPDATE stage_tab_index
                    SET content = NEW.content_text
                    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
            END
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
                UPDATE stage_tab_index
                    SET content = NEW.content_text
                    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
            END
            """);
        jdbc.execute("""
            CREATE TRIGGER stage_tab_payload_ad AFTER DELETE ON stage_tab_payload BEGIN
                UPDATE stage_tab_index
                    SET content = ''
                    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = OLD.tab_id);
            END
            """);
    }
}
