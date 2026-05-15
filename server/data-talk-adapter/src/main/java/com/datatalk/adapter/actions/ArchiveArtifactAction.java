package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.FileArtifactService.ArchiveCandidateOutcome;
import com.datatalk.application.fileartifact.PathSafetyError;
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
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ok", Map.of("type", "boolean"));
        properties.put("fileArtifactId", Map.of("type", "string"));
        properties.put("status", Map.of("type", "string"));
        properties.put("physicalPath", Map.of("type", "string"));
        properties.put("warn", Map.of("type", "string"));
        properties.put("error", Map.of("type", "string"));
        properties.put("hint", Map.of("type", "string"));
        return Map.of(
            "type", "object",
            "required", List.of("ok"),
            "properties", properties
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
            return CompletableFuture.completedFuture(errorResult("path_not_found", null));
        }

        String path = input.get("path") != null ? String.valueOf(input.get("path")).trim() : "";
        if (path.isEmpty()) {
            return CompletableFuture.completedFuture(errorResult("path_not_found", null));
        }

        String kindRaw = input.get("kind") != null ? String.valueOf(input.get("kind")).trim() : "";
        FileArtifactKind kind;
        try {
            kind = FileArtifactKind.fromDb(kindRaw);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(errorResult("path_not_found", null));
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
            return CompletableFuture.completedFuture(errorResult(r.error().wire(), hintFor(r.error())));
        }

        return CompletableFuture.completedFuture(errorResult("path_not_found", null));
    }

    private static Map<String, Object> errorResult(String error, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", error);
        if (hint != null) {
            m.put("hint", hint);
        }
        return m;
    }

    private static String hintFor(PathSafetyError err) {
        return switch (err) {
            case PATH_OUTSIDE_SESSION_DIR -> "archive_artifact requires the file to be located under the current session's working directory (~/.data-talk/opencode/<sessionId>/...). Move the file under the session dir, or skip archiving for ad-hoc artifacts.";
            case PATH_NOT_FOUND -> "The requested path does not exist on disk. Confirm the file was actually written before calling archive_artifact.";
            case PATH_IS_DIRECTORY -> "archive_artifact only accepts regular files, not directories.";
            case PATH_IS_SYSTEM -> "Cannot archive files from system directories (/proc, /sys, /etc, etc).";
            case PATH_CONTAINS_SYMLINK -> "archive_artifact rejects symlinks to prevent path traversal attacks. Pass the real file path.";
            case PATH_TOCTOU_RACE -> "Path safety re-check failed (file changed between validations). Retry the call.";
        };
    }
}
