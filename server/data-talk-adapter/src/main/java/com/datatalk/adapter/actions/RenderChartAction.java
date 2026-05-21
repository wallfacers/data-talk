package com.datatalk.adapter.actions;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.render_chart",
    executor = Executor.SERVER,
    description = "action.render_chart.description",
    produces = {"datatalk.artifact"},
    requiresConnection = false,
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.ARTIFACT }
)
public class RenderChartAction implements ActionHandler<Map, Map> {

    private final ChartArtifactService chartService;

    public RenderChartAction(ChartArtifactService chartService) {
        this.chartService = chartService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("echartsOption"),
            "properties", Map.of(
                "echartsOption", Map.of("type", "object"),
                "sourceArtifactId", Map.of("type", "string"),
                "supersedes", Map.of("type", "string"),
                "originMessageId", Map.of("type", "string"),
                "originPartId", Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("artifactId", "version"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version", Map.of("type", "integer")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of();
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        Object echartsOption = input.get("echartsOption");
        if (!(echartsOption instanceof Map<?, ?> optionMap)) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID,
                "echartsOption must be an object",
                true
            ));
        }

        try {
            ChartArtifactService.Result result = chartService.createChartArtifact(new ChartArtifactService.Request(
                ctx.sessionId(),
                (Map<String, Object>) optionMap,
                (String) input.get("sourceArtifactId"),
                (String) input.get("supersedes"),
                (String) input.get("originMessageId"),
                (String) input.get("originPartId"),
                ctx.callId()
            ));
            return CompletableFuture.completedFuture(Map.of(
                "artifactId", result.artifactId(),
                "version", result.version()
            ));
        } catch (RuntimeException e) {
            return CompletableFuture.failedStage(e);
        }
    }
}
