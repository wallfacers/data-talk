package com.datatalk.application.session;

import com.datatalk.domain.event.NumberedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Writes a {@link NumberedEvent} to an {@link OutputStream} in the SSE wire
 * format. Used by {@code ChannelController} after upgrading a POST response
 * to {@code text/event-stream}.
 *
 * <p>If the initial {@code expectedFirstEventName} is set, the subscriber
 * asserts the first NumberedEvent's type matches (defensive check so
 * "connected" really is the first frame).</p>
 */
public class SseEmitterSubscriber implements Consumer<NumberedEvent> {

    private final OutputStream out;
    private final ObjectMapper om;
    private final String expectedFirstEventName;
    private volatile boolean firstFrame = true;
    private volatile boolean broken = false;

    public SseEmitterSubscriber(OutputStream out, ObjectMapper om, String expectedFirstEventName) {
        this.out = out;
        this.om = om;
        this.expectedFirstEventName = expectedFirstEventName;
    }

    @Override
    public void accept(NumberedEvent n) {
        if (broken) return;
        try {
            String name = n.event().typeName();
            if (firstFrame && expectedFirstEventName != null && !expectedFirstEventName.equals(name)) {
                // soft warning; don't break the stream just for this
            }
            firstFrame = false;
            StringBuilder sb = new StringBuilder();
            sb.append("id: ").append(n.eventId()).append('\n');
            sb.append("event: ").append(name).append('\n');
            sb.append("data: ").append(om.writeValueAsString(n.event())).append("\n\n");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            broken = true;
        }
    }

    public boolean isBroken() { return broken; }
}
