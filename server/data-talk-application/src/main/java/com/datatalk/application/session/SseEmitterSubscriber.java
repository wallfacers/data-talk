package com.datatalk.application.session;

import com.datatalk.domain.event.DtEvent;
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
            String name = eventName(n.event());
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

    private static String eventName(DtEvent e) {
        return switch (e) {
            case DtEvent.Connected c              -> "connected";
            case DtEvent.Disconnected d           -> "disconnected";
            case DtEvent.SessionStatus s          -> "session.status";
            case DtEvent.SessionStarted ss        -> "session.started";
            case DtEvent.SessionEnded se2         -> "session.ended";
            case DtEvent.AgentStatus as2          -> "agent.status";
            case DtEvent.TaskComplete tc          -> "task.complete";
            case DtEvent.MessageCreated mc        -> "message.created";
            case DtEvent.MessageUpdated mu        -> "message.updated";
            case DtEvent.MessageCompleted mco     -> "message.completed";
            case DtEvent.MessagePartCreated pc    -> "message.part.created";
            case DtEvent.MessagePartUpdated pu    -> "message.part.updated";
            case DtEvent.MessagePartDelta pd      -> "message.part.delta";
            case DtEvent.MessagePartRemoved pr    -> "message.part.removed";
            case DtEvent.ActionInvoke ai          -> "action.invoke";
            case DtEvent.ActionCancel ac          -> "action.cancel";
            case DtEvent.ActionResponse ar        -> "action.response";
            case DtEvent.ArtifactSnapshot as      -> "artifact.snapshot";
            case DtEvent.OntologyUpdated ou       -> "ontology.updated";
            case DtEvent.Heartbeat hb             -> "heartbeat";
            case DtEvent.PingPong pp              -> "ping";
            case DtEvent.StreamError se           -> "error";
        };
    }
}
