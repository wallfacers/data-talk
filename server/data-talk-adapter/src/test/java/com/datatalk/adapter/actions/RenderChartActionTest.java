package com.datatalk.adapter.actions;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderChartActionTest {

    @Test
    @SuppressWarnings("unchecked")
    void handleDelegatesToChartArtifactServiceAndReturnsServiceResult() {
        ChartArtifactService service = mock(ChartArtifactService.class);
        when(service.createChartArtifact(any())).thenReturn(new ChartArtifactService.Result("art_42", 1));

        RenderChartAction action = new RenderChartAction(service);
        Map<String, Object> result = (Map<String, Object>) action.handle(
            new ActionContext("s1", "call_99", null, null),
            Map.of(
                "echartsOption", Map.of("series", List.of()),
                "sourceArtifactId", "art_src",
                "originMessageId", "msg_7",
                "originPartId", "part_2"
            )
        ).toCompletableFuture().join();

        assertThat(result).isEqualTo(Map.of("artifactId", "art_42", "version", 1));

        ArgumentCaptor<ChartArtifactService.Request> request =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        verify(service).createChartArtifact(request.capture());
        assertThat(request.getValue().sessionId()).isEqualTo("s1");
        assertThat(request.getValue().callId()).isEqualTo("call_99");
        assertThat(request.getValue().sourceArtifactId()).isEqualTo("art_src");
        assertThat(request.getValue().originMessageId()).isEqualTo("msg_7");
        assertThat(request.getValue().originPartId()).isEqualTo("part_2");
        assertThat(request.getValue().echartsOption()).containsKey("series");
    }

    @Test
    void handleAcceptsMissingSourceArtifactId() {
        ChartArtifactService service = mock(ChartArtifactService.class);
        when(service.createChartArtifact(any())).thenReturn(new ChartArtifactService.Result("art_42", 1));

        RenderChartAction action = new RenderChartAction(service);
        action.handle(
            new ActionContext("s1", "call_1", null, null),
            Map.of("echartsOption", Map.of("series", List.of()))
        ).toCompletableFuture().join();

        ArgumentCaptor<ChartArtifactService.Request> request =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        verify(service).createChartArtifact(request.capture());
        assertThat(request.getValue().sourceArtifactId()).isNull();
        assertThat(request.getValue().originMessageId()).isNull();
        assertThat(request.getValue().originPartId()).isNull();
    }

    @Test
    void handleUsesSupersedesAsCompatibilityFallback() {
        ChartArtifactService service = mock(ChartArtifactService.class);
        when(service.createChartArtifact(any())).thenReturn(new ChartArtifactService.Result("art_42", 1));

        RenderChartAction action = new RenderChartAction(service);
        action.handle(
            new ActionContext("s1", "call_2", null, null),
            Map.of(
                "echartsOption", Map.of("series", List.of()),
                "supersedes", "art_old"
            )
        ).toCompletableFuture().join();

        ArgumentCaptor<ChartArtifactService.Request> request =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        verify(service).createChartArtifact(request.capture());
        assertThat(request.getValue().sourceArtifactId()).isEqualTo("art_old");
    }

    @Test
    void rejectsNonObjectEchartsOptionWithoutCallingService() {
        ChartArtifactService service = mock(ChartArtifactService.class);
        RenderChartAction action = new RenderChartAction(service);

        assertThatThrownBy(() -> action.handle(
            new ActionContext("s1", "call_1", null, null),
            Map.of("echartsOption", "not-an-object")
        ).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class);

        verify(service, never()).createChartArtifact(any());
    }

    @Test
    void sideEffectsReturnsEmpty() {
        RenderChartAction action = new RenderChartAction(mock(ChartArtifactService.class));

        assertThat(action.sideEffects()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchemaRequiresOnlyEchartsOptionAndKeepsCompatibilityFields() {
        RenderChartAction action = new RenderChartAction(mock(ChartArtifactService.class));

        Map<String, Object> schema = action.inputSchema();
        assertThat((List<String>) schema.get("required")).containsExactly("echartsOption");
        assertThat((Map<String, Object>) schema.get("properties"))
            .containsKeys("echartsOption", "sourceArtifactId", "supersedes", "originMessageId", "originPartId");
    }
}
