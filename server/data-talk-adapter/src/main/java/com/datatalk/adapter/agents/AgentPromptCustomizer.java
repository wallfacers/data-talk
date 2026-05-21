package com.datatalk.adapter.agents;

import com.datatalk.application.stage.AgentPromptBuilder;
import com.datatalk.infra.opencode.process.OpenCodeBootstrapWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Wires the {@link AgentPromptBuilder} into the {@link OpenCodeBootstrapWriter}
 * so that {@code {{STAGE_TAB_DIGEST}}} in AGENTS.md is resolved at bootstrap time.
 */
@Configuration
public class AgentPromptCustomizer {
    @Autowired
    void wire(OpenCodeBootstrapWriter writer, AgentPromptBuilder builder) {
        writer.setInstructionsSupplier(() -> {
            try (var in = getClass().getClassLoader().getResourceAsStream("agents/AGENTS.md")) {
                if (in == null) throw new IOException("agents/AGENTS.md not found on classpath");
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return builder.render(raw);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load AGENTS.md", e);
            }
        });
    }
}
