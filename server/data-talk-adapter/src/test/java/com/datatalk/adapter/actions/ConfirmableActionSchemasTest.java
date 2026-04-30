package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionContextRefreshService;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.application.session.SessionDataContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfirmableActionSchemasTest {

    private final JsonSchemaLoader schemas = new JsonSchemaLoader(new ObjectMapper());

    @Test
    void terminateSession_requiresConfirmationTokenWhenConfirmIsTrue() {
        Map<String, Object> schema = new TerminateSessionConfirmableAction(
            mock(DiagnosticsService.class),
            mock(Translator.class)
        ).inputSchema();

        assertInvalid(schema, Map.of("sessionId", "42", "confirm", true));
        assertValid(schema, Map.of("sessionId", "42", "confirm", false));
        assertValid(schema, Map.of(
            "sessionId", "42",
            "confirm", true,
            "confirmationToken", "token"
        ));
    }

    @Test
    void optimizeTable_requiresConfirmationTokenWhenConfirmIsTrue() {
        Map<String, Object> schema = new OptimizeTableConfirmableAction(
            mock(DiagnosticsService.class),
            mock(Translator.class)
        ).inputSchema();

        assertInvalid(schema, Map.of("table", "users", "confirm", true));
        assertValid(schema, Map.of("table", "users", "confirm", false));
        assertValid(schema, Map.of(
            "table", "users",
            "confirm", true,
            "confirmationToken", "token"
        ));
    }

    @Test
    void updateConnection_requiresConfirmationTokenWhenConfirmIsTrue() {
        Map<String, Object> schema = new UpdateConnectionConfirmableAction(
            mock(ConnectionService.class),
            mock(ConnectionContextRefreshService.class),
            mock(ConnectionRepository.class),
            mock(SessionDataContextService.class),
            mock(Translator.class)
        ).inputSchema();

        Map<String, Object> base = Map.of(
            "connectionId", "conn-1",
            "name", "Main",
            "kind", "h2",
            "host", "localhost",
            "port", 0,
            "username", "sa"
        );

        assertInvalid(schema, with(base, "confirm", true));
        assertValid(schema, with(base, "confirm", false));
        assertValid(schema, with(with(base, "confirm", true), "confirmationToken", "token"));
    }

    private void assertValid(Map<String, Object> schema, Map<String, Object> input) {
        assertThat(schemas.validate(schema, input).errors())
            .as("expected valid input: %s", input)
            .isEmpty();
    }

    private void assertInvalid(Map<String, Object> schema, Map<String, Object> input) {
        assertThat(schemas.validate(schema, input).errors())
            .as("expected invalid input: %s", input)
            .isNotEmpty();
    }

    private static Map<String, Object> with(Map<String, Object> source, String key, Object value) {
        var copy = new java.util.LinkedHashMap<>(source);
        copy.put(key, value);
        return copy;
    }
}
