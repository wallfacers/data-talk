package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SyntheticSessionMessageRecord;
import com.datatalk.application.persistence.SyntheticSessionMessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HistoryServiceTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void getMessagesReturnsEmptyArrayWhenSessionHasNoOpenCodeBinding() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        SyntheticSessionMessageRepository syntheticMessages = mock(SyntheticSessionMessageRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", false, null, 0L, 0L, false)));
        when(syntheticMessages.findBySession("dt-1")).thenReturn(List.of());

        HistoryService svc = new HistoryService(sessions, artifacts, syntheticMessages, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
        verify(gateway, never()).listMessages(anyString(), any());
    }

    @Test
    void getMessagesForwardsToGatewayWhenOpenCodeSidExists() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        SyntheticSessionMessageRepository syntheticMessages = mock(SyntheticSessionMessageRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", true, "ses_oc", 0L, 0L, false)));
        when(syntheticMessages.findBySession("dt-1")).thenReturn(List.of());
        ArrayNode fixture = om.createArrayNode();
        fixture.add(om.createObjectNode().set("info",
            om.createObjectNode().put("id", "msg_1").put("role", "user")));
        when(gateway.listMessages("ses_oc", null)).thenReturn(fixture);

        HistoryService svc = new HistoryService(sessions, artifacts, syntheticMessages, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).path("info").path("id").asText()).isEqualTo("msg_1");
        assertThat(result.get(0).path("info").path("role").asText()).isEqualTo("user");
        verify(gateway).listMessages(eq("ses_oc"), eq((Integer) null));
    }

    @Test
    void getMessagesReturnsEmptyArrayWhenSessionNotFound() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        SyntheticSessionMessageRepository syntheticMessages = mock(SyntheticSessionMessageRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("unknown")).thenReturn(Optional.empty());
        when(syntheticMessages.findBySession("unknown")).thenReturn(List.of());

        HistoryService svc = new HistoryService(sessions, artifacts, syntheticMessages, gateway, om);

        JsonNode result = svc.getMessages("unknown");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
    }

    @Test
    void getMessagesMergesSyntheticBangQueryMessagesWithOpenCodeHistory() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        SyntheticSessionMessageRepository syntheticMessages = mock(SyntheticSessionMessageRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", true, "ses_oc", 0L, 0L, false)));
        when(syntheticMessages.findBySession("dt-1")).thenReturn(List.of(
            new SyntheticSessionMessageRecord(
                "sqm_1",
                "dt-1",
                "bang_query_user",
                "!select 1",
                """
                    {"displayKind":"bang_query_user","queryMode":"direct_sql"}
                    """.trim(),
                1713650000000L
            )
        ));
        ArrayNode fixture = om.createArrayNode();
        fixture.add(message("msg_1", "assistant", 1713650000000L, "reply", "assistant"));
        when(gateway.listMessages("ses_oc", null)).thenReturn(fixture);

        HistoryService svc = new HistoryService(sessions, artifacts, syntheticMessages, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).path("info").path("id").asText()).isEqualTo("sqm_1");
        assertThat(result.get(0).path("info").path("role").asText()).isEqualTo("user");
        assertThat(result.get(0).path("parts").get(0).path("text").asText()).isEqualTo("!select 1");
        assertThat(result.get(0).path("parts").get(0).path("metadata").path("displayKind").asText())
            .isEqualTo("bang_query_user");
        assertThat(result.get(0).path("parts").get(0).path("metadata").path("queryMode").asText())
            .isEqualTo("direct_sql");
        assertThat(result.get(1).path("info").path("id").asText()).isEqualTo("msg_1");
        assertThat(result.get(1).path("info").path("role").asText()).isEqualTo("assistant");
    }

    @Test
    void getMessagesUsesStableTieBreakerForSameCreatedAt() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        SyntheticSessionMessageRepository syntheticMessages = mock(SyntheticSessionMessageRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", true, "ses_oc", 0L, 0L, false)));
        when(syntheticMessages.findBySession("dt-1")).thenReturn(List.of(
            new SyntheticSessionMessageRecord(
                "sqm_1", "dt-1", "bang_query_user", "!with x as (select 1)",
                """
                    {"displayKind":"bang_query_user","queryMode":"direct_sql"}
                    """.trim(),
                1713650000000L
            )
        ));
        ArrayNode fixture = om.createArrayNode();
        fixture.add(message("msg_b", "user", 1713650000000L, "u-b", null));
        fixture.add(message("msg_a", "user", 1713650000000L, "u-a", null));
        fixture.add(message("msg_c", "assistant", 1713650000000L, "a", null));
        fixture.add(message("msg_d", "system", 1713650000000L, "o", null));
        when(gateway.listMessages("ses_oc", null)).thenReturn(fixture);

        HistoryService svc = new HistoryService(sessions, artifacts, syntheticMessages, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(5);
        assertThat(result.get(0).path("info").path("id").asText()).isEqualTo("sqm_1");
        assertThat(result.get(1).path("info").path("id").asText()).isEqualTo("msg_a");
        assertThat(result.get(2).path("info").path("id").asText()).isEqualTo("msg_b");
        assertThat(result.get(3).path("info").path("id").asText()).isEqualTo("msg_c");
        assertThat(result.get(4).path("info").path("id").asText()).isEqualTo("msg_d");
    }

    private ObjectNode message(String id, String role, long createdAt, String text, String metadataKind) {
        var info = om.createObjectNode()
            .put("id", id)
            .put("role", role)
            .put("sessionID", "ses_oc")
            .set("time", om.createObjectNode().put("created", createdAt));
        var part = om.createObjectNode()
            .put("type", "text")
            .put("id", "prt_" + id)
            .put("sessionID", "ses_oc")
            .put("messageID", id)
            .put("text", text);
        if (metadataKind != null) {
            part.set("metadata", om.createObjectNode().put("displayKind", metadataKind));
        }
        ObjectNode node = om.createObjectNode();
        node.set("info", info);
        node.set("parts", om.createArrayNode().add(part));
        return node;
    }
}
