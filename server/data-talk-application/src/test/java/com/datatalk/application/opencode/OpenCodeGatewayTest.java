package com.datatalk.application.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeGatewayTest {

    @Test
    void forwardUserMessageDelegatesToSender() {
        AtomicReference<String> capturedSessionId = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedRequest = new AtomicReference<>();
        OpenCodeGateway gateway = new OpenCodeGateway(
            (sessionId, requestBody) -> {
                capturedSessionId.set(sessionId);
                capturedRequest.set(requestBody);
            },
            () -> "oc-42",
            sid -> {},
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); }
        );

        gateway.forwardUserMessage("ses_123", Map.of("model", "gpt-test"));

        assertThat(capturedSessionId.get()).isEqualTo("ses_123");
        assertThat(capturedRequest.get()).containsEntry("model", "gpt-test");
    }

    @Test
    void createSessionReturnsOpenCodeId() {
        OpenCodeGateway gw = new OpenCodeGateway(
            (s, body) -> {},
            () -> "oc-42",
            sid -> {},
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); }
        );
        assertThat(gw.createOpenCodeSession()).isEqualTo("oc-42");
    }

    @Test
    void deleteOpenCodeSessionInvokesDeleter() {
        List<String> deleted = new ArrayList<>();
        OpenCodeGateway gw = new OpenCodeGateway(
            (s, body) -> {},
            () -> "oc-1",
            deleted::add,
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); }
        );
        gw.deleteOpenCodeSession("ses_zzz");
        assertThat(deleted).containsExactly("ses_zzz");
    }

    @Test
    void listMessagesDelegatesToLister() {
        AtomicReference<String> capturedOcSid = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode fixture = mapper.createArrayNode();
        fixture.add(mapper.createObjectNode().put("test", 1));

        OpenCodeGateway gateway = new OpenCodeGateway(
            (sessionId, body) -> {},
            () -> "ocsid-1",
            sid -> {},
            (ocSid, limit) -> { capturedOcSid.set(ocSid); return fixture; }
        );

        JsonNode result = gateway.listMessages("ses_abc", 50);

        assertThat(capturedOcSid.get()).isEqualTo("ses_abc");
        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(1);
    }

    @Test
    void abortOpenCodeSessionInvokesAborter() {
        List<String> abortedSessionIds = new ArrayList<>();

        OpenCodeGateway gateway = new OpenCodeGateway(
            (sessionId, body) -> {},
            () -> "ocsid-1",
            sid -> {},
            abortedSessionIds::add,
            (ocSid, limit) -> { throw new UnsupportedOperationException("lister stub"); }
        );

        boolean aborted = gateway.abortOpenCodeSession("ses_abort");

        assertThat(aborted).isTrue();
        assertThat(abortedSessionIds).containsExactly("ses_abort");
    }
}
