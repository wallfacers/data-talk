package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterSubscriberTest {

    @Test
    void formatsEventWithIdAndTypeAndData() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseEmitterSubscriber sub = new SseEmitterSubscriber(out, new ObjectMapper(), "connected");
        NumberedEvent n = new NumberedEvent(42L, "s-1",
            new DtEvent.Connected("s-1", 1), 1000L);

        sub.accept(n);

        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text)
            .contains("id: 42")
            .contains("event: connected")
            .contains("data: {")
            .endsWith("\n\n");
    }
}
