package com.datatalk.application.session;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTargetDiscoveryServiceTest {

    private ConnectionRepository connectionRepo;
    private ConnectionService connectionService;
    private ConnectionTargetDiscoveryService service;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqliteDs = new SQLiteDataSource();
        sqliteDs.setUrl("jdbc:sqlite::memory:");
        Connection metaConn = sqliteDs.getConnection();
        metaConn.createStatement().execute("""
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
              last_test_at BIGINT
            )
            """);
        connectionRepo = new ConnectionRepository(new JdbcTemplate(new SingleConnectionDataSource(metaConn, true)));
        connectionService = Mockito.mock(ConnectionService.class);
        service = new ConnectionTargetDiscoveryService(connectionRepo, connectionService, translator());

        String dbName = "mem:ctx_discovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            c.createStatement().execute("create schema if not exists analytics");
        }

        connectionRepo.insert(new ConnectionRecord(
            "c1", "H2 主库", "h2", "localhost", 0, dbName, "sa", new byte[]{1}, null, 1L, 3000, null, null
        ));
        Mockito.when(connectionService.decryptPassword("c1")).thenReturn("");
    }

    @Test
    void discover_returns_configured_database_and_detected_schemas() {
        var result = service.discover("c1");

        assertThat(result.connectionId()).isEqualTo("c1");
        assertThat(result.databaseNames()).isNotEmpty();
        assertThat(result.schemaNames()).contains("PUBLIC", "analytics");
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.connection.unknown", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("error.connection.unknown", Locale.SIMPLIFIED_CHINESE, "数据源不存在：{0}");
        return new Translator(source);
    }
}
