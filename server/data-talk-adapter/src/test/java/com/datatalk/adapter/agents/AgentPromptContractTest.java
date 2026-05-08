package com.datatalk.adapter.agents;

import com.datatalk.application.opencode.McpNameMapper;
import com.datatalk.application.registry.ActionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentPromptContractTest {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("datatalk_[a-z_]+");
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    @Autowired
    ActionRegistry registry;

    @Autowired
    ObjectMapper om;

    @Test
    void runtimePromptReferencesOnlyRegisteredMcpTools() throws IOException {
        String prompt = loadPrompt();
        Set<String> promptToolNames = TOOL_NAME_PATTERN.matcher(prompt)
            .results()
            .map(result -> result.group())
            .collect(Collectors.toSet());

        McpNameMapper mapper = new McpNameMapper(registry.mcpExposed());
        Set<String> registeredToolNames = mapper.openCodeToolNames().stream()
            .collect(Collectors.toSet());

        assertThat(promptToolNames).isSubsetOf(registeredToolNames);
    }

    @Test
    void runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets() throws IOException {
        String prompt = loadPrompt();

        assertThat(HAN_PATTERN.matcher(prompt).find())
            .as("runtime prompt should stay English-only")
            .isFalse();
        assertThat(prompt)
            .doesNotContain("`er_canvas`")
            .doesNotContain("`markdown_note`")
            .doesNotContain("`report`")
            .doesNotContain("`dashboard`");
    }

    @Test
    void runtimePromptDocumentsExactUiExecShapesAndContextBoundaries() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("session data context")
            .contains("query editor context")
            .contains("filter.type=query_editor")
            .contains("connectionId")
            .contains("database")
            .contains("Each patch op is shaped like")
            .contains("object=workspace")
            .contains("action=open")
            .contains("params.type=query_editor")
            .contains("params.connection_id")
            .contains("params.target")
            .contains("object=query_editor")
            .contains("action=apply_text_edits")
            .contains("params.baseVersion")
            .contains("expectedText")
            .contains("target=active")
            .contains("set_context({ useSessionContext: true })")
            .contains("database requires an effective connectionId")
            .contains("schema requires an effective connectionId and database")
            .contains("limit")
            .contains("datatalk_ui_find")
            .contains("datatalk_ui_read")
            .contains("datatalk_ui_patch")
            .contains("datatalk_ui_exec")
            .doesNotContain("workspace.open")
            .doesNotContain("workspace.choose_connection")
            .doesNotContain("datatalk.ui.");
    }

    @Test
    void runtimePromptRequiresExplicitDatabaseSchemaDisambiguationForTableErrors() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("matches multiple candidates")
            .contains("Select a database/schema first")
            .contains("do not say the database has no data")
            .contains("datatalk_list_connection_targets")
            .contains("ask the user to choose");
    }

    @Test
    void runtimePromptRequiresFullResolvedTargetForContextSwitches() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Do not call `datatalk_set_data_context` with only `database` or only `schema`")
            .contains("copy the full `matched_target` fields")
            .contains("connectionId=<matched_target.connectionId>")
            .contains("selectedLevel=<matched_target.level>");
    }

    @Test
    void runtimePromptDocumentsRequiredInputsForRegisteredServerActions() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Required input: `target`")
            .contains("Required input: `connectionId`")
            .contains("Required input: always include `name` and `kind`")
            .contains("For `kind=sqlite`, include `databaseName` as the SQLite file path or `:memory:`")
            .contains("For `kind=duckdb`, include `databaseName` as the DuckDB file path or `:memory:`")
            .contains("For other kinds, also include `host`, `port`, `username`, and `password`")
            .contains("Required input: always include `connectionId`, `name`, and `kind`")
            .contains("For other kinds, also include `host`, `port`, and `username`")
            .contains("Required input: `sql`")
            .contains("Required input: `echartsOption`")
            .contains("Required input: `newArtifactId` and `oldArtifactId`")
            .contains("Required input: `artifactId`");
    }

    @Test
    void runtimePromptRequiresConfirmationTokenForConfirmedMutationTools() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("When a confirmable mutation tool is called with `confirm=true`, include `confirmationToken`")
            .contains("datatalk_update_connection_confirmable")
            .contains("datatalk_terminate_session")
            .contains("datatalk_optimize_table");
    }

    @Test
    void runtimePromptDocumentsBoundedSchemaReadsAndTruncationHandling() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Schema Reading Rules")
            .contains("explicit `tables`")
            .contains("table discovery")
            .contains("pattern")
            .contains("limit")
            .contains("large schemas")
            .contains("Never pass a large table list")
            .contains("truncated")
            .contains("Do not describe truncation as a tool failure");
    }

    @Test
    void runtimePromptDocumentsSqliteFileScopedConnectionSemantics() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("SQLite is file-scoped")
            .contains("kind=sqlite")
            .contains("databaseName is the SQLite file path or `:memory:`")
            .contains("SQLite has no independent schema selector")
            .contains("do not ask to switch SQLite schemas")
            .contains(":memory:` is ephemeral per JDBC connection");
    }

    @Test
    void runtimePromptDocumentsDuckdbEmbeddedConnectionSemantics() throws IOException {
        var prompt = loadPrompt();
        assertThat(prompt).contains("kind=duckdb");
        assertThat(prompt).contains("embedded");
        // DuckDB should have dialect notes
        assertThat(prompt).contains("DuckDB");
        assertThat(prompt).contains("read_csv");
        assertThat(prompt).contains("read_parquet");
    }

    @Test
    void tidbAppearsInConnectionKindEnum() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("kind: `tidb`")
            .contains("Default port: 4000")
            .contains("Canonical kind: `tidb`")
            .contains("TiDB-only L3")
            .contains("TiDB-only L2")
            .contains("TiDB-only L1")
            .contains("dialect_unsupported on TiDB Day-1");
    }

    @Test
    void agentsMdMentionsTidbCanonicalKind() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Canonical kind: `tidb`")
            .contains("Default port: 4000")
            .contains("Reject any user attempt to map TiDB to `mysql`");
    }

    @Test
    void runtimePromptDocumentsDiagnosticsMutationClosedLoop() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("datatalk_terminate_session")
            .contains("datatalk_optimize_table")
            .contains("Mutation Actions")
            .contains("Lock complaint received")
            .contains("Holder session has been blocking")
            .doesNotContain("Not yet available");
    }

    @Test
    void runtimePromptRoutesTableBrowsingToQueryEditorAndAnalyticsToServerData() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Intent Routing Gate")
            .contains("query editor UI workflow")
            .contains("browse table rows")
            .contains("do not use `datatalk_execute_sql`")
            .contains("server data workflow")
            .contains("analytical question")
            .contains("report")
            .contains("chart");
    }

    @Test
    void runtimePromptClarifiesRoutingEdgeCases() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("single-table `COUNT(*)` without grouping")
            .contains("Grouped counts")
            .contains("query editor result grid")
            .contains("Do not replace unrelated SQL")
            .contains("smallest aggregated result");
    }

    @Test
    void runtimePromptDoesNotRequireConnectionForGeneralChat() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("General chat and product-help requests do not require a data source")
            .contains("Do not call `datatalk_ui_exec` with `action=choose_connection` for greetings")
            .contains("Only prompt the connection chooser when the user asks a database-related question or explicitly uses `!` SQL");
    }

    @Test
    void runtimePromptSpecifiesChartFenceLanguage() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("inline fenced code block with language `chart`")
            .contains("ECharts option JSON")
            .contains("chart:<artifactId>")
            .contains("```chart:art-abc123")
            .doesNotContain("inline fenced chart block");
    }

    @Test
    void registeredUiActionSchemasStayAlignedWithPromptSurface() throws Exception {
        String uiFindSchema = om.writeValueAsString(registry.require("datatalk.ui.find").inputSchema());
        String uiReadSchema = om.writeValueAsString(registry.require("datatalk.ui.read").inputSchema());
        String uiPatchSchema = om.writeValueAsString(registry.require("datatalk.ui.patch").inputSchema());
        String uiExecSchema = om.writeValueAsString(registry.require("datatalk.ui.exec").inputSchema());

        assertThat(uiFindSchema)
            .contains("filter")
            .contains("query")
            .contains("read")
            .contains("output")
            .contains("objectId")
            .contains("originSessionId")
            .contains("connectionId")
            .contains("fts")
            .contains("regex")
            .contains("matches")
            .contains("tabs_only")
            .contains("contextLines");
        assertThat(uiReadSchema)
            .contains("workspace")
            .contains("query_editor")
            .contains("er_inspector")
            .contains("er_designer")
            .contains("target")
            .contains("state")
            .contains("schema")
            .contains("actions")
            .contains("full");
        assertThat(uiPatchSchema)
            .contains("query_editor")
            .contains("er_inspector")
            .contains("er_designer")
            .contains("add")
            .contains("remove")
            .contains("replace")
            .contains("baseVersion")
            .contains("JSON Pointer");
        assertThat(uiExecSchema)
            .contains("workspace")
            .contains("query_editor")
            .contains("er_inspector")
            .contains("choose_connection")
            .contains("open_er_inspector")
            .contains("open_er_designer")
            .contains("auto_layout")
            .contains("add_neighbors")
            .contains("apply_text_edits")
            .contains("set_context")
            .contains("useSessionContext")
            .contains("run_sql")
            .contains("format_sql")
            .contains("connection_id")
            .contains("limit")
            .contains("integer")
            .contains("baseVersion")
            .contains("preferredConnectionId");
    }

    @Test
    void agentsMdReferencesErInspectorVerbs() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("## ER Tabs (Inspector & Designer)")
            .contains("ui_exec(workspace, open_er_inspector")
            .contains("ui_patch(inspector_tab,")
            .contains("filter: { type: \"er_inspector\" }")
            .doesNotContain("datatalk_layout_erd");
    }

    @Test
    void agentsMdReferencesDesignerVerbs() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("er_designer verbs are live")
            .contains("ui_exec(workspace, open_er_designer")
            .contains("ui_exec(designer_tab, bind_target")
            .contains("ui_exec(designer_tab, diff_against_db)")
            .contains("ui_exec(designer_tab, generate_ddl)")
            .contains("DDL lands in a query_editor tab")
            .contains("L2/L3 confirmation");
    }

    @Test
    void runtimePromptDocumentsErDesignerPatchGuards() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("object=er_designer")
            .contains("structural paths")
            .contains("op=add")
            .contains("op=replace")
            .contains("require `value`")
            .contains("view paths")
            .contains("baseVersion: number");
    }

    @Test
    void uiExecActionSchemaContainsOpenErInspectorVerb() {
        Map<String, Object> schema = registry.require("datatalk.ui.exec").inputSchema();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");

        boolean hasOpenErInspector = oneOf.stream()
            .map(s -> (Map<String, Object>) s.get("properties"))
            .map(p -> (Map<String, Object>) p.get("action"))
            .filter(a -> a != null)
            .map(a -> (List<?>) a.get("enum"))
            .filter(en -> en != null)
            .anyMatch(en -> en.contains("open_er_inspector"));

        assertThat(hasOpenErInspector).isTrue();
    }

    @Test
    void uiExecActionSchemaContainsDesignerVerbs() {
        Map<String, Object> schema = registry.require("datatalk.ui.exec").inputSchema();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");

        boolean hasGenerateDdl = oneOf.stream()
            .map(s -> (Map<String, Object>) s.get("properties"))
            .map(p -> (Map<String, Object>) p.get("action"))
            .filter(a -> a != null)
            .map(a -> (List<?>) a.get("enum"))
            .filter(en -> en != null)
            .anyMatch(en -> en.contains("generate_ddl") && en.contains("diff_against_db"));

        assertThat(hasGenerateDdl).isTrue();
    }

    @Test
    void uiExecActionSchemaAdvertisesQueryEditorContextParameters() {
        Map<String, Object> schema = registry.require("datatalk.ui.exec").inputSchema();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");

        Map<String, Object> queryEditorSchema = oneOf.stream()
            .filter(candidate -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) candidate.get("properties");
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) properties.get("object");
                return ((List<?>) object.get("enum")).contains("query_editor");
            })
            .findFirst()
            .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) queryEditorSchema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) properties.get("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> paramProperties = (Map<String, Object>) params.get("properties");

        assertThat(paramProperties.get("useSessionContext"))
            .isEqualTo(Map.of("type", "boolean"));
        assertThat(paramProperties.get("connectionId"))
            .isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(paramProperties.get("database"))
            .isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(paramProperties.get("schema"))
            .isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(paramProperties.get("limit"))
            .isEqualTo(Map.of(
                "type", List.of("integer", "null"),
                "enum", Arrays.asList(10, 100, 1000, null)
            ));
    }

    @Test
    void uiPatchActionSchemaAllowsErInspectorObject() {
        Map<String, Object> schema = registry.require("datatalk.ui.patch").inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) properties.get("object");

        List<?> objectTypes = (List<?>) object.get("enum");
        assertThat(objectTypes.stream().map(String::valueOf)).contains("er_inspector", "er_designer");
    }

    @Test
    void productionRegistryDoesNotExposeLegacyDemoEchoAction() {
        Set<String> registeredActionIds = registry.all().stream()
            .map(action -> action.id())
            .collect(Collectors.toSet());

        assertThat(registeredActionIds).doesNotContain("datatalk.demo.echo");
    }

    @Test
    void renderedTemplateRegistersUiFindAndDoesNotMentionUiList() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt).contains("datatalk_ui_find");
        assertThat(prompt).doesNotContain("datatalk_ui_list");
    }

    @Test
    void renderedTemplateContainsTabSnapshotSection() throws IOException {
        String prompt = loadPrompt();

        // The raw template should contain the placeholder that gets resolved at runtime
        assertThat(prompt)
            .satisfiesAnyOf(
                p -> assertThat(p).contains("{{STAGE_TAB_DIGEST}}"),
                p -> assertThat(p).contains("## Open Tabs Snapshot")
            );
    }

    @Test
    void runtimePromptDocumentsSharedWorkbenchConcurrencyWithoutCloseAlias() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("## Concurrency Contract")
            .contains("## Library vs Workset")
            .contains("workspace-wide objects shared across all chat sessions")
            .contains("error.markdown")
            .contains("baseVersion")
            .contains("expectedText")
            .contains("inWorkset")
            .contains("detach")
            .contains("archive")
            .contains("trash")
            .contains("shared across all sessions and persisted across app restarts");
        assertThat(prompt)
            .doesNotContain("Deprecated since")
            .doesNotContain("deprecated alias")
            .doesNotContain("action=close")
            .doesNotContain("`close`");
    }

    private static String loadPrompt() throws IOException {
        return new ClassPathResource("agents/AGENTS.md")
            .getContentAsString(StandardCharsets.UTF_8);
    }
}
