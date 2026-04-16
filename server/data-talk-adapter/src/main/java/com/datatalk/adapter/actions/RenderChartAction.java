package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.PayloadRef;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.render_chart",
    executor = Executor.SERVER,
    description = "Render an ECharts-option chart. Set supersedes to replace a previous chart.",
    produces = {"datatalk.artifact"},
    requiresConnection = false,
    timeoutMs = 5_000
)
public class RenderChartAction implements ActionHandler<Map, Map> {

    private final ArtifactRepository artifacts;
    private final SessionRepository sessions;
    private final ObjectMapper om;
    private final Clock clock;
    private final IdGenerator ids;

    public RenderChartAction(ArtifactRepository artifacts, SessionRepository sessions,
                              ObjectMapper om, Clock clock, IdGenerator ids) {
        this.artifacts = artifacts;
        this.sessions = sessions;
        this.om = om;
        this.clock = clock;
        this.ids = ids;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sourceArtifactId", "echartsOption"),
            "properties", Map.of(
                "sourceArtifactId", Map.of("type", "string"),
                "echartsOption",    Map.of("type", "object"),
                "supersedes",       Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version",    Map.of("type", "integer")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String source = String.valueOf(input.get("sourceArtifactId"));
        Object optObj = input.get("echartsOption");
        if (!(optObj instanceof Map<?, ?> opt)) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID, "echartsOption must be an object", true));
        }
        if (artifacts.findLatestById(source).isEmpty()) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID,
                "sourceArtifactId " + source + " not found", true));
        }

        String supersedes = (String) input.get("supersedes");
        Integer supersedesVer = null;
        if (supersedes != null) {
            var prev = artifacts.findLatestById(supersedes);
            if (prev.isEmpty()) {
                return CompletableFuture.failedStage(new DataTalkException(
                    DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND,
                    "no artifact with id " + supersedes, true));
            }
            supersedesVer = prev.get().version();
        }

        String artifactId = ids.nextArtifactId();
        String payloadJson;
        try { payloadJson = om.writeValueAsString(Map.of(
            "sourceArtifactId", source,
            "echartsOption", opt
        )); } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }

        artifacts.insert(new ArtifactRecord(
            artifactId, 1, ctx.sessionId(), "chart", ctx.callId(),
            PayloadRef.INLINE_PREFIX + payloadJson, payloadJson.length(),
            supersedes, supersedesVer, false, clock.millis()
        ));

        return CompletableFuture.completedFuture(Map.of(
            "artifactId", artifactId,
            "version", 1
        ));
    }
}
