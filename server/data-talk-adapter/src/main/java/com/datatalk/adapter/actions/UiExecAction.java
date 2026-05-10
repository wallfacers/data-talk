package com.datatalk.adapter.actions;

import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
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
                "oneOf", List.of(workspaceExecSchema(), queryEditorExecSchema(), erInspectorExecSchema(), erDesignerExecSchema(), dashboardExecSchema())
        );
    }

    private static Map<String, Object> actionRequiresParams(
            String object,
            String action,
            List<String> requiredParams
    ) {
        return Map.of(
                "if", actionCondition(object, action),
                "then", Map.of(
                        "required", List.of("params"),
                        "properties", Map.of(
                                "params", Map.of(
                                        "type", "object",
                                        "required", requiredParams
                                )
                        )
                )
        );
    }

    private static Map<String, Object> actionRequiresAnyParam(
            String object,
            String action,
            List<String> paramNames
    ) {
        return Map.of(
                "if", actionCondition(object, action),
                "then", Map.of(
                        "required", List.of("params"),
                        "properties", Map.of(
                                "params", Map.of(
                                        "type", "object",
                                        "anyOf", paramNames.stream()
                                                .map(param -> Map.of("required", List.of(param)))
                                                .toList()
                                )
                        )
                )
        );
    }

    private static Map<String, Object> actionCondition(String object, String action) {
        return Map.of(
                "properties", Map.of(
                        "object", Map.of("const", object),
                        "action", Map.of("const", action)
                ),
                "required", List.of("object", "action")
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
                                        "open", "focus", "choose_connection", "detach", "archive", "trash", "rename", "pin",
                                        "open_er_inspector", "open_er_designer"
                                ),
                                "description", "Workspace verbs for opening, focusing, detaching, archiving, deleting, renaming, pinning tabs, and creating ER inspector / designer tabs; choose_connection only when a database-related request needs a data source."
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
                                        Map.entry("payload", Map.of(
                                                "type", "object",
                                                "description", "Optional query_editor open payload. SQL content may be provided as initialSql, content, or sql; initialSql wins over content, content wins over sql.",
                                                "properties", Map.ofEntries(
                                                        Map.entry("initialSql", Map.of(
                                                                "type", "string",
                                                                "description", "Initial SQL text for a query_editor tab."
                                                        )),
                                                        Map.entry("content", Map.of(
                                                                "type", "string",
                                                                "description", "Initial SQL text alias for query_editor open."
                                                        )),
                                                        Map.entry("sql", Map.of(
                                                                "type", "string",
                                                                "description", "Legacy initial SQL text alias for query_editor open."
                                                        )),
                                                        Map.entry("autoRun", Map.of("type", "boolean")),
                                                        Map.entry("connectionId", Map.of("type", "string")),
                                                        Map.entry("connectionName", Map.of("type", "string")),
                                                        Map.entry("database", Map.of("type", "string")),
                                                        Map.entry("schema", Map.of("type", "string"))
                                                )
                                        )),
                                        Map.entry("target", Map.of("type", "string")),
                                        Map.entry("targets", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "Batch alternative to `target`. Applies the action to all listed tab IDs."
                                        )),
                                        Map.entry("preferredConnectionId", Map.of("type", "string")),
                                        Map.entry("tabs", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "object"),
                                                "description", "Batch alternative for `action=open`. Each element accepts the same fields as a single open call (type, title, connection_id, database, schema, payload)."
                                        )),
                                        Map.entry("archived", Map.of(
                                                "type", "boolean",
                                                "default", Boolean.TRUE,
                                                "description", "Only used for `action=archive`. true=archive, false=unarchive."
                                        )),
                                        Map.entry("pinned", Map.of(
                                                "type", "boolean",
                                                "default", Boolean.TRUE,
                                                "description", "Only used for `action=pin`. true=pin, false=unpin."
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
                ),
                "allOf", List.of(
                        actionRequiresParams("workspace", "open", List.of("type")),
                        actionRequiresParams("workspace", "focus", List.of("target")),
                        actionRequiresParams("workspace", "detach", List.of("target")),
                        actionRequiresParams("workspace", "archive", List.of("target")),
                        actionRequiresParams("workspace", "trash", List.of("target")),
                        actionRequiresAnyParam("workspace", "trash", List.of("target", "targets")),
                        actionRequiresParams("workspace", "archive", List.of("target")),
                        actionRequiresAnyParam("workspace", "archive", List.of("target", "targets")),
                        actionRequiresParams("workspace", "rename", List.of("target", "title")),
                        actionRequiresParams("workspace", "pin", List.of("target")),
                        actionRequiresParams("workspace", "open_er_inspector", List.of("connectionId", "tables")),
                        actionRequiresParams("workspace", "open_er_designer", List.of("dialect"))
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
                                        Map.entry("useSessionContext", Map.of("type", "boolean")),
                                        Map.entry("connectionId", Map.of("type", List.of("string", "null"))),
                                        Map.entry("database", Map.of("type", List.of("string", "null"))),
                                        Map.entry("schema", Map.of("type", List.of("string", "null"))),
                                        Map.entry("limit", Map.of(
                                                "type", List.of("integer", "null"),
                                                "enum", Arrays.asList(10, 100, 1000, null)
                                        ))
                                )
                        ))
                )),
                Map.entry("allOf", List.of(
                        actionRequiresParams("query_editor", "apply_text_edits", List.of("baseVersion", "edits")),
                        actionRequiresAnyParam("query_editor", "set_context",
                                List.of("useSessionContext", "connectionId", "database", "schema", "limit"))
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
                )),
                Map.entry("allOf", List.of(
                        actionRequiresParams("er_inspector", "add_neighbors", List.of("table"))
                ))
        );
    }

    private static Map<String, Object> erDesignerExecSchema() {
        return Map.ofEntries(
                Map.entry("required", List.of("object", "action")),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("er_designer"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "auto_layout", "fit_view",
                                        "bind_target", "unbind_target",
                                        "sync_from_db", "diff_against_db", "generate_ddl"
                                ),
                                "description", "ER designer verbs. generate_ddl writes DDL into a new query_editor tab and returns its tabId; the user runs it through L2/L3 confirmation."
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("connectionId", Map.of(
                                                "type", "string",
                                                "description", "Required for bind_target."
                                        )),
                                        Map.entry("database", Map.of("type", "string")),
                                        Map.entry("schema", Map.of("type", "string")),
                                        Map.entry("tables", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "Optional for sync_from_db. Subset of tables to refresh from the bound DB; empty = all bound tables."
                                        )),
                                        Map.entry("includeDrops", Map.of(
                                                "type", "boolean",
                                                "default", Boolean.FALSE,
                                                "description", "Optional for generate_ddl. Day-1 always false; reserved for later phases."
                                        ))
                                )
                        ))
                )),
                Map.entry("allOf", List.of(
                        actionRequiresParams("er_designer", "bind_target", List.of("connectionId"))
                ))
        );
    }

    private static Map<String, Object> dashboardExecSchema() {
        return Map.ofEntries(
                Map.entry("required", List.of("object", "action")),
                Map.entry("properties", Map.ofEntries(
                        Map.entry("object", Map.of("type", "string", "enum", List.of("dashboard"))),
                        Map.entry("action", Map.of(
                                "type", "string",
                                "enum", List.of("create", "focus"),
                                "description", "Dashboard verbs. create generates a new dashboard from a JSON payload; focus activates a dashboard tab."
                        )),
                        Map.entry("params", Map.of(
                                "type", "object",
                                "properties", Map.ofEntries(
                                        Map.entry("dashboardJson", Map.of(
                                                "type", "object",
                                                "description", "Required for create: the full dashboard JSON payload conforming to dashboard-schema.json."
                                        )),
                                        Map.entry("target", Map.of(
                                                "type", "string",
                                                "description", "Required for focus: the dashboard id."
                                        ))
                                )
                        ))
                )),
                Map.entry("allOf", List.of(
                        actionRequiresParams("dashboard", "create", List.of("dashboardJson")),
                        actionRequiresParams("dashboard", "focus", List.of("target"))
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
