package com.datatalk.application.channel;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Business layer behind Streamable HTTP. Pure Java; knows nothing about HTTP.
 */
@Service
public class ChannelService {

    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final SessionBusRegistry buses;
    private final PendingCallRegistry pending;
    private final IdGenerator ids;
    private final Clock clock;

    public ChannelService(SessionRepository sessions, MessageRepository messages,
                          SessionBusRegistry buses, PendingCallRegistry pending,
                          IdGenerator ids, Clock clock) {
        this.sessions = sessions;
        this.messages = messages;
        this.buses = buses;
        this.pending = pending;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Persist a user message, emit message.created + part.created + session.status:busy,
     * and flip the session's {@code has_ever_sent} flag. Does NOT forward to OpenCode
     * — that's the gateway's job (Task 23).
     */
    public String sendMessage(String sessionId, List<Part> parts) {
        if (sessions.findById(sessionId).isEmpty()) {
            throw new IllegalArgumentException("unknown session: " + sessionId);
        }
        long now = clock.millis();
        String messageId = ids.next();
        Message m = new Message(messageId, sessionId, Message.Role.USER, parts, now);
        messages.save(m);
        sessions.markHasEverSent(sessionId, now);

        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.MessageCreated(m));
        for (Part p : parts) bus.publish(new DtEvent.MessagePartCreated(p));
        bus.publish(new DtEvent.SessionStatus("busy", Map.of()));
        return messageId;
    }

    public boolean completeActionResult(String callId, boolean ok, Object output, ErrorInfo error) {
        if (ok) {
            return pending.complete(callId, output);
        }
        return pending.fail(callId, new ActionResultError(error));
    }

    public void abort(String sessionId) {
        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
    }

    public static class ActionResultError extends RuntimeException {
        public final ErrorInfo info;
        public ActionResultError(ErrorInfo info) {
            super(info == null ? null : info.message());
            this.info = info;
        }
    }
}
