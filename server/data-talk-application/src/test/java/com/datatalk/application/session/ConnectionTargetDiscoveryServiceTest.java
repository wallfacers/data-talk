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
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
              last_test_at BIGINT,
              oracle_service_type TEXT,
              sqlserver_encrypt INTEGER NOT NULL DEFAULT 1,
              sqlserver_trust_server_certificate INTEGER NOT NULL DEFAULT 1,
              sqlserver_instance_name TEXT
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
            "c1", "H2 主库", "h2", "localhost", 0, dbName, "sa", new byte[]{1}, null, 1L, 3000, null, null,
            null, 1, true, null));
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
                "mysql-1", "MySQL", "mysql", "localhost", 3306, "app", "root", new byte[]{1}, null, 2L, 3000, null, null,
            null, 1, true, null));
            Mockito.when(connectionService.decryptPassword("mysql-1")).thenReturn("");

            var result = service.discover("mysql-1");

            assertThat(result.databaseNames()).containsExactly("app");
            assertThat(result.schemaNames()).isEmpty();
            verify(meta, never()).getSchemas();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void discover_treats_mariadb_databases_as_catalogs_not_independent_schemas() throws Exception {
        Connection jdbc = mock(Connection.class);
        var meta = mock(java.sql.DatabaseMetaData.class);
        ResultSet catalogs = mock(ResultSet.class);
        when(jdbc.getMetaData()).thenReturn(meta);
        when(meta.getCatalogs()).thenReturn(catalogs);
        when(catalogs.next()).thenReturn(true, false);
        when(catalogs.getString(1)).thenReturn("mydb");
        Driver driver = new StubDriver("jdbc:mariadb://localhost:3306/", jdbc);
        DriverManager.registerDriver(driver);
        try {
            connectionRepo.insert(new ConnectionRecord(
                "mariadb-1", "MariaDB", "mariadb", "localhost", 3306, "app", "root", new byte[]{1}, null, 2L, 3000, null, null,
            null, 1, true, null));
            Mockito.when(connectionService.decryptPassword("mariadb-1")).thenReturn("");

            var result = service.discover("mariadb-1");

            assertThat(result.databaseNames()).containsExactly("app");
            assertThat(result.schemaNames()).isEmpty();
            verify(meta, never()).getSchemas();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void discover_treats_sqlite_as_file_scoped_without_schema_namespace() {
        connectionRepo.insert(new ConnectionRecord(
            "sqlite-memory",
            "Scratch SQLite",
            "sqlite",
            "",
            0,
            null,
            "",
            new byte[]{1},
            null,
            3L,
            3000,
            null,
            null,
            null, 1, true, null));
        Mockito.when(connectionService.decryptPassword("sqlite-memory")).thenReturn("");

        var result = service.discover("sqlite-memory");

        assertThat(result.databaseNames()).containsExactly(":memory:");
        assertThat(result.schemaNames()).isEmpty();
    }

    @Test
    void discover_throws_when_sqlite_target_cannot_be_opened() throws Exception {
        var invalidTarget = Files.createTempDirectory("sqlite-discovery-target");
        connectionRepo.insert(new ConnectionRecord(
            "sqlite-invalid",
            "Broken SQLite",
            "sqlite",
            "",
            0,
            invalidTarget.toString(),
            "",
            new byte[]{1},
            null,
            4L,
            3000,
            null,
            null,
            null, 1, true, null));
        Mockito.when(connectionService.decryptPassword("sqlite-invalid")).thenReturn("");

        assertThatThrownBy(() -> service.discover("sqlite-invalid"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(invalidTarget.toString());
    }

    @Test
    void discover_oracle_discovers_schemas_and_filters_system_schemas() throws Exception {
        Connection jdbc = mock(Connection.class);
        var meta = mock(java.sql.DatabaseMetaData.class);
        ResultSet schemas = mock(ResultSet.class);
        when(jdbc.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemas);
        // Return schemas including Oracle system schemas that should be filtered
        when(schemas.next()).thenReturn(
            true, true, true, true, true, true, true, true, false);
        when(schemas.getString("TABLE_SCHEM")).thenReturn(
            "HR", "SYS", "SYSTEM", "SCOTT", "MDSYS", "DBSNMP", "XDB", "ORDSYS");
        Driver driver = new StubDriver("jdbc:oracle:thin:@//", jdbc);
        DriverManager.registerDriver(driver);
        try {
            connectionRepo.insert(new ConnectionRecord(
                "oracle-1", "Oracle", "oracle", "host", 1521, "orclpdb", "system",
                new byte[]{1}, null, 10L, 3000, null, null,
                null, 1, true, null));
            Mockito.when(connectionService.decryptPassword("oracle-1")).thenReturn("pw");

            var result = service.discover("oracle-1");

            // Oracle: configured database (orclpdb) is in databaseNames
            assertThat(result.databaseNames()).containsExactly("orclpdb");
            // System schemas (SYS, SYSTEM, MDSYS, DBSNMP, XDB, ORDSYS) are filtered out
            assertThat(result.schemaNames()).containsExactlyInAnyOrder("HR", "SCOTT");
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    void discover_sqlserver_has_independent_schema_namespace_and_filters_sys_schema() throws Exception {
        // SQL Server should have independent schema namespace (hasIndependentSchema = true)
        // and should filter 'sys' schema
        Connection jdbc = mock(Connection.class);
        var meta = mock(java.sql.DatabaseMetaData.class);
        ResultSet schemas = mock(ResultSet.class);
        ResultSet catalogs = mock(ResultSet.class);
        when(jdbc.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemas);
        when(schemas.next()).thenReturn(true, true, true, true, false);
        when(schemas.getString("TABLE_SCHEM")).thenReturn("dbo", "sales", "sys", "guest");
        Driver driver = new StubDriver("jdbc:sqlserver://", jdbc);
        DriverManager.registerDriver(driver);
        try {
            connectionRepo.insert(new ConnectionRecord(
                "sqlserver-1", "SQL Server", "sqlserver", "host", 1433, "mydb", "sa",
                new byte[]{1}, null, 10L, 3000, null, null,
                null, 1, true, null));
            Mockito.when(connectionService.decryptPassword("sqlserver-1")).thenReturn("pw");

            var result = service.discover("sqlserver-1");

            // SQL Server: sys schema is filtered out
            assertThat(result.schemaNames()).containsExactlyInAnyOrder("dbo", "sales", "guest");
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
