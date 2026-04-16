package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.supersede_artifact",
    executor = Executor.SERVER,
    description = "Explicitly mark one artifact as superseded by another.",
    timeoutMs = 3_000
)
public class SupersedeArtifactAction implements ActionHandler<Map, Map> {

    private final ArtifactRepository artifacts;

    public SupersedeArtifactAction(ArtifactRepository artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("newArtifactId", "oldArtifactId"),
            "properties", Map.of(
                "newArtifactId", Map.of("type", "string"),
                "oldArtifactId", Map.of("type", "string"),
                "reason",        Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("ok"),
            "properties", Map.of("ok", Map.of("type", "boolean"))
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.PATCH_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String newId = String.valueOf(input.get("newArtifactId"));
        String oldId = String.valueOf(input.get("oldArtifactId"));

        var oldArtifact = artifacts.findLatestById(oldId);
        var newArtifact = artifacts.findLatestById(newId);

        if (oldArtifact.isEmpty() || newArtifact.isEmpty()) {
            return CompletableFuture.failedStage(
                new DataTalkException(
                    DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND,
                    "old or new artifact not found",
                    false
                )
            );
        }

        artifacts.updateSupersedes(oldId, oldArtifact.get().version(), newId);

        return CompletableFuture.completedFuture(Map.of("ok", true));
    }
}
