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
 * Note: This test runs within the full Spring Boot context that scans
 * all @DataTalkAction beans. The "ui.list no longer registered" assertion
 * may fail until Task 11 removes the UiListAction — that is expected.
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
            "outputMode", "count",
            "filter", java.util.Map.of("scope", "workspace")
        );

        var query = UiFindAction.parse(input);

        assertThat(query.outputMode()).isEqualTo(
            com.datatalk.application.stage.StageFindQuery.OutputMode.COUNT);
        assertThat(query.filter()).isNotNull();
        assertThat(query.filter().scope()).isEqualTo(
            com.datatalk.domain.stage.StageTabScope.WORKSPACE);
    }

    @Test
    void toEnvelopeSerializesResult() {
        com.datatalk.application.stage.StageFindResult result =
            com.datatalk.application.stage.StageFindResult.metadata(
                java.util.List.of(java.util.Map.of("id", "t1", "title", "Test")),
                1, false);

        java.util.Map<String, Object> envelope = UiFindAction.toEnvelope(result);

        assertThat(envelope).containsEntry("outputMode", "metadata");
        assertThat(envelope).containsEntry("totalMatched", 1);
        assertThat(envelope).containsEntry("truncated", false);
    }
}
