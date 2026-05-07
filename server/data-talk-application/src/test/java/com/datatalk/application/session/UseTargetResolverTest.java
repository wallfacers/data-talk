package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UseTargetResolverTest {

    private SessionRepository sessionRepo;
    private ConnectionRepository connectionRepo;
    private SessionDataContextRepository contextRepo;
    private ConnectionTargetDiscoveryService discovery;
    private UseTargetResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        Connection conn = sqliteDs.getConnection();
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
              read_only INTEGER NOT NULL DEFAULT 0
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
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
        sessionRepo = new SessionRepository(jdbc);
        connectionRepo = new ConnectionRepository(jdbc);
        contextRepo = new SessionDataContextRepository(jdbc);
        discovery = Mockito.mock(ConnectionTargetDiscoveryService.class);
        resolver = new UseTargetResolver(sessionRepo, connectionRepo, contextRepo, discovery, translator(),
            Clock.fixed(Instant.ofEpochMilli(1_710_000_100_000L), ZoneOffset.UTC));

        connectionRepo.insert(new ConnectionRecord("c1", "主库", "h2", "localhost", 0, "app_db", "sa", new byte[]{1}, null, 1L, 3000, null, null, null, 1, true, null, false));
        connectionRepo.insert(new ConnectionRecord("c2", "analytics", "h2", "localhost", 0, "analytics_db", "sa", new byte[]{1}, null, 1L, 3000, null, null, null, 1, true, null, false));
        sessionRepo.upsert(new SessionRecord("s1", "c1", "ctx", false, null, 1L, 1L, false));
        contextRepo.upsert(new SessionDataContextRecord("s1", "c1", "主库", null, null, null, 1L));
    }

    @Test
    void resolve_prefers_current_connection_targets_before_global_connection_name() {
        Mockito.when(discovery.discover("c1")).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            "c1", "主库", java.util.Set.of("app_db"), java.util.Set.of("analytics")
        ));

        var result = resolver.resolve("s1", "analytics");

        assertThat(result.status()).isEqualTo("matched");
        assertThat(result.context().connectionId()).isEqualTo("c1");
        assertThat(result.context().schemaName()).isEqualTo("analytics");
        assertThat(result.context().selectedLevel()).isEqualTo("schema");
        assertThat(result.matchedTarget().level()).isEqualTo("schema");
    }

    @Test
    void resolve_falls_back_to_global_connection_name_match() {
        Mockito.when(discovery.discover("c1")).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            "c1", "主库", java.util.Set.of("app_db"), java.util.Set.of("public")
        ));

        var result = resolver.resolve("s1", "analytics");

        assertThat(result.status()).isEqualTo("matched");
        assertThat(result.context().connectionId()).isEqualTo("c2");
        assertThat(result.context().connectionNameSnapshot()).isEqualTo("analytics");
        assertThat(result.context().databaseName()).isNull();
        assertThat(result.context().schemaName()).isNull();
        assertThat(result.context().selectedLevel()).isEqualTo("connection");
        assertThat(result.matchedTarget().level()).isEqualTo("connection");
    }

    @Test
    void resolve_returns_not_found_with_suggestions() {
        Mockito.when(discovery.discover("c1")).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            "c1", "主库", java.util.Set.of("app_db"), java.util.Set.of("public", "sales", "support")
        ));

        var result = resolver.resolve("s1", "sup");

        assertThat(result.status()).isEqualTo("not_found");
        assertThat(result.suggestions()).extracting(UseTargetResolver.TargetOption::label)
            .contains("support");
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.target.required", Locale.ENGLISH, "Target is required");
        source.addMessage("error.target.required", Locale.SIMPLIFIED_CHINESE, "必须提供目标");
        source.addMessage("error.session.not_found", Locale.ENGLISH, "Session not found: {0}");
        source.addMessage("error.session.not_found", Locale.SIMPLIFIED_CHINESE, "会话不存在：{0}");
        source.addMessage("error.use_target.not_found", Locale.ENGLISH, "No database, schema, or connection matched ''{0}''");
        source.addMessage("error.use_target.not_found", Locale.SIMPLIFIED_CHINESE, "没有匹配到数据库、schema 或连接：{0}");
        return new Translator(source);
    }
}
