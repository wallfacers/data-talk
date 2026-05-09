package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionDataContextServiceTest {

    private Connection conn;
    private DataSource ds;
    private SessionRepository sessionRepo;
    private ConnectionRepository connectionRepo;
    private SessionDataContextRepository contextRepo;
    private SessionDataContextService service;
    private ConnectionTargetDiscoveryService discovery;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        conn = sqliteDs.getConnection();
        conn.createStatement().execute("PRAGMA foreign_keys=ON");
        conn.createStatement().execute("""
            CREATE TABLE connections (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              kind TEXT NOT NULL,
              host TEXT NOT NULL,
              port INTEGER NOT NULL,
              database_name TEXT,
              username TEXT NOT NULL,
              password_enc BLOB NOT NULL,
              schema_digest TEXT,
              created_at BIGINT NOT NULL,
              connect_timeout INTEGER NOT NULL DEFAULT 3000,
              last_test_status TEXT,
              last_test_at BIGINT,
              oracle_service_type TEXT,
              sqlserver_encrypt INTEGER DEFAULT 1,
              sqlserver_trust_server_certificate INTEGER DEFAULT 1,
              sqlserver_instance_name TEXT,
              read_only INTEGER NOT NULL DEFAULT 0,
              compatibility_mode TEXT,
              oceanbase_tenant TEXT,
              oceanbase_cluster TEXT
            )
            """);
        conn.createStatement().execute("""
            CREATE TABLE sessions (
              id TEXT PRIMARY KEY,
              connection_id TEXT REFERENCES connections(id),
              title TEXT NOT NULL,
              has_ever_sent INTEGER NOT NULL DEFAULT 0,
              opencode_sid TEXT,
              created_at BIGINT NOT NULL,
              updated_at BIGINT NOT NULL,
              title_locked INTEGER NOT NULL DEFAULT 0
            )
            """);
        conn.createStatement().execute("""
            CREATE TABLE session_data_contexts (
              session_id TEXT PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
              connection_id TEXT REFERENCES connections(id),
              connection_name_snapshot TEXT,
              database_name TEXT,
              schema_name TEXT,
              selected_level TEXT,
              updated_at BIGINT NOT NULL
            )
            """);
        ds = new SingleConnectionDataSource(conn, true);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        sessionRepo = new SessionRepository(jdbc);
        connectionRepo = new ConnectionRepository(jdbc);
        contextRepo = new SessionDataContextRepository(jdbc);
        discovery = org.mockito.Mockito.mock(ConnectionTargetDiscoveryService.class);
        service = new SessionDataContextService(
            sessionRepo,
            connectionRepo,
            contextRepo,
            discovery,
            translator(),
            Clock.fixed(Instant.ofEpochMilli(1_710_000_000_000L), ZoneOffset.UTC)
        );

        connectionRepo.insert(new ConnectionRecord(
            "c1", "主库", "postgres", "localhost", 5432, "app_db", "u",
            new byte[]{1}, null, 100L, 3000, null, null,
            null, 1, true, null, false, null, null, null));
        sessionRepo.upsert(new SessionRecord("s1", "c1", "测试会话", false, null, 100L, 100L, false));
    }

    @AfterEach
    void tearDown() throws Exception {
        LocaleContextHolder.resetLocaleContext();
        if (conn != null) conn.close();
    }

    @Test
    void get_inherits_session_connection_when_context_missing() {
        SessionDataContextRecord record = service.get("s1");

        assertThat(record.sessionId()).isEqualTo("s1");
        assertThat(record.connectionId()).isEqualTo("c1");
        assertThat(record.connectionNameSnapshot()).isEqualTo("主库");
        assertThat(record.databaseName()).isNull();
        assertThat(record.schemaName()).isNull();
        assertThat(record.selectedLevel()).isEqualTo("connection");
    }

    @Test
    void get_returns_empty_context_when_missing_and_session_has_no_connection() {
        sessionRepo.upsert(new SessionRecord("s-null", null, "无连接会话", false, null, 101L, 101L, false));

        SessionDataContextRecord record = service.get("s-null");

        assertThat(record.sessionId()).isEqualTo("s-null");
        assertThat(record.connectionId()).isNull();
        assertThat(record.connectionNameSnapshot()).isNull();
        assertThat(record.databaseName()).isNull();
        assertThat(record.schemaName()).isNull();
        assertThat(record.selectedLevel()).isNull();
    }

    @Test
    void set_upserts_context_and_fills_connection_name_snapshot() {
        SessionDataContextRecord saved = service.set("s1", new SessionDataContextUpdateRequest(
            "c1", "analytics", "public", "schema"
        ));

        assertThat(saved.connectionId()).isEqualTo("c1");
        assertThat(saved.connectionNameSnapshot()).isEqualTo("主库");
        assertThat(saved.databaseName()).isEqualTo("analytics");
        assertThat(saved.schemaName()).isEqualTo("public");
        assertThat(saved.selectedLevel()).isEqualTo("schema");

        SessionDataContextRecord persisted = contextRepo.findBySessionId("s1").orElseThrow();
        assertThat(persisted.connectionNameSnapshot()).isEqualTo("主库");
        assertThat(persisted.databaseName()).isEqualTo("analytics");
        assertThat(persisted.schemaName()).isEqualTo("public");
        assertThat(persisted.selectedLevel()).isEqualTo("schema");
    }

    @Test
    void set_with_null_connection_clears_database_schema_and_selected_level() {
        contextRepo.upsert(new SessionDataContextRecord(
            "s1", "c1", "主库", "analytics", "public", "schema", 111L
        ));

        SessionDataContextRecord cleared = service.set("s1", new SessionDataContextUpdateRequest(
            null, "ignored_db", "ignored_schema", "database"
        ));

        assertThat(cleared.connectionId()).isNull();
        assertThat(cleared.connectionNameSnapshot()).isNull();
        assertThat(cleared.databaseName()).isNull();
        assertThat(cleared.schemaName()).isNull();
        assertThat(cleared.selectedLevel()).isNull();
    }

    @Test
    void set_throws_when_session_missing() {
        assertThatThrownBy(() -> service.set("missing", new SessionDataContextUpdateRequest(
            "c1", null, null, "connection"
        )))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void set_rejects_null_body_with_localized_message() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        assertThatThrownBy(() -> service.set("s1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("请求体不能为空");
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.request_body_required", Locale.ENGLISH, "Request body is required");
        source.addMessage("error.request_body_required", Locale.SIMPLIFIED_CHINESE, "请求体不能为空");
        source.addMessage("error.session.not_found", Locale.ENGLISH, "Session not found: {0}");
        source.addMessage("error.session.not_found", Locale.SIMPLIFIED_CHINESE, "会话不存在：{0}");
        source.addMessage("error.connection.unknown", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("error.connection.unknown", Locale.SIMPLIFIED_CHINESE, "数据源不存在：{0}");
        return new Translator(source);
    }
}
