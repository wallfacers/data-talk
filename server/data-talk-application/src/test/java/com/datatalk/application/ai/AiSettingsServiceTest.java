package com.datatalk.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSettingsServiceTest {

    @Test
    void models_merges_opencode_list_with_disabled_set() throws Exception {
        var om = new ObjectMapper();
        var client = mock(OpenCodeProviderClient.class);
        when(client.listProviders()).thenReturn(om.readTree("""
            {"all":[{"id":"openai","name":"OpenAI","models":{
                "gpt-5":{"id":"gpt-5","name":"GPT-5"},
                "gpt-5-nano":{"id":"gpt-5-nano","name":"GPT-5 Nano"}}}],
             "connected":["openai"]}
            """));
        var modelPrefs = mock(AiModelPrefsRepository.class);
        when(modelPrefs.disabledSet()).thenReturn(Set.of("openai/gpt-5-nano"));
        var userPrefs = mock(AiUserPrefsRepository.class);
        var svc = new AiSettingsService(client, modelPrefs, userPrefs, om);

        var out = svc.listModels();
        assertThat(out.providers()).hasSize(1);
        var p = out.providers().get(0);
        assertThat(p.id()).isEqualTo("openai");
        assertThat(p.connected()).isTrue();
        assertThat(p.models()).extracting("id", "enabled")
            .containsExactlyInAnyOrder(
                tuple("gpt-5", true),
                tuple("gpt-5-nano", false));
    }

    @Test
    void models_skips_disconnected_providers() throws Exception {
        var om = new ObjectMapper();
        var client = mock(OpenCodeProviderClient.class);
        when(client.listProviders()).thenReturn(om.readTree("""
            {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":[]}
            """));
        var modelPrefs = mock(AiModelPrefsRepository.class);
        when(modelPrefs.disabledSet()).thenReturn(Set.of());
        var svc = new AiSettingsService(client, modelPrefs,
            mock(AiUserPrefsRepository.class), om);

        var out = svc.listModels();
        assertThat(out.providers().get(0).connected()).isFalse();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... vals) {
        return org.assertj.core.groups.Tuple.tuple(vals);
    }
}
