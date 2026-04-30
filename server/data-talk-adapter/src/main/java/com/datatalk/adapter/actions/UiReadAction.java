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
        id = "datatalk.ui.read",
        executor = Executor.CLIENT,
        description = "action.ui_read.description",
        timeoutMs = 3_000,
        riskLevel = { RiskLevel.L1 },
        category = { Category.UI }
)
public class UiReadAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("object"),
                "properties", Map.ofEntries(
                        Map.entry("object", Map.of(
                                "type", "string",
                                "enum", List.of("workspace", "query_editor", "er_inspector", "er_designer"),
                                "description", "UI object type to read, including ER tabs for versioned state before patching."
                        )),
                        Map.entry("target", Map.of(
                                "type", "string",
                                "description", "Explicit object id. Omit only when the active object is already clear."
                        )),
                        Map.entry("mode", Map.of(
                                "type", "string",
                                "enum", List.of("state", "schema", "actions", "full"),
                                "description", "state=current values, schema=readable fields, actions=supported exec operations, full=state+schema+actions."
                        ))
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
        throw new UnsupportedOperationException("datatalk.ui.read runs on client; dispatcher must not call handler");
    }
}
