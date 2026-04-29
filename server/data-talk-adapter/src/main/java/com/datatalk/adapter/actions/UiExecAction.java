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

    // AI-facing description fields and aiHint values stay in English per Spec §4 P12.
    // User-visible labels belong in localized message bundles.
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
                "oneOf", List.of(workspaceExecSchema(), queryEditorExecSchema(), erInspectorExecSchema())
        );
    }

    private static Map<String, Object> workspaceExecSchema() {
        return Map.of(
                "required", List.of("object", "action"),
                "properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("workspace"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "open", "focus", "choose_connection", "detach", "archive", "trash",
                                        "open_er_inspector", "open_er_designer"
                                ),
                                "description", "Workspace verbs for opening, focusing, detaching, archiving, deleting tabs, and creating ER inspector / designer tabs; choose_connection only when a database-related request needs a data source."
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("type", Map.of(
                                                "type", "string",
                                                "enum", List.of("query_editor"),
                                                "description", "workspace open supports query_editor. Use open_er_inspector for ER tabs."
                                        )),
                                        Map.entry("title", Map.of("type", "string")),
                                        Map.entry("connection_id", Map.of("type", "string")),
                                        Map.entry("connectionId", Map.of("type", "string")),
                                        Map.entry("database", Map.of("type", "string")),
                                        Map.entry("schema", Map.of("type", "string")),
                                        Map.entry("payload", Map.of("type", "object")),
                                        Map.entry("target", Map.of("type", "string")),
                                        Map.entry("preferredConnectionId", Map.of("type", "string")),
                                        Map.entry("archived", Map.of(
                                                "type", "boolean",
                                                "default", Boolean.TRUE,
                                                "description", "Only used for `action=archive`. true=archive, false=unarchive."
                                        )),
                                        Map.entry("tables", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "Required for open_er_inspector. Seed table names; 1..100."
                                        )),
                                        Map.entry("neighborDepth", Map.of(
                                                "type", "integer",
                                                "enum", List.of(0, 1, 2),
                                                "description", "Optional for open_er_inspector. Direct/indirect neighbor expansion. Default 1."
                                        )),
                                        Map.entry("dialect", Map.of(
                                                "type", "string",
                                                "enum", List.of("mysql", "postgresql", "h2", "sqlite"),
                                                "description", "Required for open_er_designer. Oracle / SQL Server are not supported."
                                        )),
                                        Map.entry("targetConnectionId", Map.of(
                                                "type", "string",
                                                "description", "Optional for open_er_designer. When set, the draft is bound to this connection for diff / generate_ddl."
                                        )),
                                        Map.entry("targetDatabase", Map.of("type", "string")),
                                        Map.entry("targetSchema", Map.of("type", "string")),
                                        Map.entry("seedTables", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "object"),
                                                "description", "Optional for open_er_designer. Initial tables for the draft."
                                        )),
                                        Map.entry("seedRelations", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "object"),
                                                "description", "Optional for open_er_designer. Initial relations for the draft."
                                        ))
                                )
                        ))
                )
        );
    }

    private static Map<String, Object> queryEditorExecSchema() {
        return Map.ofEntries(
                Map.entry("required", List.of("object", "action")),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("query_editor"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of("apply_text_edits", "set_context", "run_sql", "format_sql", "focus"),
                                "description", "Query editor verbs for editing, context changes, execution, formatting, and focus."
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("baseVersion", Map.of(
                                                "type", "number",
                                                "description", "Required for action=apply_text_edits: tab payloadVersion read just before the edit; rejected with `error.code='version_conflict'` if it has drifted."
                                        )),
                                        Map.entry("edits", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "required", List.of("range", "text", "expectedText"),
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
                                                                Map.entry("expectedText", Map.of(
                                                                        "type", "string",
                                                                        "description", "Required: exact current text in the range, normalized (\\r\\n -> \\n). Rejected with `error.code='expected_text_mismatch'` if it doesn't match."
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
                )),
                Map.entry("allOf", List.of(
                        Map.of(
                                "if", Map.of(
                                        "properties", Map.of(
                                                "object", Map.of("const", "query_editor"),
                                                "action", Map.of("const", "apply_text_edits")
                                        ),
                                        "required", List.of("object", "action")
                                ),
                                "then", Map.of(
                                        "required", List.of("params"),
                                        "properties", Map.of(
                                                "params", Map.of(
                                                        "type", "object",
                                                        "required", List.of("baseVersion", "edits")
                                                )
                                        )
                                )
                        )
                ))
        );
    }

    private static Map<String, Object> erInspectorExecSchema() {
        return Map.ofEntries(
                Map.entry("required", List.of("object", "action")),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("er_inspector"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of("refresh", "auto_layout", "fit_view", "add_neighbors", "fork_to_designer"),
                                "description", "ER inspector verbs. fork_to_designer is implemented in Plan B and returns an error in Plan A."
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("table", Map.of(
                                                "type", "string",
                                                "description", "Required for add_neighbors: the table whose direct neighbors should be expanded into selection."
                                        )),
                                        Map.entry("title", Map.of(
                                                "type", "string",
                                                "description", "Optional for fork_to_designer."
                                        ))
                                )
                        ))
                ))
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
