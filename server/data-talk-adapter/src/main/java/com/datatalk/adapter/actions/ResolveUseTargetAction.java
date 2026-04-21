package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.UseTargetResolver;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.resolve_use_target",
    executor = Executor.SERVER,
    description = "action.resolve_use_target.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.METADATA }
)
public class ResolveUseTargetAction implements ActionHandler<Map, Map> {

    private final UseTargetResolver resolver;

    public ResolveUseTargetAction(UseTargetResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("target"),
            "properties", Map.of("target", Map.of("type", "string"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("status", "candidates", "suggestions"),
            "properties", Map.of(
                "status", Map.of("type", "string"),
                "context", Map.of("type", "object"),
                "matched_target", Map.of("type", "object"),
                "candidates", Map.of("type", "array"),
                "suggestions", Map.of("type", "array"),
                "message", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String target = String.valueOf(input.get("target"));
        var resolved = resolver.resolve(ctx.sessionId(), target);
        var out = new LinkedHashMap<String, Object>();
        out.put("status", resolved.status());
        out.put("context", resolved.context() == null ? null : dataContextMap(resolved.context()));
        out.put("matched_target", resolved.matchedTarget() == null ? null : optionMap(resolved.matchedTarget()));
        out.put("candidates", resolved.candidates().stream().map(ResolveUseTargetAction::optionMap).toList());
        out.put("suggestions", resolved.suggestions().stream().map(ResolveUseTargetAction::optionMap).toList());
        out.put("message", resolved.message());
        return CompletableFuture.completedFuture(out);
    }

    private static Map<String, Object> dataContextMap(SessionDataContextRecord record) {
        return GetDataContextAction.toMap(record);
    }

    private static Map<String, Object> optionMap(UseTargetResolver.TargetOption option) {
        var out = new LinkedHashMap<String, Object>();
        out.put("level", option.level());
        out.put("connectionId", option.connectionId());
        out.put("connectionName", option.connectionName());
        out.put("database", option.database());
        out.put("schema", option.schema());
        out.put("label", option.label());
        return out;
    }
}
