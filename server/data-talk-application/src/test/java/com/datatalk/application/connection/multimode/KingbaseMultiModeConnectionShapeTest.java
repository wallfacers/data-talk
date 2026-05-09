package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseMultiModeConnectionShapeTest {
    @Test
    void kingbaseAcceptsPgMode() {
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.PG);
    }

    @Test
    void kingbaseAcceptsOracleMode() {
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.ORACLE);
    }

    @Test
    void kingbaseRejectsMysqlMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires compatibility_mode in {pg,oracle}");
    }

    @Test
    void kingbaseRejectsNullMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires compatibility_mode");
    }

    @Test
    void kingbaseDay1FirstClassOnlyPg() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.PG)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void oceanbaseRowStillAccepted_NoV1Regression() {
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.MYSQL);
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.MYSQL)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void singleModeKindStillRejectsAnyMode_NoV1Regression() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("mysql", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not specify compatibility_mode");
    }
}
