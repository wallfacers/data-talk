package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

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
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", false, null, 0L, 0L, false)));

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
        verify(gateway, never()).listMessages(anyString(), any());
    }

    @Test
    void getMessagesForwardsToGatewayWhenOpenCodeSidExists() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("dt-1")).thenReturn(Optional.of(
            new SessionRecord("dt-1", null, "t", true, "ses_oc", 0L, 0L, false)));
        ArrayNode fixture = om.createArrayNode();
        fixture.add(om.createObjectNode().set("info",
            om.createObjectNode().put("id", "msg_1").put("role", "user")));
        when(gateway.listMessages("ses_oc", null)).thenReturn(fixture);

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("dt-1");

        assertThat(result).isSameAs(fixture);
        verify(gateway).listMessages(eq("ses_oc"), eq((Integer) null));
    }

    @Test
    void getMessagesReturnsEmptyArrayWhenSessionNotFound() {
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactRepository artifacts = mock(ArtifactRepository.class);
        OpenCodeGateway gateway = mock(OpenCodeGateway.class);
        when(sessions.findById("unknown")).thenReturn(Optional.empty());

        HistoryService svc = new HistoryService(sessions, artifacts, gateway, om);

        JsonNode result = svc.getMessages("unknown");

        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(0);
    }
}