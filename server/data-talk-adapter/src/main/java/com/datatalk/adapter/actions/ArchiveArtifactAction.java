package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.FileArtifactService.ArchiveCandidateOutcome;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.archive_artifact",
    executor = Executor.SERVER,
    description = "action.archive_artifact.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.ARTIFACT }
)
public class ArchiveArtifactAction implements ActionHandler<Map, Map> {

    private final FileArtifactService svc;
    private final IdGenerator ids;
    private final Clock clock;

    public ArchiveArtifactAction(FileArtifactService svc, IdGenerator ids, Clock clock) {
        this.svc = svc;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("path", "kind"),
            "properties", Map.of(
                "path", Map.of("type", "string"),
                "kind", Map.of("type", "string", "enum", List.of(
                    "report", "er_diagram", "sql_script", "dataset", "other")),
                "title", Map.of("type", "string"),
                "summary", Map.of("type", "string")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("ok"),
            "properties", Map.of(
                "ok", Map.of("type", "boolean"),
                "fileArtifactId", Map.of("type", "string"),
                "status", Map.of("type", "string"),
                "physicalPath", Map.of("type", "string"),
                "warn", Map.of("type", "string"),
                "error", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.CREATE_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String sessionId = ctx.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return CompletableFuture.completedFuture(errorResult("path_not_found"));
        }

        String path = input.get("path") != null ? String.valueOf(input.get("path")).trim() : "";
        if (path.isEmpty()) {
            return CompletableFuture.completedFuture(errorResult("path_not_found"));
        }

        String kindRaw = input.get("kind") != null ? String.valueOf(input.get("kind")).trim() : "";
        FileArtifactKind kind;
        try {
            kind = FileArtifactKind.fromDb(kindRaw);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(errorResult("path_not_found"));
        }

        String title = input.get("title") != null ? String.valueOf(input.get("title")).trim() : null;
        if (title != null && title.isEmpty()) {
            title = null;
        }
        String summary = input.get("summary") != null ? String.valueOf(input.get("summary")).trim() : null;
        if (summary != null && summary.isEmpty()) {
            summary = null;
        }

        ArchiveCandidateOutcome outcome = svc.archiveCandidate(
                sessionId, path, kind, title, summary, clock, ids);

        if (outcome instanceof ArchiveCandidateOutcome.Success s) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("fileArtifactId", s.fileArtifactId());
            result.put("status", "candidate");
            result.put("physicalPath", s.physicalPath());
            if (s.alreadyArchived()) {
                result.put("warn", "already_archived");
            }
            return CompletableFuture.completedFuture(result);
        }

        if (outcome instanceof ArchiveCandidateOutcome.PathRejected r) {
            return CompletableFuture.completedFuture(errorResult(r.error().wire()));
        }

        return CompletableFuture.completedFuture(errorResult("path_not_found"));
    }

    private static Map<String, Object> errorResult(String error) {
        return Map.of("ok", false, "error", error);
    }
}
