package com.datatalk.adapter.actions;

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
                                "enum", List.of("query_editor"),
                                "description", "Only query_editor supports patch today."
                        )),
                        Map.entry("target", Map.of(
                                "type", "string",
                                "description", "Explicit query_editor tab id. Omit only when the active query editor is already clear."
                        )),
                        Map.entry("ops", Map.of(
                                "type", "array",
                                "items", Map.of("oneOf", List.of(
                                        replaceOp("/content", Map.of("type", "string"), true),
                                        replaceOp("/connectionId", Map.of("type", List.of("string", "null")), false),
                                        replaceOp("/database", Map.of("type", List.of("string", "null")), false),
                                        replaceOp("/schema", Map.of("type", List.of("string", "null")), false)
                                ))
                        )),
                        Map.entry("reason", Map.of("type", "string"))
                )
        );
    }

    private static Map<String, Object> replaceOp(String path,
                                                 Map<String, Object> valueSchema,
                                                 boolean requiresBaseVersion) {
        List<String> required = requiresBaseVersion
            ? List.of("op", "path", "value", "baseVersion")
            : List.of("op", "path", "value");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("op", Map.of("type", "string", "enum", List.of("replace")));
        properties.put("path", Map.of("type", "string", "enum", List.of(path)));
        properties.put("value", valueSchema);
        if (requiresBaseVersion) {
            properties.put("baseVersion", Map.of(
                "type", "number",
                "description", "Required: tab payloadVersion at the moment you read the content; rejected if it has drifted."
            ));
        }
        return Map.of(
                "type", "object",
                "required", required,
                "properties", properties
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
