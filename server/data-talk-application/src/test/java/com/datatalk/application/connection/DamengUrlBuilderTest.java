package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamengUrlBuilderTest {

    @Test
    void buildsDamengUrlWithDatabaseName() {
        var conn = damengConn("scott");
        assertThat(JdbcUrlBuilder.build(conn))
            .isEqualTo("jdbc:dm://192.168.1.10:5236");
    }

    @Test
    void buildsDamengUrlWithoutDatabaseName() {
        var conn = damengConn(null);
        assertThat(JdbcUrlBuilder.build(conn))
            .isEqualTo("jdbc:dm://192.168.1.10:5236");
    }

    @Test
    void buildsDamengUrlWithBlankDatabaseName() {
        var conn = damengConn("");
        assertThat(JdbcUrlBuilder.build(conn))
            .isEqualTo("jdbc:dm://192.168.1.10:5236");
    }

    @Test
    void damengUrlHasNoDatabaseSuffix() {
        // Dameng is server-level; URL must not contain /<database>
        var conn = damengConn("SCOTT");
        String url = JdbcUrlBuilder.build(conn);
        assertThat(url).doesNotContain("/SCOTT");
        assertThat(url).doesNotContain("/scott");
        assertThat(url).doesNotMatch(".*:/.*");
    }

    @Test
    void damengUrlDefaultPort() {
        var conn = damengConn(null, 5236);
        assertThat(JdbcUrlBuilder.build(conn))
            .isEqualTo("jdbc:dm://192.168.1.10:5236");
    }

    @Test
    void damengUrlCustomPort() {
        var conn = damengConn(null, 6236);
        assertThat(JdbcUrlBuilder.build(conn))
            .isEqualTo("jdbc:dm://192.168.1.10:6236");
    }

    private static ConnectionRecord damengConn(String databaseName) {
        return damengConn(databaseName, 5236);
    }

    private static ConnectionRecord damengConn(String databaseName, int port) {
        return new ConnectionRecord(
            "c-dm-1", "dameng-test", ConnectionKind.DAMENG,
            "192.168.1.10", port, databaseName,
            "SYSDBA", new byte[0], null,
            System.currentTimeMillis(), 3000,
            null, null, null,
            1, true, null, false
        );
    }
}
