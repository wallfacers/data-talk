package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUrlBuilderTest {

    @Test
    void buildsMysqlUrlWithoutLiteralNullDatabaseName() {
        var connection = new DbConnection(
            "c1",
            "mysql-root",
            DbType.MYSQL,
            "192.168.1.3",
            3306,
            null,
            "root",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:mysql://192.168.1.3:3306/");
    }

    @Test
    void defaultsPostgresqlDatabaseNameWhenAbsent() {
        var connection = new DbConnection(
            "c2",
            "pg-root",
            DbType.POSTGRESQL,
            "127.0.0.1",
            5432,
            null,
            "postgres",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:postgresql://127.0.0.1:5432/postgres");
    }

    @Test
    void sqliteFilePathBuildsJdbcSqliteUrl() {
        var connection = new ConnectionRecord(
            "sqlite-file",
            "Local SQLite",
            ConnectionKind.SQLITE,
            "",
            0,
            "/tmp/app.db",
            "",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlite:/tmp/app.db");
    }

    @Test
    void sqliteMemoryBuildsExactMemoryUrl() {
        var connection = new ConnectionRecord(
            "sqlite-memory",
            "Memory SQLite",
            ConnectionKind.SQLITE,
            "",
            0,
            ":memory:",
            "",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlite::memory:");
    }

    @Test
    void sqliteNullDatabaseUsesMemoryUrl() {
        var connection = new ConnectionRecord(
            "sqlite-null",
            "Default SQLite",
            ConnectionKind.SQLITE,
            "",
            0,
            null,
            "",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlite::memory:");
    }

    @Test
    void sqliteBlankDatabaseUsesMemoryUrl() {
        var connection = new ConnectionRecord(
            "sqlite-blank",
            "Blank SQLite",
            ConnectionKind.SQLITE,
            "",
            0,
            "   ",
            "",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlite::memory:");
    }

    @Test
    void mariadb_with_database_builds_jdbc_mariadb_url() {
        var connection = new ConnectionRecord(
            "mariadb-1",
            "MariaDB Test",
            ConnectionKind.MARIADB,
            "host",
            3306,
            "testdb",
            "root",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:mariadb://host:3306/testdb");
    }

    @Test
    void mariadb_null_database_builds_url_without_db() {
        var connection = new ConnectionRecord(
            "mariadb-2",
            "MariaDB No DB",
            ConnectionKind.MARIADB,
            "host",
            3306,
            null,
            "root",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:mariadb://host:3306/");
    }

    @Test
    void mariadb_custom_port() {
        var connection = new ConnectionRecord(
            "mariadb-3",
            "MariaDB Custom Port",
            ConnectionKind.MARIADB,
            "host",
            3307,
            "mydb",
            "root",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:mariadb://host:3307/mydb");
    }

    @Test
    void oracle_service_name_builds_thin_url() {
        var connection = new ConnectionRecord(
            "oracle-1",
            "Oracle Service Name",
            ConnectionKind.ORACLE,
            "host",
            1521,
            "orclpdb",
            "system",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@//host:1521/orclpdb");
    }

    @Test
    void oracle_sid_mode_builds_sid_url() {
        var connection = new ConnectionRecord(
            "oracle-2",
            "Oracle SID",
            ConnectionKind.ORACLE,
            "host",
            1521,
            "ORCL",
            "system",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            "sid", 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@host:1521:ORCL");
    }

    @Test
    void oracle_null_database_uses_default() {
        var connection = new ConnectionRecord(
            "oracle-3",
            "Oracle Default DB",
            ConnectionKind.ORACLE,
            "host",
            1521,
            null,
            "system",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@//host:1521/ORCL");
    }

    @Test
    void oracle_sid_null_database_uses_default() {
        var connection = new ConnectionRecord(
            "oracle-4",
            "Oracle SID Default DB",
            ConnectionKind.ORACLE,
            "host",
            1521,
            null,
            "system",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            "sid", 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@host:1521:ORCL");
    }

    @Test
    void oracle_dbConnection_builds_service_name_url() {
        var connection = new DbConnection(
            "c1",
            "oracle-root",
            DbType.ORACLE,
            "192.168.1.5",
            1521,
            "freepdb1",
            "system",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@//192.168.1.5:1521/freepdb1");
    }

    @Test
    void oracle_dbConnection_null_database_uses_default() {
        var connection = new DbConnection(
            "c2",
            "oracle-default",
            DbType.ORACLE,
            "localhost",
            1521,
            null,
            "system",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:oracle:thin:@//localhost:1521/ORCL");
    }

    // --- SQL Server URL building ---

    @Test
    void sqlserver_with_database_builds_url() {
        var connection = new ConnectionRecord(
            "sqlserver-1",
            "SQL Server Test",
            ConnectionKind.SQLSERVER,
            "db.example.com",
            1433,
            "mydb",
            "sa",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://db.example.com:1433;databaseName=mydb;encrypt=true;trustServerCertificate=true");
    }

    @Test
    void sqlserver_null_database_omits_databaseName() {
        var connection = new ConnectionRecord(
            "sqlserver-2",
            "SQL Server No DB",
            ConnectionKind.SQLSERVER,
            "db.example.com",
            1433,
            null,
            "sa",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://db.example.com:1433;encrypt=true;trustServerCertificate=true");
    }

    @Test
    void sqlserver_with_instance_name_builds_url() {
        var connection = new ConnectionRecord(
            "sqlserver-3",
            "SQL Server Instance",
            ConnectionKind.SQLSERVER,
            "db.example.com",
            1433,
            "mydb",
            "sa",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, "SQLEXPRESS", false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://db.example.com\\SQLEXPRESS:1433;databaseName=mydb;encrypt=true;trustServerCertificate=true");
    }

    @Test
    void sqlserver_encrypt_disabled_builds_url() {
        var connection = new ConnectionRecord(
            "sqlserver-4",
            "SQL Server No Encrypt",
            ConnectionKind.SQLSERVER,
            "db.example.com",
            1433,
            "mydb",
            "sa",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 0, false, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://db.example.com:1433;databaseName=mydb;encrypt=false;trustServerCertificate=false");
    }

    @Test
    void sqlserver_dbConnection_builds_url() {
        var connection = new DbConnection(
            "c1",
            "sqlserver-root",
            DbType.SQLSERVER,
            "192.168.1.10",
            1433,
            "testdb",
            "sa",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://192.168.1.10:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true");
    }

    @Test
    void sqlserver_dbConnection_null_database_builds_url() {
        var connection = new DbConnection(
            "c2",
            "sqlserver-default",
            DbType.SQLSERVER,
            "localhost",
            1433,
            null,
            "sa",
            "secret",
            Instant.parse("2026-04-21T00:00:00Z")
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlserver://localhost:1433;encrypt=true;trustServerCertificate=true");
    }

    // --- DuckDB URL building ---

    @Test
    void duckdb_in_memory_builds_named_memory_url() {
        var connection = new ConnectionRecord(
            "conn-duckdb-001",
            "In-Memory DuckDB",
            ConnectionKind.DUCKDB,
            "",
            0,
            ":memory:",
            "",
            new byte[]{},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:duckdb::memory:dt_mem_conn-duckdb-001");
    }

    @Test
    void duckdb_file_mode_builds_url() {
        var connection = new ConnectionRecord(
            "duckdb-file",
            "File DuckDB",
            ConnectionKind.DUCKDB,
            "",
            0,
            "/home/user/.datatalk/duckdb/mydb.db",
            "",
            new byte[]{},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:duckdb:/home/user/.datatalk/duckdb/mydb.db");
    }

    @Test
    void duckdb_file_read_only_builds_url_with_readonly_param() {
        var connection = new ConnectionRecord(
            "duckdb-ro",
            "Read-Only DuckDB",
            ConnectionKind.DUCKDB,
            "",
            0,
            "/home/user/.datatalk/duckdb/mydb.db",
            "",
            new byte[]{},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, true);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:duckdb:/home/user/.datatalk/duckdb/mydb.db?readonly=true");
    }

    // --- ClickHouse URL building ---

    @Test
    void clickhouse_with_database_builds_jdbc_url() {
        var connection = new ConnectionRecord(
            "clickhouse-1",
            "ClickHouse Test",
            ConnectionKind.CLICKHOUSE,
            "host",
            8123,
            "mydb",
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://host:8123/mydb");
    }

    @Test
    void clickhouse_null_database_builds_url_without_db() {
        var connection = new ConnectionRecord(
            "clickhouse-2",
            "ClickHouse No DB",
            ConnectionKind.CLICKHOUSE,
            "host",
            8123,
            null,
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://host:8123/");
    }

    @Test
    void clickhouse_custom_port_builds_url() {
        var connection = new ConnectionRecord(
            "clickhouse-3",
            "ClickHouse Custom Port",
            ConnectionKind.CLICKHOUSE,
            "host",
            9440,
            "analytics",
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://host:9440/analytics");
    }

    @Test
    void clickhouse_default_port_8123() {
        var connection = new ConnectionRecord(
            "clickhouse-4",
            "ClickHouse Default Port",
            ConnectionKind.CLICKHOUSE,
            "localhost",
            8123,
            "testdb",
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://localhost:8123/testdb");
    }

    @Test
    void clickhouse_unsupported_kind_throws() {
        // Ensure that an unsupported kind still throws even with other kinds present
        var connection = new ConnectionRecord(
            "unknown-1",
            "Unknown Kind",
            "unknown_kind",
            "host",
            1234,
            "db",
            "user",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThatThrownBy(() -> JdbcUrlBuilder.build(connection))
            .isInstanceOf(com.datatalk.domain.error.DataTalkException.class)
            .hasMessageContaining("unsupported database kind");
    }

    @Test
    void clickhouse_https_port_appends_ssl() {
        var connection = new ConnectionRecord(
            "clickhouse-ssl",
            "ClickHouse HTTPS",
            ConnectionKind.CLICKHOUSE,
            "host",
            8443,
            "mydb",
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://host:8443/mydb?ssl=true");
    }

    @Test
    void clickhouse_https_port_null_database_appends_ssl() {
        var connection = new ConnectionRecord(
            "clickhouse-ssl-nodb",
            "ClickHouse HTTPS No DB",
            ConnectionKind.CLICKHOUSE,
            "host",
            8443,
            null,
            "default",
            new byte[]{1},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, false);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:clickhouse://host:8443/?ssl=true");
    }

    @Test
    void duckdb_in_memory_read_only_builds_url_with_readonly_param() {
        var connection = new ConnectionRecord(
            "duckdb-mem-ro",
            "Read-Only In-Memory DuckDB",
            ConnectionKind.DUCKDB,
            "",
            0,
            ":memory:",
            "",
            new byte[]{},
            null,
            1L,
            3000,
            null,
            null,
            null, 1, true, null, true);

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:duckdb::memory:dt_mem_duckdb-mem-ro?readonly=true");
    }
}
