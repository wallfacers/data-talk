package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.FilePart;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.datatalk.domain.part.TextPart;
import com.datatalk.domain.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Business layer behind Streamable HTTP. Pure Java; knows nothing about HTTP.
 */
@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);

    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final SessionBusRegistry buses;
    private final PendingCallRegistry pending;
    private final IdGenerator ids;
    private final Clock clock;
    private final OpenCodeGateway gateway;
    private final OpenCodeSessionMap sessionMap;
    private final ObjectMapper om;
    private final AiUserPrefsRepository userPrefs;

    public ChannelService(SessionRepository sessions, MessageRepository messages,
                          SessionBusRegistry buses, PendingCallRegistry pending,
                          IdGenerator ids, Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                          ObjectMapper om, AiUserPrefsRepository userPrefs) {
        this.sessions = sessions;
        this.messages = messages;
        this.buses = buses;
        this.pending = pending;
        this.ids = ids;
        this.clock = clock;
        this.gateway = gateway;
        this.sessionMap = sessionMap;
        this.om = om;
        this.userPrefs = userPrefs;
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
        List<Part> stamped = parts.stream().map(p -> p.withMessageId(messageId)).toList();
        Message m = new Message(messageId, sessionId, Message.Role.USER, stamped, now);
        messages.save(m);
        sessions.markHasEverSent(sessionId, now);

        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.MessageCreated(m));
        for (Part p : stamped) bus.publish(new DtEvent.MessagePartCreated(p));
        bus.publish(new DtEvent.SessionStatus("busy", Map.of()));

        // Forward to OpenCode
        String ocSid = sessionMap.openCodeFor(sessionId);
        if (ocSid == null) {
            ocSid = gateway.createOpenCodeSession();
            sessionMap.bind(sessionId, ocSid);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> wireParts = stamped.stream()
            .map(this::partForWire)
            .filter(Objects::nonNull)
            .toList();
        body.put("parts", wireParts);
        String model = userPrefs.getCurrentModel();
        if (Strings.isNotBlank(model)) {
            body.put("model", normalizeModel(model));
        }
        gateway.forwardUserMessage(ocSid, body);

        return messageId;
    }

    /**
     * OpenCode 1.4.7's {@code POST /session/:id/message} applies strict Zod
     * validation to <em>every</em> field the client sends: if {@code id}
     * doesn't start with {@code "prt_"} it rejects the whole request. The
     * server generates its own ids / timestamps / flags when you omit them.
     *
     * <p>So we forward only the minimum that OpenCode actually needs from the
     * client (matches the open-db-studio Rust reference client). DataTalk's
     * internal persisted Part keeps its UUID id / domain-specific fields —
     * they're for our own SSE fan-out, not OpenCode.</p>
     *
     * <p>Returns {@code null} for part types we haven't mapped yet, so the
     * caller can filter them out rather than crash the whole send.</p>
     */
    private Map<String, Object> partForWire(Part p) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (p instanceof TextPart t) {
            out.put("type", "text");
            out.put("text", t.text() == null ? "" : t.text());
            return out;
        }
        if (p instanceof FilePart f) {
            out.put("type", "file");
            if (f.mime() != null) out.put("mime", f.mime());
            if (f.filename() != null) out.put("filename", f.filename());
            if (f.url() != null) out.put("url", f.url());
            if (f.source() != null) out.put("source", f.source());
            return out;
        }
        log.warn("[channel] dropping unsupported outbound part type for OpenCode: {}",
            p.getClass().getSimpleName());
        return null;
    }

    /**
     * Splits a DataTalk preference string like {@code "openai/gpt-5"} into
     * OpenCode 1.4.7's {@code {providerID, modelID}} object. A missing provider
     * ({@code "gpt-5"}) falls back to an empty providerID — OpenCode resolves
     * via its own defaults in that case.
     */
    private Map<String, String> normalizeModel(String model) {
        int slash = model.indexOf('/');
        if (slash < 0) {
            return Map.of("providerID", "", "modelID", model);
        }
        return Map.of(
            "providerID", model.substring(0, slash),
            "modelID", model.substring(slash + 1));
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
