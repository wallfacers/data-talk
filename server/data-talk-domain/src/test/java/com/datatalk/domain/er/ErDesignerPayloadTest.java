package com.datatalk.domain.er;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErDesignerPayloadTest {

    @Test
    void dialectResolvesSupportedConnectionKinds() {
        var payload = new ErDesignerPayload("postgres", "c1", "db", "public", List.of(), List.of());

        assertThat(payload.resolveDialect()).isEqualTo(Dialect.POSTGRESQL);
    }

    @Test
    void dialectRejectsUnsupportedConnectionKindsWithErError() {
        var payload = new ErDesignerPayload("oracle", "c1", null, null, List.of(), List.of());

        assertThatThrownBy(payload::resolveDialect)
            .isInstanceOf(ErErrors.DialectUnsupportedException.class)
            .matches(e -> ((ErErrors.DialectUnsupportedException) e).kind().equals("oracle"));
    }
}
