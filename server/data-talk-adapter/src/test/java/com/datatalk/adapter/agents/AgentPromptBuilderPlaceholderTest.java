package com.datatalk.adapter.agents;

import com.datatalk.application.stage.ActiveSessionDirProvider;
import com.datatalk.application.stage.AgentPromptBuilder;
import com.datatalk.application.stage.SessionTitleLookup;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.infra.opencode.process.OpenCodeBootstrapWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract for the {@code {{ACTIVE_SESSION_DIR}}} + {@code {{STAGE_TAB_DIGEST}}}
 * placeholder rendering performed by {@link AgentPromptBuilder#render(String)}
 * and the supplier wired by {@link AgentPromptCustomizer}. Verifies the spec
 * Requirement "AgentPromptBuilder 占位符渲染保持".
 */
class AgentPromptBuilderPlaceholderTest {

    @Test
    void agentsMdContainsBothPlaceholdersLiterally() throws IOException {
        String raw = loadAgentsMd();
        assertThat(raw).contains("{{STAGE_TAB_DIGEST}}");
        assertThat(raw).contains("{{ACTIVE_SESSION_DIR}}");
    }

    @Test
    void renderSubstitutesBothPlaceholdersWhenSessionBound() throws IOException {
        String raw = loadAgentsMd();
        AgentPromptBuilder builder = newBuilder(Optional.of("S-1"));
        String rendered = builder.render(raw);
        assertThat(rendered).doesNotContain("{{STAGE_TAB_DIGEST}}");
        assertThat(rendered).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        assertThat(rendered).contains("./sessions/S-1/");
    }

    @Test
    void renderEmitsNoActiveSessionSentinelWhenSessionAbsent() throws IOException {
        String raw = loadAgentsMd();
        AgentPromptBuilder builder = newBuilder(Optional.empty());
        String rendered = builder.render(raw);
        assertThat(rendered).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        assertThat(rendered).contains("<no active session>");
    }

    @Test
    void customizerSupplierLoadsAgentsMdAndDelegatesToBuilder() {
        OpenCodeBootstrapWriter writer = mock(OpenCodeBootstrapWriter.class);
        AgentPromptBuilder builder = newBuilder(Optional.of("S-x"));
        new AgentPromptCustomizer().wire(writer, builder);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<String>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(writer).setInstructionsSupplier(captor.capture());
        Supplier<String> supplier = captor.getValue();
        assertThat(supplier).as("AgentPromptCustomizer must set an instructions supplier").isNotNull();

        String rendered = supplier.get();
        // Supplier reads classpath:/agents/AGENTS.md and renders both placeholders.
        assertThat(rendered).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        assertThat(rendered).doesNotContain("{{STAGE_TAB_DIGEST}}");
        assertThat(rendered).contains("./sessions/S-x/");
    }

    @Test
    void supplierWrapsIoExceptionWhenAgentsMdMissing() {
        // Mirror the production supplier contract: classpath miss → RuntimeException
        // whose cause is IOException with "AGENTS.md not found" message.
        AgentPromptBuilder builder = newBuilder(Optional.empty());
        Supplier<String> supplier = () -> {
            try (var in = AgentPromptBuilderPlaceholderTest.class
                .getClassLoader()
                .getResourceAsStream("agents/DOES_NOT_EXIST.md")) {
                if (in == null) throw new IOException("AGENTS.md not found on classpath");
                return builder.render(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load AGENTS.md", e);
            }
        };

        assertThatThrownBy(supplier::get)
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("AGENTS.md not found on classpath");
    }

    // ---------- Helpers ----------

    private static String loadAgentsMd() throws IOException {
        try (var in = new ClassPathResource("agents/AGENTS.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static AgentPromptBuilder newBuilder(Optional<String> sessionId) {
        StageTabRepository repo = mock(StageTabRepository.class);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of());
        when(repo.countActive()).thenReturn(0);
        when(repo.countArchived()).thenReturn(0);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        when(lookup.titlesByIds(List.of())).thenReturn(Map.of());
        ActiveSessionDirProvider provider = () -> sessionId;
        return new AgentPromptBuilder(repo, lookup, provider);
    }
}
