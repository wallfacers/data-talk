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
        id = "datatalk.ui.list",
        executor = Executor.CLIENT,
        description = "action.ui_list.description",
        timeoutMs = 1_000,
        riskLevel = { RiskLevel.L1 },
        category = { Category.UI }
)
public class UiListAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "filter", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("type", Map.of(
                                                "type", "string",
                                                "enum", List.of("workspace", "query_editor")
                                        )),
                                        Map.entry("keyword", Map.of("type", "string")),
                                        Map.entry("connectionId", Map.of("type", "string")),
                                        Map.entry("database", Map.of("type", "string"))
                                )
                        )
                )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("items"),
                "properties", Map.of(
                        "items", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.ofEntries(
                                                Map.entry("objectId", Map.of("type", "string")),
                                                Map.entry("type", Map.of("type", "string")),
                                                Map.entry("title", Map.of("type", "string")),
                                                Map.entry("connectionId", Map.of("type", List.of("string", "null"))),
                                                Map.entry("database", Map.of("type", List.of("string", "null")))
                                        )
                                )
                        )
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
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        throw new UnsupportedOperationException("datatalk.ui.list runs on client; dispatcher must not call handler");
    }
}
