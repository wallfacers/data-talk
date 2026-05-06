package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            "sid", 1, true, null);

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
            null, 1, true, null);

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
            "sid", 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, null);

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
            null, 1, true, "SQLEXPRESS");

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
            null, 0, false, null);

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
}
