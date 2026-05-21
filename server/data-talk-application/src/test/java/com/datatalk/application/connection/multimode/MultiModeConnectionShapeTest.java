package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiModeConnectionShapeTest {
    @Test
    void oceanbaseAcceptsMysqlMode() {
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.MYSQL);
        // no throw
    }

    @Test
    void oceanbaseAcceptsOracleMode() {
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.ORACLE);
        // no throw — Day-1 unsupported but legal at the validate boundary
    }

    @Test
    void oceanbaseRejectsPgMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.PG))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oceanbase requires compatibility_mode in {mysql,oracle}");
    }

    @Test
    void oceanbaseRejectsNullMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("oceanbase", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oceanbase requires compatibility_mode");
    }

    @Test
    void singleModeKindRejectsAnyMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("mysql", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not specify compatibility_mode");
    }

    @Test
    void singleModeKindAcceptsNullMode() {
        MultiModeConnectionShape.validateModeForKind("mysql", null);
        // no throw
    }

    @Test
    void oceanbaseDay1FirstClassOnlyMysql() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.MYSQL)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void singleModeKindIsDay1FirstClassWhenModeIsNull() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("mysql", null)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("mysql", CompatibilityMode.MYSQL)).isFalse();
    }
}
