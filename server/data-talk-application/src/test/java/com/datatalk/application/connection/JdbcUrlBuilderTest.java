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
            null
        );

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
            null
        );

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
            null
        );

        assertThat(JdbcUrlBuilder.build(connection))
            .isEqualTo("jdbc:sqlite::memory:");
    }
}
