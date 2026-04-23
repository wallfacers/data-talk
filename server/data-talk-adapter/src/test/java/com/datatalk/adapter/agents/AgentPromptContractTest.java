package com.datatalk.adapter.agents;

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

    private static final Pattern ACTION_ID_PATTERN = Pattern.compile("datatalk(?:\\.[a-z_]+)+");
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    @Autowired
    ActionRegistry registry;

    @Autowired
    ObjectMapper om;

    @Test
    void runtimePromptReferencesOnlyRegisteredActions() throws IOException {
        String prompt = loadPrompt();
        Set<String> promptActionIds = ACTION_ID_PATTERN.matcher(prompt)
            .results()
            .filter(result -> {
                int end = result.end();
                if (end >= prompt.length()) {
                    return true;
                }
                char next = prompt.charAt(end);
                return next != '.' && next != '*';
            })
            .map(result -> result.group())
            .collect(Collectors.toSet());

        Set<String> registeredActionIds = registry.all().stream()
            .map(action -> action.id())
            .collect(Collectors.toSet());

        assertThat(promptActionIds).isSubsetOf(registeredActionIds);
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
            .doesNotContain("workspace.open")
            .doesNotContain("workspace.choose_connection");
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
