package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.dto.ConnectionDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListConnectionsActionTest {

    private final ConnectionService connections = mock(ConnectionService.class);
    private final SessionDataContextService sessionContexts = mock(SessionDataContextService.class);
    private final ListConnectionsAction action = new ListConnectionsAction(connections, sessionContexts);

    private static ConnectionDto conn(String id, String name) {
        return new ConnectionDto(
            id, name, "h2", "localhost", 0,
            null, "sa", 1L, 3000, null, null,
            null, false, false, null, false,
            null, null, null
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void listConnections_includesActiveMarker_whenSessionHasConnection() {
        when(connections.list()).thenReturn(List.of(conn("C-1", "alpha"), conn("C-2", "beta"), conn("C-3", "gamma")));
        when(sessionContexts.get("S-1")).thenReturn(new SessionDataContextRecord(
            "S-1", "C-2", "beta", null, null, "connection", 100L
        ));

        Map<String, Object> out = action.handle(new ActionContext("S-1", "call-1", null, "oc-1"), Map.of())
            .toCompletableFuture().join();

        assertThat(out).containsEntry("activeSessionConnectionId", "C-2");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("connections");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("id", "C-1").containsEntry("isActiveInSession", false);
        assertThat(rows.get(1)).containsEntry("id", "C-2").containsEntry("isActiveInSession", true);
        assertThat(rows.get(2)).containsEntry("id", "C-3").containsEntry("isActiveInSession", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listConnections_emitsNullActiveAndFalseFlags_whenSessionHasNoConnection() {
        when(connections.list()).thenReturn(List.of(conn("C-1", "alpha"), conn("C-2", "beta")));
        when(sessionContexts.get("S-2")).thenReturn(new SessionDataContextRecord(
            "S-2", null, null, null, null, null, 100L
        ));

        Map<String, Object> out = action.handle(new ActionContext("S-2", "call-2", null, "oc-2"), Map.of())
            .toCompletableFuture().join();

        assertThat(out).containsKey("activeSessionConnectionId");
        assertThat(out.get("activeSessionConnectionId")).isNull();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("connections");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("isActiveInSession", false);
        assertThat(rows.get(1)).containsEntry("isActiveInSession", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listConnections_treatsMissingSessionAsNoActive() {
        when(connections.list()).thenReturn(List.of(conn("C-1", "alpha")));
        when(sessionContexts.get("S-missing")).thenThrow(new java.util.NoSuchElementException("session not found"));

        Map<String, Object> out = action.handle(new ActionContext("S-missing", "call-3", null, "oc-3"), Map.of())
            .toCompletableFuture().join();

        assertThat(out).containsKey("activeSessionConnectionId");
        assertThat(out.get("activeSessionConnectionId")).isNull();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("connections");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("isActiveInSession", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputSchema_declaresActiveMarkerFields() {
        Map<String, Object> schema = action.outputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(properties).containsKey("activeSessionConnectionId");
        Map<String, Object> activeIdSchema = (Map<String, Object>) properties.get("activeSessionConnectionId");
        Object activeIdType = activeIdSchema.get("type");
        if (activeIdType instanceof List<?> typeList) {
            assertThat(typeList.stream().map(String::valueOf).toList())
                .contains("string", "null");
        } else {
            assertThat(activeIdType).isEqualTo("string");
        }

        Map<String, Object> connectionsSchema = (Map<String, Object>) properties.get("connections");
        Map<String, Object> items = (Map<String, Object>) connectionsSchema.get("items");
        Map<String, Object> itemProperties = (Map<String, Object>) items.get("properties");
        assertThat(itemProperties).containsKey("isActiveInSession");
        assertThat(((Map<String, Object>) itemProperties.get("isActiveInSession")).get("type")).isEqualTo("boolean");
        assertThat((List<String>) items.get("required")).contains("isActiveInSession");
    }
}
