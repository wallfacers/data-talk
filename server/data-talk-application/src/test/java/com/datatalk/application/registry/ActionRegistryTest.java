package com.datatalk.application.registry;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {ActionRegistry.class, JsonSchemaLoader.class, Translator.class, ActionRegistryTest.TestActions.class})
class ActionRegistryTest {

    @Autowired
    ActionRegistry registry;

    @Test
    void discoversAnnotatedHandlers() {
        assertThat(registry.all()).extracting("id").containsExactlyInAnyOrder("test.alpha", "test.beta");
    }

    @Test
    void returnsDescriptorWithAnnotationMetadata() {
        var d = registry.require("test.alpha");
        assertThat(d.executor()).isEqualTo(Executor.SERVER);
        assertThat(d.description()).isEqualTo("Alpha Translated");
        assertThat(d.timeoutMs()).isEqualTo(12_345);
        assertThat(d.exposeToMcp()).isTrue();
    }

    @Test
    void mcpExposedOnlyIncludesOptedInActions() {
        assertThat(registry.mcpExposed()).extracting("id").containsExactly("test.alpha");
        assertThat(registry.require("test.beta").exposeToMcp()).isFalse();
    }

    @Test
    void throwsForUnknownActionId() {
        assertThatThrownBy(() -> registry.require("test.missing"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("test.missing");
    }

    @Test
    void lookupHandlerReturnsBean() {
        ActionHandler<?, ?> h = registry.handler("test.alpha");
        assertThat(h).isInstanceOf(AlphaHandler.class);
    }

    @Configuration
    static class TestActions {
        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean
        StaticMessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();
            source.addMessage("action.test.alpha", java.util.Locale.ENGLISH, "Alpha Translated");
            source.addMessage("action.test.alpha", java.util.Locale.SIMPLIFIED_CHINESE, "Alpha Translated");
            source.addMessage("error.action.unknown", java.util.Locale.ENGLISH, "Unknown action: {0}");
            source.addMessage("error.action.unknown", java.util.Locale.SIMPLIFIED_CHINESE, "未知操作：{0}");
            return source;
        }

        @Bean
        AlphaHandler alpha() { return new AlphaHandler(); }

        @Bean
        BetaHandler beta() { return new BetaHandler(); }
    }

    @DataTalkAction(id = "test.alpha", executor = Executor.SERVER, description = "action.test.alpha", timeoutMs = 12_345)
    static class AlphaHandler implements ActionHandler<Map, Map> {
        @Override
        public Map<String, Object> inputSchema() { return Map.of("type", "object"); }

        @Override
        public Map<String, Object> outputSchema() { return Map.of("type", "object"); }

        @Override
        public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }

        @Override
        public Class<Map> inputType() { return Map.class; }

        @Override
        public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            return CompletableFuture.completedFuture(input);
        }
    }

    @DataTalkAction(
        id = "test.beta",
        executor = Executor.CLIENT,
        description = "Beta",
        requiresConnection = true,
        exposeToMcp = false
    )
    static class BetaHandler implements ActionHandler<Map, Map> {
        @Override
        public Map<String, Object> inputSchema() { return Map.of("type", "object"); }

        @Override
        public Map<String, Object> outputSchema() { return Map.of("type", "object"); }

        @Override
        public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }

        @Override
        public Class<Map> inputType() { return Map.class; }

        @Override
        public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }
}
