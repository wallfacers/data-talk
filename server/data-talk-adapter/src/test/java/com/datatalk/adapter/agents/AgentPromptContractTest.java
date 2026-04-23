package com.datatalk.adapter.agents;

import com.datatalk.application.registry.ActionRegistry;
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

    @Test
    void runtimePromptReferencesOnlyRegisteredActions() throws IOException {
        String prompt = loadPrompt();
        Set<String> promptActionIds = ACTION_ID_PATTERN.matcher(prompt)
            .results()
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
            .doesNotContain("er_canvas")
            .doesNotContain("markdown_note")
            .doesNotContain("report")
            .doesNotContain("dashboard");
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
