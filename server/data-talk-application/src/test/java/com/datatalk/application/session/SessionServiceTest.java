package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.application.stage.ActiveSessionRegistry;
import com.datatalk.domain.fileartifact.FileArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SessionServiceTest {

    @TempDir
    Path tmp;

    private SessionRepository repo;
    private SessionService svc;
    private SessionWorkdirService workdirs;
    private FileArtifactRepository fileArtifacts;
    private ActiveSessionRegistry activeSessions;
    private ConnectionRepository connections;
    private OpenCodeGateway gateway;
    private OpenCodeSessionMap sessionMap;
    private SessionBusRegistry buses;
    private Translator translator;
    private Connection conn;
    private DataSource ds;

    @BeforeEach
    void setUp() throws Exception {
        // Use SQLite in-memory with a single connection that stays open
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        conn = sqliteDs.getConnection();
        // Create schema with title_locked column
        conn.createStatement().execute("""
            CREATE TABLE sessions (
              id TEXT PRIMARY KEY,
              connection_id TEXT,
              title TEXT NOT NULL,
              has_ever_sent INTEGER NOT NULL DEFAULT 0,
              opencode_sid TEXT,
              created_at BIGINT NOT NULL,
              updated_at BIGINT NOT NULL,
              title_locked INTEGER NOT NULL DEFAULT 0
            )
            """);
        // Use SingleConnectionDataSource to always return the same connection
        ds = new SingleConnectionDataSource(conn, true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repo = new SessionRepository(jdbc);
        connections = mock(ConnectionRepository.class);
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        buses = mock(SessionBusRegistry.class);
        translator = mock(Translator.class);
        org.mockito.Mockito.when(connections.findById(anyString()))
            .thenAnswer(inv -> java.util.Optional.of(connectionRecord(inv.getArgument(0, String.class))));
        org.mockito.Mockito.when(translator.get("session.default_title")).thenReturn("新会话");
        org.mockito.Mockito.when(translator.get("error.session.title_blank")).thenReturn("title must not be blank");
        org.mockito.Mockito.when(translator.get(org.mockito.ArgumentMatchers.eq("error.session.not_found"), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(inv -> "session not found: " + inv.getArgument(1));
        workdirs = new SessionWorkdirService(
            new SessionWorkdirRoot(tmp, tmp.resolve("opencode")),
            new com.fasterxml.jackson.databind.ObjectMapper());
        fileArtifacts = mock(FileArtifactRepository.class);
        activeSessions = new ActiveSessionRegistry();
        svc = new SessionService(connections, repo, Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC),
            gateway, sessionMap, buses, translator, workdirs, fileArtifacts, activeSessions);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void rename_locksTitleAtomically() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        SessionRecord result = svc.rename("s1", "我的查询");

        assertThat(result.title()).isEqualTo("我的查询");
        assertThat(result.titleLocked()).isTrue();
        SessionRecord persisted = repo.findById("s1").orElseThrow();
        assertThat(persisted.titleLocked()).isTrue();
    }

    @Test
    void rename_rejectsBlankTitle() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        assertThatThrownBy(() -> svc.rename("s1", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must not be blank");
    }

    @Test
    void rename_rejectsNullTitle() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

        assertThatThrownBy(() -> svc.rename("s1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must not be blank");
    }

    @Test
    void rename_throwsWhenSessionNotFound() {
        assertThatThrownBy(() -> svc.rename("nonexistent", "title"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("session not found: nonexistent");
    }

    @Test
    void create_defaultsBlankTitleToNewSession() {
        SessionRecord fromNull = svc.create(null, null).record();
        repo.markHasEverSent(fromNull.id(), 600L);
        SessionRecord fromBlank = svc.create(null, "   ").record();
        repo.markHasEverSent(fromBlank.id(), 601L);
        SessionRecord fromEmpty = svc.create(null, "").record();

        assertThat(fromNull.title()).isEqualTo("新会话");
        assertThat(fromBlank.title()).isEqualTo("新会话");
        assertThat(fromEmpty.title()).isEqualTo("新会话");
    }

    @Test
    void create_preservesExplicitTitle() {
        SessionRecord rec = svc.create(null, "我的会话").record();
        assertThat(rec.title()).isEqualTo("我的会话");
    }

    @Test
    void createCreatesSessionWorkdirAndMeta() throws Exception {
        SessionRecord rec = svc.create("c1", "带目录").record();

        Path dir = workdirs.root().sessionDir(rec.id());
        assertThat(dir).isDirectory();
        assertThat(Files.readString(dir.resolve(".meta.json")))
            .contains("\"sessionId\" : \"" + rec.id() + "\"")
            .contains("\"connectionId\" : \"c1\"");
        assertThat(activeSessions.currentSessionId()).contains(rec.id());
    }

    @Test
    void delete_cascadesToOpenCodeWhenOcSidPresent() {
        repo.upsert(new SessionRecord("s1", "c1", "t", true, "ses_xxx", 100L, 100L, false));
        workdirs.getOrCreate("s1", "c1");
        svc.delete("s1");
        assertThat(repo.findById("s1")).isEmpty();
        assertThat(workdirs.root().sessionDir("s1")).doesNotExist();
        verify(gateway).deleteOpenCodeSession("ses_xxx");
        verify(sessionMap).unbind("s1");
        verify(buses).close("s1");
        verify(fileArtifacts).deleteTransientByForSession("s1");
        verify(fileArtifacts).detachArchivedFromSession("s1");
    }

    @Test
    void delete_skipsOpenCodeCallWhenOcSidNullButStillUnbindsAndClosesBus() {
        repo.upsert(new SessionRecord("s1", "c1", "t", false, null, 100L, 100L, false));
        svc.delete("s1");
        assertThat(repo.findById("s1")).isEmpty();
        verify(gateway, never()).deleteOpenCodeSession(org.mockito.ArgumentMatchers.anyString());
        // unbind + bus close are cheap no-ops if no binding/bus exists; we still call them
        // unconditionally so the cleanup ordering invariant holds for every delete.
        verify(sessionMap).unbind("s1");
        verify(buses).close("s1");
    }

    @Test
    void delete_swallowsOpenCodeGatewayFailure() {
        repo.upsert(new SessionRecord("s1", "c1", "t", true, "ses_xxx", 100L, 100L, false));
        org.mockito.Mockito.doThrow(new RuntimeException("oc down"))
            .when(gateway).deleteOpenCodeSession("ses_xxx");

        // Local delete must still succeed despite gateway failure
        svc.delete("s1");

        assertThat(repo.findById("s1")).isEmpty();
        verify(sessionMap).unbind("s1");
        verify(buses).close("s1");
    }

    @Test
    void deleteAll_removesEverySession_andCleansOpenCodeAndBus() {
        repo.upsert(new SessionRecord("s1", "c1", "t1", true, "oc-1", 100L, 100L, false));
        repo.upsert(new SessionRecord("s2", "c1", "t2", false, null, 110L, 110L, false));
        workdirs.getOrCreate("s1", "c1");
        workdirs.getOrCreate("s2", "c1");

        svc.deleteAll();

        assertThat(repo.listAll()).isEmpty();
        assertThat(workdirs.root().sessionDir("s1")).doesNotExist();
        assertThat(workdirs.root().sessionDir("s2")).doesNotExist();
        verify(sessionMap).unbind("s1");
        verify(sessionMap).unbind("s2");
        verify(buses).close("s1");
        verify(buses).close("s2");
        verify(gateway).deleteOpenCodeSession("oc-1");
        verify(fileArtifacts).deleteTransientByForSession("s1");
        verify(fileArtifacts).deleteTransientByForSession("s2");
        verify(fileArtifacts).detachArchivedFromSession("s1");
        verify(fileArtifacts).detachArchivedFromSession("s2");
    }

    @Test
    void delete_stopsEventSourcesBeforeRemovingRow() {
        // Guards the FK-violation race: any cleanup that could push more events
        // (sessionMap unbind, OpenCode delete, bus close) MUST run before the
        // session row goes away. Otherwise late events INSERT into a dangling
        // session_id and trip the events→sessions FK.
        repo.upsert(new SessionRecord("s1", "c1", "t", true, "ses_xxx", 100L, 100L, false));

        SessionRepository repoSpy = org.mockito.Mockito.spy(repo);
        SessionService spied = new SessionService(connections, repoSpy,
            Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC),
            gateway, sessionMap, buses, translator, workdirs, fileArtifacts, activeSessions);

        spied.delete("s1");

        var order = inOrder(sessionMap, gateway, buses, repoSpy);
        order.verify(sessionMap).unbind("s1");
        order.verify(gateway).deleteOpenCodeSession("ses_xxx");
        order.verify(buses).close("s1");
        order.verify(repoSpy).deleteById("s1");
    }

    @Test
    void create_reusesExistingEmpty() {
        long now0 = 100L;
        repo.upsert(new SessionRecord("existing_empty", "c1", "新会话", false, null, now0, now0, false));

        CreateSessionResult result = svc.create("c2", "任意标题");

        assertThat(result.reusedEmpty()).isTrue();
        assertThat(result.record().id()).isEqualTo("existing_empty");
        assertThat(result.record().connectionId()).isEqualTo("c2");
        assertThat(repo.findById("existing_empty").orElseThrow().connectionId()).isEqualTo("c2");
        assertThat(repo.listAll()).hasSize(1);
    }

    @Test
    void create_whenNoEmpty_createsNew() {
        repo.upsert(new SessionRecord("used", "c1", "sent", true, null, 100L, 100L, false));

        CreateSessionResult result = svc.create("c1", null);

        assertThat(result.reusedEmpty()).isFalse();
        assertThat(result.record().hasEverSent()).isFalse();
        assertThat(result.record().id()).isNotEqualTo("used");
        assertThat(repo.listAll()).hasSize(2);
    }

    @Test
    void create_concurrentInvocations_yieldSingleEmpty() throws Exception {
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                exec.submit(() -> {
                    ready.countDown();
                    try {
                        fire.await();
                        svc.create(null, null);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            fire.countDown();
            done.await();
        } finally {
            exec.shutdown();
        }

        long emptyCount = repo.listAll().stream().filter(r -> !r.hasEverSent()).count();
        assertThat(emptyCount).isEqualTo(1L);
    }

    @Test
    void create_dropsUnknownConnectionIdToNull() {
        org.mockito.Mockito.when(connections.findById("stale-conn")).thenReturn(java.util.Optional.empty());

        SessionRecord record = svc.create("stale-conn", "旧缓存").record();

        assertThat(record.connectionId()).isNull();
    }

    // Two-phase delete tests

    @Test
    void delete_force_false_returns_BlockedByCandidates_when_candidates_exist() {
        repo.upsert(new SessionRecord("ses_a", "c1", "t", false, null, 100L, 100L, false));
        org.mockito.Mockito.when(fileArtifacts.countCandidatesBySession("ses_a")).thenReturn(2);
        org.mockito.Mockito.when(fileArtifacts.findCandidatesBySession("ses_a"))
            .thenReturn(java.util.List.of(anyArtifact("fa_1"), anyArtifact("fa_2")));

        var out = svc.delete("ses_a", false);

        assertThat(out).isInstanceOf(DeleteOutcome.BlockedByCandidates.class);
        var blocked = (DeleteOutcome.BlockedByCandidates) out;
        assertThat(blocked.candidates()).hasSize(2);
        assertThat(blocked.sessionId()).isEqualTo("ses_a");
        // Session should still exist
        assertThat(repo.findById("ses_a")).isPresent();
        verify(sessionMap, never()).unbind(anyString());
        verify(fileArtifacts, never()).deleteTransientByForSession(anyString());
    }

    @Test
    void delete_force_false_returns_Ok_when_no_candidates() {
        repo.upsert(new SessionRecord("ses_a", "c1", "t", false, null, 100L, 100L, false));
        workdirs.getOrCreate("ses_a", "c1");
        org.mockito.Mockito.when(fileArtifacts.countCandidatesBySession("ses_a")).thenReturn(0);

        var out = svc.delete("ses_a", false);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        assertThat(repo.findById("ses_a")).isEmpty();
        verify(fileArtifacts).deleteTransientByForSession("ses_a");
        verify(fileArtifacts).detachArchivedFromSession("ses_a");
    }

    @Test
    void delete_force_true_proceeds_even_with_candidates() {
        repo.upsert(new SessionRecord("ses_a", "c1", "t", false, null, 100L, 100L, false));
        workdirs.getOrCreate("ses_a", "c1");
        // countCandidatesBySession not called when force=true (verify lenient)

        var out = svc.delete("ses_a", true);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        assertThat(repo.findById("ses_a")).isEmpty();
        verify(fileArtifacts, never()).countCandidatesBySession(anyString());
    }

    @Test
    void delete_returns_NotFound_for_unknown_id() {
        var out = svc.delete("ses_nope", false);
        assertThat(out).isInstanceOf(DeleteOutcome.NotFound.class);
        var notFound = (DeleteOutcome.NotFound) out;
        assertThat(notFound.id()).isEqualTo("ses_nope");
    }

    @Test
    void deprecated_delete_shim_throws_NotFound() {
        assertThatThrownBy(() -> svc.delete("nonexistent"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("session not found: nonexistent");
    }

    private static FileArtifact anyArtifact(String id) {
        return new FileArtifact(
            id,
            com.datatalk.domain.fileartifact.FileArtifactScope.SESSION,
            com.datatalk.domain.fileartifact.FileArtifactStatus.CANDIDATE,
            com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
            "ses_test",
            null,
            "test.txt",
            "/tmp/test.txt",
            100L,
            "text/plain",
            null,
            null,
            java.time.Instant.ofEpochMilli(100L),
            java.time.Instant.ofEpochMilli(100L),
            null,
            null, false);
    }

    private static ConnectionRecord connectionRecord(String id) {
        return new ConnectionRecord(id, "seed-" + id, "mysql", "h", 3306,
            null, "u", new byte[] {0}, null, 0L, 3000, null, null,
            null, 1, true, null, false);
    }
}
