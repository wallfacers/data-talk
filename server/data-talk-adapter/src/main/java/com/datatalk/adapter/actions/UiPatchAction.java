package com.datatalk.adapter.actions;

import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
        id = "datatalk.ui.patch",
        executor = Executor.CLIENT,
        description = "action.ui_patch.description",
        timeoutMs = 3_000,
        riskLevel = { RiskLevel.L1 },
        category = { Category.UI }
)
public class UiPatchAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("object", "ops"),
                "properties", Map.ofEntries(
                        Map.entry("object", Map.of(
                                "type", "string",
                                "enum", List.of("query_editor", "er_inspector"),
                                "description", "Tab type that owns the patch target."
                        )),
                        Map.entry("target", Map.of(
                                "type", "string",
                                "description", "Explicit tab id. Omit only when the active object is unambiguous."
                        )),
                        Map.entry("baseVersion", Map.of(
                                "oneOf", List.of(
                                        Map.of("type", "number"),
                                        Map.of("type", "string", "enum", List.of("auto"))
                                ),
                                "description", "Optional. 'auto' (default) lets the server use the latest version. Designer structural paths require a numeric baseVersion in Plan B."
                        )),
                        Map.entry("ops", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "required", List.of("op", "path"),
                                        "properties", Map.ofEntries(
                                                Map.entry("op", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("add", "remove", "replace"),
                                                        "description", "RFC 6902 subset; merge/move/copy/test are not supported."
                                                )),
                                                Map.entry("path", Map.of(
                                                        "type", "string",
                                                        "description", "JSON Pointer with /key[matchKey=value] addressing extension; see docs/references/er-tab-protocol.md for the inspector path whitelist."
                                                )),
                                                Map.entry("value", Map.of("description", "Op value (omitted for remove)."))
                                        )
                                )
                        )),
                        Map.entry("reason", Map.of("type", "string"))
                )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object");
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
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        throw new UnsupportedOperationException("datatalk.ui.patch runs on client; dispatcher must not call handler");
    }
}
