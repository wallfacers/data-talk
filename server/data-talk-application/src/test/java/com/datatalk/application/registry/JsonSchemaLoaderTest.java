package com.datatalk.application.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaLoaderTest {
    private final JsonSchemaLoader loader = new JsonSchemaLoader(new ObjectMapper());

    @Test
    void acceptsValidInput() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", java.util.List.of("name"),
            "properties", Map.of("name", Map.of("type", "string"))
        );
        var result = loader.validate(schema, Map.of("name", "Alice"));
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingRequired() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", java.util.List.of("name"),
            "properties", Map.of("name", Map.of("type", "string"))
        );
        var result = loader.validate(schema, Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }
}
