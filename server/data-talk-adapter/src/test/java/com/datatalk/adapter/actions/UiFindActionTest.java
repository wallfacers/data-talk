package com.datatalk.adapter.actions;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.stage.StageFindService;
import com.datatalk.domain.action.ActionDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the ui_find action is registered and discoverable.
 */
class UiFindActionTest {

    @Test
    void uiFindActionParsesMetadataMode() {
        StageFindService svc = mock(StageFindService.class);
        UiFindAction action = new UiFindAction(svc);

        // Verify the action has correct metadata
        assertThat(action.inputSchema()).containsEntry("type", "object");
        assertThat(action.outputSchema()).containsEntry("type", "object");
    }

    @Test
    void parseMapsOutputModeCorrectly() {
        java.util.Map<String, Object> input = java.util.Map.of(
            "output", java.util.Map.of("mode", "count"),
            "filter", java.util.Map.of(
                "originSessionId", "sess-1"
            )
        );

        var query = UiFindAction.parse(input);

        assertThat(query.outputMode()).isEqualTo(
            com.datatalk.application.stage.StageFindQuery.OutputMode.COUNT);
        assertThat(query.filter()).isNotNull();
        assertThat(query.filter().originSessionId()).isEqualTo("sess-1");
    }

    @Test
    void toEnvelopeSerializesResult() {
        com.datatalk.application.stage.StageFindResult result =
            com.datatalk.application.stage.StageFindResult.metadata(
                java.util.List.of(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "id", "t1",
                    "title", "Test",
                    "originSessionId", "sess-1",
                    "originSessionTitle", "April Weekly"
                ))),
                1, false);

        java.util.Map<String, Object> envelope = UiFindAction.toEnvelope(result);

        assertThat(envelope).containsKey("items");
        assertThat(envelope).containsEntry("totalMatched", 1);
        assertThat(envelope).containsEntry("truncated", false);
        assertThat((Collection<?>) envelope.get("items")).singleElement().satisfies(item -> {
            java.util.Map<String, Object> entry = (java.util.Map<String, Object>) item;
            assertThat(entry).containsEntry("originSessionId", "sess-1")
                .containsEntry("originSessionTitle", "April Weekly");
        });
    }
}
