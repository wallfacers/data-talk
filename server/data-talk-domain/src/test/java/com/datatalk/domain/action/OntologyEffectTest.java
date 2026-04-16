package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyEffectTest {

    @Test
    void hasExactlyThreeValuesInDeclaredOrder() {
        assertThat(OntologyEffect.values())
            .containsExactly(
                OntologyEffect.NONE,
                OntologyEffect.CREATE_ARTIFACT,
                OntologyEffect.PATCH_ARTIFACT
            );
    }
}
