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
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void discover_treats_mysql_databases_as_catalogs_not_independent_schemas() throws Exception {
        Connection jdbc = mock(Connection.class);
        var meta = mock(java.sql.DatabaseMetaData.class);
        ResultSet schemas = mock(ResultSet.class);
        when(jdbc.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemas);
        when(schemas.next()).thenReturn(true, false);
        when(schemas.getString("TABLE_SCHEM")).thenReturn("should_not_be_exposed");
        Driver driver = new StubDriver("jdbc:mysql://localhost:3306/", jdbc);
        DriverManager.registerDriver(driver);
        try {
            connectionRepo.insert(new ConnectionRecord(
                "mysql-1", "MySQL", "mysql", "localhost", 3306, "app", "root", new byte[]{1}, null, 2L, 3000, null, null
            ));
            Mockito.when(connectionService.decryptPassword("mysql-1")).thenReturn("");

            var result = service.discover("mysql-1");

            assertThat(result.databaseNames()).containsExactly("app");
            assertThat(result.schemaNames()).isEmpty();
            verify(meta, never()).getSchemas();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    private Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.connection.unknown", Locale.ENGLISH, "Connection not found: {0}");
        source.addMessage("error.connection.unknown", Locale.SIMPLIFIED_CHINESE, "数据源不存在：{0}");
        return new Translator(source);
    }

    private record StubDriver(String prefix, Connection connection) implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return acceptsURL(url) ? connection : null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith(prefix);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
