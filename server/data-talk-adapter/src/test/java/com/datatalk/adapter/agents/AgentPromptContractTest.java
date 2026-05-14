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

/**
 * Post-refactor contract for AGENTS.md after the agents-md-skills-refactor change.
 *
 * <p>Before the refactor, this class held 29 tests, ~20 of which asserted exact
 * substrings from sections that have now moved into individual skill files
 * (Schema Reading Rules, TiDB / DuckDB dialect detail, ER protocol, Concurrency
 * Contract, Dashboards, per-tool {@code Required input} blocks). Those assertions
 * have been deleted — the corresponding invariants are now enforced via
 * {@link SkillRoutingContractTest}'s per-skill structural checks and per-skill
 * boundary tests.</p>
 *
 * <p>What survives here are assertions that remain valid against the new
 * skeleton ({@code datatalk_*} tool subset, English-only prompt, registered
 * action surfaces) plus pure {@link ActionRegistry} schema assertions that
 * don't depend on AGENTS.md content.</p>
 */
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
            .doesNotContain("`report`");
    }

    @Test
    void renderedTemplateRegistersUiFindAndDoesNotMentionUiList() throws IOException {
        String prompt = loadPrompt();
        assertThat(prompt).contains("datatalk_ui_find");
        assertThat(prompt).doesNotContain("datatalk_ui_list");
    }

    @Test
    void renderedTemplateContainsTabSnapshotPlaceholderOrSection() throws IOException {
        String prompt = loadPrompt();
        // Raw template contains the placeholder; runtime-rendered prompt contains the resolved section.
        assertThat(prompt)
            .satisfiesAnyOf(
                p -> assertThat(p).contains("{{STAGE_TAB_DIGEST}}"),
                p -> assertThat(p).contains("## Open Tabs Snapshot")
            );
    }

    @Test
    void productionRegistryDoesNotExposeLegacyDemoEchoAction() {
        Set<String> registeredActionIds = registry.all().stream()
            .map(action -> action.id())
            .collect(Collectors.toSet());

        assertThat(registeredActionIds).doesNotContain("datatalk.demo.echo");
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

    private static String loadPrompt() throws IOException {
        return new ClassPathResource("agents/AGENTS.md")
            .getContentAsString(StandardCharsets.UTF_8);
    }
}
