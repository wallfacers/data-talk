package com.datatalk.domain.event;

import com.datatalk.domain.part.TextPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtEventTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void serializesConnectedEventWithTypeDiscriminator() throws Exception {
        DtEvent e = new DtEvent.Connected("s1", 1);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"connected\"").contains("\"sessionId\":\"s1\"");
    }

    @Test
    void serializesMessagePartCreated() throws Exception {
        TextPart p = new TextPart("hi");
        DtEvent e = new DtEvent.MessagePartCreated(p);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"message.part.created\"").contains("\"content\":\"hi\"");
    }

    @Test
    void serializesActionInvoke() throws Exception {
        DtEvent e = new DtEvent.ActionInvoke("call-1", "datatalk.pin_artifact",
            Map.of("artifactId", "art-1"), 5_000);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"action.invoke\"").contains("\"callId\":\"call-1\"");
    }

    @Test
    void serializesErrorEventWithFatalFlag() throws Exception {
        DtEvent e = new DtEvent.StreamError(new ErrorInfo("upstream.unavailable",
            "OpenCode down", true, null), true);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"error\"").contains("\"fatal\":true");
    }
}
