package com.datatalk.application.connection;

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
}
