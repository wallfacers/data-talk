package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompatibilityModeTest {
    @Test
    void wireValueRoundTrip() {
        assertThat(CompatibilityMode.MYSQL.wireValue()).isEqualTo("mysql");
        assertThat(CompatibilityMode.ORACLE.wireValue()).isEqualTo("oracle");
        assertThat(CompatibilityMode.PG.wireValue()).isEqualTo("pg");
    }

    @Test
    void parseAcceptsKnownValues() {
        assertThat(CompatibilityMode.of("mysql")).isEqualTo(CompatibilityMode.MYSQL);
        assertThat(CompatibilityMode.of("oracle")).isEqualTo(CompatibilityMode.ORACLE);
        assertThat(CompatibilityMode.of("pg")).isEqualTo(CompatibilityMode.PG);
    }

    @Test
    void parseRejectsUnknownValues() {
        assertThatThrownBy(() -> CompatibilityMode.of("postgres"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown compatibility mode: postgres");
    }
}
