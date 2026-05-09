package com.datatalk.application.connection;

import com.datatalk.application.connection.multimode.CompatibilityMode;
import com.datatalk.application.connection.multimode.MultiModeConnectionShape;
import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseConnectionRecordValidationTest {
    @Test
    void kingbaseRecordWithPgModeIsValid() {
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.PG);
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.PG)).isTrue();
    }

    @Test
    void kingbaseWithMysqlModeIsRejected() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kingbaseRecordTenantAndClusterAreNull() {
        var c = kingbaseRecord("pg");
        assertThat(c.oceanbaseTenant()).isNull();
        assertThat(c.oceanbaseCluster()).isNull();
    }

    private static ConnectionRecord kingbaseRecord(String mode) {
        return new ConnectionRecord("id", "n", "kingbase", "h", 54321, "db", "kbuser", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false, mode, null, null);
    }
}
