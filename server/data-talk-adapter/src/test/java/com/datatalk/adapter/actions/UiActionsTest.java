package com.datatalk.adapter.actions;

import com.datatalk.application.registry.JsonSchemaLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UiActionsTest {

    private final UiReadAction uiReadAction = new UiReadAction();
    private final UiPatchAction uiPatchAction = new UiPatchAction();
    private final UiExecAction uiExecAction = new UiExecAction();
    private final JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());

    @Test
    void uiReadAction_declaresClientExecutorAndUiCategory() {
        DataTalkAction ann = UiReadAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.read");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }

    @Test
    void uiPatchAction_declaresClientExecutorAndUiCategory() {
        DataTalkAction ann = UiPatchAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.patch");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }

    @Test
    void uiExecAction_usesLongTimeout() {
        DataTalkAction ann = UiExecAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.exec");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.timeoutMs()).isGreaterThanOrEqualTo(30_000);
    }

    @Test
    void uiPatchAction_declaresLenientOpsAndNumericTopLevelBaseVersion() {
        Map<String, Object> schema = uiPatchAction.inputSchema();
        Map<String, Object> properties = map(schema.get("properties"), "properties");
        Map<String, Object> baseVersion = map(properties.get("baseVersion"), "baseVersion");
        Map<String, Object> ops = map(properties.get("ops"), "ops");
        Map<String, Object> item = map(ops.get("items"), "items");
        Map<String, Object> op = navigate(item, "properties", "op");

        assertThat(baseVersion.get("type")).isEqualTo("number");
        assertThat(baseVersion.get("description"))
            .asString()
            .contains("query_editor /content")
            .contains("numeric")
            .doesNotContain("'auto' (default)");
        assertThat(list(item.get("required"), "required")).containsExactlyInAnyOrder("op", "path");
        assertThat(list(op.get("enum"), "enum")).containsExactlyInAnyOrder("add", "remove", "replace");
    }

    @Test
    void uiPatchAction_requiresBaseVersionForQueryEditorContentReplace() {
        Map<String, Object> schema = uiPatchAction.inputSchema();

        assertInvalid(schema, Map.of(
            "object", "query_editor",
            "ops", List.of(Map.of("op", "replace", "path", "/content", "value", "select 2"))
        ));
        assertValid(schema, Map.of(
            "object", "query_editor",
            "baseVersion", 7,
            "ops", List.of(Map.of("op", "replace", "path", "/content", "value", "select 2"))
        ));
        assertValid(schema, Map.of(
            "object", "query_editor",
            "ops", List.of(Map.of("op", "replace", "path", "/database", "value", "analytics"))
        ));
    }

    @Test
    void uiPatchAction_requiresValueForReplaceOps() {
        Map<String, Object> schema = uiPatchAction.inputSchema();

        assertInvalid(schema, Map.of(
            "object", "query_editor",
            "ops", List.of(Map.of("op", "replace", "path", "/database"))
        ));
    }

    @Test
    void uiPatchAction_requiresValueForAddOps() {
        Map<String, Object> schema = uiPatchAction.inputSchema();

        assertInvalid(schema, Map.of(
            "object", "er_designer",
            "baseVersion", 0,
            "ops", List.of(Map.of("op", "add", "path", "/tables/-"))
        ));
    }

    @Test
    void uiPatchAction_requiresBaseVersionForErDesignerStructuralPatches() {
        Map<String, Object> schema = uiPatchAction.inputSchema();

        assertInvalid(schema, Map.of(
            "object", "er_designer",
            "ops", List.of(Map.of(
                "op", "add",
                "path", "/tables/-",
                "value", Map.of("name", "orders", "columns", List.of(), "indexes", List.of(), "uniques", List.of())
            ))
        ));
        assertValid(schema, Map.of(
            "object", "er_designer",
            "baseVersion", 0,
            "ops", List.of(Map.of(
                "op", "add",
                "path", "/tables/-",
                "value", Map.of("name", "orders", "columns", List.of(), "indexes", List.of(), "uniques", List.of())
            ))
        ));
        assertValid(schema, Map.of(
            "object", "er_designer",
            "ops", List.of(Map.of(
                "op", "replace",
                "path", "/viewport",
                "value", Map.of("x", 0, "y", 0, "zoom", 1)
            ))
        ));
    }

    @Test
    void uiReadAction_acceptsErTabObjectsSoAgentsCanReadVersionedState() {
        Map<String, Object> schema = uiReadAction.inputSchema();
        Map<String, Object> object = navigate(schema, "properties", "object");

        assertThat(list(object.get("enum"), "enum"))
            .contains("workspace", "query_editor", "er_inspector", "er_designer");
    }

    @Test
    void uiExec_applyTextEdits_eachEditRequiresExpectedText() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> qeBranch = findOneOfBranch(schema, "query_editor");
        Map<String, Object> editsParam = navigate(qeBranch, "properties", "params", "properties", "edits");
        Map<String, Object> editItem = map(editsParam.get("items"), "items");

        assertThat(list(editItem.get("required"), "required"))
            .containsExactlyInAnyOrder("range", "text", "expectedText");
    }

    @Test
    void uiExec_workspaceAction_includesNewVerbs() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> action = navigate(wsBranch, "properties", "action");

        assertThat(list(action.get("enum"), "enum"))
            .containsExactlyInAnyOrder(
                "open", "focus", "choose_connection", "detach", "archive", "trash",
                "rename", "pin",
                "open_er_inspector", "open_er_designer"
            )
            .doesNotContain("close");
    }

    @Test
    void uiExec_workspaceOpenPayloadDocumentsSqlContentAliases() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> payload = navigate(wsBranch, "properties", "params", "properties", "payload");
        Map<String, Object> payloadProperties = map(payload.get("properties"), "payload.properties");

        assertThat(payload.get("type")).isEqualTo("object");
        assertThat(payloadProperties.keySet())
            .contains("initialSql", "content", "sql", "autoRun", "connectionId", "connectionName", "database", "schema");
        assertThat(map(payloadProperties.get("content"), "content").get("description"))
            .asString()
            .contains("SQL");
    }

    @Test
    void uiExec_erInspectorBranch_exposesInspectorVerbs() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> erBranch = findOneOfBranch(schema, "er_inspector");
        Map<String, Object> action = navigate(erBranch, "properties", "action");

        assertThat(list(action.get("enum"), "enum"))
            .containsExactlyInAnyOrder("refresh", "auto_layout", "fit_view", "add_neighbors", "fork_to_designer");
    }

    @Test
    void uiExec_erDesignerBranch_exposesDesignerVerbs() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> erBranch = findOneOfBranch(schema, "er_designer");
        Map<String, Object> action = navigate(erBranch, "properties", "action");
        Map<String, Object> params = navigate(erBranch, "properties", "params", "properties");

        assertThat(list(action.get("enum"), "enum"))
            .containsExactlyInAnyOrder(
                "auto_layout", "fit_view",
                "bind_target", "unbind_target",
                "sync_from_db", "diff_against_db", "generate_ddl"
            );
        assertThat(action.get("description"))
            .asString()
            .contains("query_editor")
            .contains("L2/L3 confirmation");
        assertThat(params.keySet())
            .contains("connectionId", "database", "schema", "tables", "includeDrops");
    }

    @Test
    void uiPatchAction_acceptsErInspectorObject() {
        Map<String, Object> schema = uiPatchAction.inputSchema();
        Map<String, Object> object = navigate(schema, "properties", "object");

        assertThat(list(object.get("enum"), "enum"))
            .contains("query_editor", "er_inspector", "er_designer");
    }

    @Test
    void uiExec_chooseConnectionSchemaDocumentsDatabaseIntentGate() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> action = navigate(wsBranch, "properties", "action");

        assertThat(action.get("description"))
            .asString()
            .contains("choose_connection only when a database-related request needs a data source");
    }

    @Test
    void uiExec_queryEditorAction_removesCloseAlias() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> qeBranch = findOneOfBranch(schema, "query_editor");
        Map<String, Object> action = navigate(qeBranch, "properties", "action");

        assertThat(list(action.get("enum"), "enum"))
            .containsExactlyInAnyOrder("apply_text_edits", "set_context", "run_sql", "format_sql", "focus")
            .doesNotContain("close");
    }

    @Test
    void uiExecAction_rejectsMissingActionSpecificRequiredParams() {
        Map<String, Object> schema = uiExecAction.inputSchema();

        assertInvalid(schema, Map.of("object", "workspace", "action", "open", "params", Map.of()));
        assertValid(schema, Map.of("object", "workspace", "action", "open", "params", Map.of("type", "query_editor")));

        assertInvalid(schema, Map.of("object", "workspace", "action", "focus", "params", Map.of()));
        assertValid(schema, Map.of("object", "workspace", "action", "focus", "params", Map.of("target", "qe-1")));

        assertInvalid(schema, Map.of("object", "workspace", "action", "open_er_inspector", "params", Map.of(
            "tables", List.of("orders")
        )));
        assertValid(schema, Map.of("object", "workspace", "action", "open_er_inspector", "params", Map.of(
            "connectionId", "conn-1",
            "tables", List.of("orders")
        )));

        assertInvalid(schema, Map.of("object", "workspace", "action", "open_er_designer", "params", Map.of()));
        assertValid(schema, Map.of("object", "workspace", "action", "open_er_designer", "params", Map.of("dialect", "mysql")));

        assertInvalid(schema, Map.of("object", "query_editor", "action", "set_context", "params", Map.of()));
        assertValid(schema, Map.of("object", "query_editor", "action", "set_context", "params", Map.of("database", "analytics")));

        assertInvalid(schema, Map.of("object", "er_inspector", "action", "add_neighbors", "params", Map.of()));
        assertValid(schema, Map.of("object", "er_inspector", "action", "add_neighbors", "params", Map.of("table", "orders")));

        assertInvalid(schema, Map.of("object", "er_designer", "action", "bind_target", "params", Map.of()));
        assertValid(schema, Map.of("object", "er_designer", "action", "bind_target", "params", Map.of("connectionId", "conn-1")));
    }

    @Test
    void uiExec_workspaceArchive_archivedFlagOptionalDefaultTrue() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> archivedFlag = navigate(wsBranch, "properties", "params", "properties", "archived");

        assertThat(archivedFlag.get("type")).isEqualTo("boolean");
        assertThat(archivedFlag.get("default")).isEqualTo(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findOneOfBranch(Map<String, Object> schema, String objectEnumValue) {
        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");
        return oneOf.stream()
            .filter(branch -> list(map(map(branch.get("properties"), "properties").get("object"), "object").get("enum"), "enum")
                .contains(objectEnumValue))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> navigate(Map<String, Object> root, String... path) {
        Map<String, Object> cur = root;
        for (String key : path) {
            cur = (Map<String, Object>) cur.get(key);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String label) {
        assertThat(value)
            .as(label)
            .isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object value, String label) {
        assertThat(value)
            .as(label)
            .isInstanceOf(List.class);
        return (List<T>) value;
    }

    private void assertValid(Map<String, Object> schema, Map<String, Object> input) {
        assertThat(schemas.validate(schema, input).errors())
            .as("expected valid input: %s", input)
            .isEmpty();
    }

    private void assertInvalid(Map<String, Object> schema, Map<String, Object> input) {
        assertThat(schemas.validate(schema, input).errors())
            .as("expected invalid input: %s", input)
            .isNotEmpty();
    }

}
