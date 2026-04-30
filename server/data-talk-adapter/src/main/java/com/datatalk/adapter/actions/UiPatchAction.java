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
        return Map.ofEntries(
                Map.entry("type", "object"),
                Map.entry("required", List.of("object", "ops")),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("object", Map.of(
                                "type", "string",
                                "enum", List.of("query_editor", "er_inspector", "er_designer"),
                                "description", "Tab type that owns the patch target."
                        )),
                        Map.entry("target", Map.of(
                                "type", "string",
                                "description", "Explicit tab id. Omit only when the active object is unambiguous."
                        )),
                        Map.entry("baseVersion", Map.of(
                                "type", "number",
                                "description", "Top-level numeric version from ui_read. Required for query_editor /content and designer structural paths; view-only paths may omit it."
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
                                                        "description", "JSON Pointer with /key[matchKey=value] addressing extension; see docs/references/er-tab-protocol.md for ER inspector/designer path rules. Designer structural path validation is enforced by the client adapter."
                                                )),
                                                Map.entry("value", Map.of("description", "Required for add and replace; omitted for remove."))
                                        ),
                                        "allOf", List.of(addOrReplaceRequiresValueSchema())
                                )
                        )),
                        Map.entry("reason", Map.of("type", "string"))
                )),
                Map.entry("allOf", List.of(
                        queryEditorContentPatchRequiresBaseVersionSchema(),
                        erDesignerStructuralPatchRequiresBaseVersionSchema()
                ))
        );
    }

    private static Map<String, Object> addOrReplaceRequiresValueSchema() {
        return Map.of(
                "if", Map.of(
                        "properties", Map.of("op", Map.of("enum", List.of("add", "replace"))),
                        "required", List.of("op")
                ),
                "then", Map.of("required", List.of("value"))
        );
    }

    private static Map<String, Object> queryEditorContentPatchRequiresBaseVersionSchema() {
        return Map.of(
                "if", Map.of(
                        "properties", Map.of(
                                "object", Map.of("const", "query_editor"),
                                "ops", Map.of(
                                        "contains", Map.of(
                                                "type", "object",
                                                "properties", Map.of("path", Map.of("const", "/content")),
                                                "required", List.of("path")
                                        )
                                )
                        ),
                        "required", List.of("object", "ops")
                ),
                "then", Map.of("required", List.of("baseVersion"))
        );
    }

    private static Map<String, Object> erDesignerStructuralPatchRequiresBaseVersionSchema() {
        return Map.of(
                "if", Map.of(
                        "properties", Map.of(
                                "object", Map.of("const", "er_designer"),
                                "ops", Map.of(
                                        "contains", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "path", Map.of(
                                                                "type", "string",
                                                                "pattern", "^/(tables|relations|dialect|targetConnectionId|targetDatabase|targetSchema)(?:$|/|\\[)"
                                                        )
                                                ),
                                                "required", List.of("path")
                                        )
                                )
                        ),
                        "required", List.of("object", "ops")
                ),
                "then", Map.of("required", List.of("baseVersion"))
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
