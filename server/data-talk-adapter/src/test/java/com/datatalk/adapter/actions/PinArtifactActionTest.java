package com.datatalk.adapter.actions;

import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PinArtifactActionTest {

    private PinArtifactAction action;

    @BeforeEach
    void setUp() {
        action = new PinArtifactAction();
    }

    @Test
    void descriptorIsClientExecutor() {
        DataTalkAction ann = action.getClass().getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.pin_artifact");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
    }

    @Test
    void handlerThrowsWhenCalledDirectly() {
        assertThrows(UnsupportedOperationException.class, () -> action.handle(null, null));
    }

    @Test
    void inputSchemaRequiresArtifactId() {
        Map<String, Object> schema = action.inputSchema();
        assertThat(schema.get("required")).isEqualTo(List.of("artifactId"));
    }

    @Test
    void outputSchemaRequiresPinned() {
        Map<String, Object> schema = action.outputSchema();
        assertThat(schema.get("required")).isEqualTo(List.of("pinned"));
    }

    @Test
    void sideEffectsArePatchArtifact() {
        assertThat(action.sideEffects()).containsExactly(OntologyEffect.PATCH_ARTIFACT);
    }
}
