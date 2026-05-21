package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that Dameng ConnectionRecord rows store NULL for multi-mode fields
 * (compatibilityMode, oceanbaseTenant, oceanbaseCluster) as required by the
 * single-mode spec.
 */
class DamengConnectionRecordValidationTest {

    @Test
    void compatibilityModeIsNullForDameng() {
        var conn = damengRecord("scott");
        assertThat(conn.compatibilityMode()).isNull();
    }

    @Test
    void oceanbaseFieldsAreNullForDameng() {
        var conn = damengRecord("scott");
        assertThat(conn.oceanbaseTenant()).isNull();
        assertThat(conn.oceanbaseCluster()).isNull();
    }

    @Test
    void kindIsDameng() {
        var conn = damengRecord("scott");
        assertThat(conn.kind()).isEqualTo(ConnectionKind.DAMENG);
    }

    @Test
    void databaseNameCanBeSchema() {
        var conn = damengRecord("SCOTT");
        assertThat(conn.databaseName()).isEqualTo("SCOTT");
    }

    @Test
    void databaseNameCanBeNull() {
        var conn = damengRecord(null);
        assertThat(conn.databaseName()).isNull();
    }

    private static ConnectionRecord damengRecord(String schema) {
        return new ConnectionRecord(
            "c-dm-1", "dameng-test", ConnectionKind.DAMENG,
            "192.168.1.10", 5236, schema,
            "SYSDBA", new byte[0], null,
            System.currentTimeMillis(), 3000,
            null, null, null,
            1, true, null, false, null, null, null
        );
    }
}
