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
        id = "datatalk.ui.exec",
        executor = Executor.CLIENT,
        description = "action.ui_exec.description",
        timeoutMs = 30_000,
        riskLevel = { RiskLevel.L1 },
        category = { Category.UI }
)
public class UiExecAction implements ActionHandler<Map, Map> {

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("object", "action"),
                "properties", Map.of(
                        "target", Map.of(
                                "type", "string",
                                "description", "Explicit object id. Omit only when the active object is already clear."
                        )
                ),
                "oneOf", List.of(workspaceExecSchema(), queryEditorExecSchema())
        );
    }

    private static Map<String, Object> workspaceExecSchema() {
        return Map.of(
                "required", List.of("object", "action"),
                "properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("workspace"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of("open", "close", "focus", "choose_connection")
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("type", Map.of(
                                                "type", "string",
                                                "enum", List.of("query_editor"),
                                                "description", "workspace open supports query_editor today."
                                        )),
                                        Map.entry("title", Map.of("type", "string")),
                                        Map.entry("connection_id", Map.of("type", "string")),
                                        Map.entry("database", Map.of("type", "string")),
                                        Map.entry("schema", Map.of("type", "string")),
                                        Map.entry("payload", Map.of("type", "object")),
                                        Map.entry("target", Map.of("type", "string")),
                                        Map.entry("preferredConnectionId", Map.of("type", "string"))
                                )
                        ))
                )
        );
    }

    private static Map<String, Object> queryEditorExecSchema() {
        return Map.of(
                "required", List.of("object", "action"),
                "properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("query_editor"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of("apply_text_edits", "set_context", "run_sql", "format_sql", "focus", "close")
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("baseVersion", Map.of("type", "number")),
                                        Map.entry("edits", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "required", List.of("range", "text"),
                                                        "properties", Map.ofEntries(
                                                                Map.entry("range", Map.of(
                                                                        "type", "object",
                                                                        "required", List.of("startLine", "startColumn", "endLine", "endColumn"),
                                                                        "properties", Map.ofEntries(
                                                                                Map.entry("startLine", Map.of("type", "number")),
                                                                                Map.entry("startColumn", Map.of("type", "number")),
                                                                                Map.entry("endLine", Map.of("type", "number")),
                                                                                Map.entry("endColumn", Map.of("type", "number"))
                                                                        )
                                                                )),
                                                                Map.entry("text", Map.of("type", "string"))
                                                        )
                                                )
                                        )),
                                        Map.entry("connectionId", Map.of("type", List.of("string", "null"))),
                                        Map.entry("database", Map.of("type", List.of("string", "null"))),
                                        Map.entry("schema", Map.of("type", List.of("string", "null"))),
                                        Map.entry("limit", Map.of("type", List.of("number", "null")))
                                )
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
        throw new UnsupportedOperationException("datatalk.ui.exec runs on client; dispatcher must not call handler");
    }
}
