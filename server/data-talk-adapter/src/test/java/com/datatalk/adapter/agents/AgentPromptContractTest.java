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
            .contains("target=active")
            .contains("datatalk_ui_list")
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
    void runtimePromptDocumentsBoundedSchemaReadsAndTruncationHandling() throws IOException {
        String prompt = loadPrompt();

        assertThat(prompt)
            .contains("Schema Reading Rules")
            .contains("explicit `tables`")
            .contains("table discovery")
            .contains("truncated")
            .contains("Do not describe truncation as a tool failure");
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
        String uiListSchema = om.writeValueAsString(registry.require("datatalk.ui.list").inputSchema());
        String uiReadSchema = om.writeValueAsString(registry.require("datatalk.ui.read").inputSchema());
        String uiPatchSchema = om.writeValueAsString(registry.require("datatalk.ui.patch").inputSchema());
        String uiExecSchema = om.writeValueAsString(registry.require("datatalk.ui.exec").inputSchema());

        assertThat(uiListSchema)
            .contains("workspace")
            .contains("query_editor")
            .contains("keyword")
            .contains("connectionId")
            .contains("database");
        assertThat(uiReadSchema)
            .contains("workspace")
            .contains("query_editor")
            .contains("target")
            .contains("state")
            .contains("schema")
            .contains("actions")
            .contains("full");
        assertThat(uiPatchSchema)
            .contains("query_editor")
            .contains("replace")
            .contains("/content")
            .contains("/connectionId")
            .contains("/database")
            .contains("/schema");
        assertThat(uiExecSchema)
            .contains("workspace")
            .contains("query_editor")
            .contains("choose_connection")
            .contains("apply_text_edits")
            .contains("set_context")
            .contains("run_sql")
            .contains("format_sql")
            .contains("connection_id")
            .contains("baseVersion")
            .contains("preferredConnectionId");
    }

    @Test
    void productionRegistryDoesNotExposeLegacyDemoEchoAction() {
        Set<String> registeredActionIds = registry.all().stream()
            .map(action -> action.id())
            .collect(Collectors.toSet());

        assertThat(registeredActionIds).doesNotContain("datatalk.demo.echo");
    }

    private static String loadPrompt() throws IOException {
        return new ClassPathResource("agents/AGENTS.md")
            .getContentAsString(StandardCharsets.UTF_8);
    }
}
