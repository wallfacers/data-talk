package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDescriptorTest {
    @Test
    void holdsAllFieldsAndIsImmutable() {
        Map<String, Object> in = Map.of("type", "object");
        Map<String, Object> out = Map.of("type", "object");
        ActionDescriptor d = new ActionDescriptor(
            "datatalk.echo", Executor.SERVER, "Echo the input.",
            in, out, List.of(), List.of(OntologyEffect.NONE), false, 30_000,
            null, null);
        assertThat(d.id()).isEqualTo("datatalk.echo");
        assertThat(d.executor()).isEqualTo(Executor.SERVER);
        assertThat(d.sideEffects()).containsExactly(OntologyEffect.NONE);
        assertThat(d.timeoutMs()).isEqualTo(30_000);
        assertThat(d.requiresConnection()).isFalse();
    }
}
