package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.event.ErrorInfo;
import com.datatalk.domain.part.FilePart;
import com.datatalk.domain.part.Part;
import com.datatalk.domain.part.TextPart;
import com.datatalk.domain.util.Strings;
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
    private final SessionBusRegistry buses;
    private final PendingCallRegistry pending;
    private final Clock clock;
    private final OpenCodeGateway gateway;
    private final OpenCodeSessionMap sessionMap;
    private final AiUserPrefsRepository userPrefs;

    public ChannelService(SessionRepository sessions,
                          SessionBusRegistry buses, PendingCallRegistry pending,
                          Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                          AiUserPrefsRepository userPrefs) {
        this.sessions = sessions;
        this.buses = buses;
        this.pending = pending;
        this.clock = clock;
        this.gateway = gateway;
        this.sessionMap = sessionMap;
        this.userPrefs = userPrefs;
    }

    /**
     * Forward a user message to OpenCode, emit session.status:busy, and flip the
     * session's {@code has_ever_sent} flag. The USER message is NOT persisted locally
     * — OpenCode echoes it back via message.created / message.part.created events,
     * which the frontend renders.
     */
    public void sendMessage(String sessionId, List<Part> parts) {
        SessionRecord session = sessions.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("unknown session: " + sessionId));
        long now = clock.millis();
        sessions.markHasEverSent(sessionId, now);

        SessionBus bus = buses.getOrCreate(sessionId);
        bus.publish(new DtEvent.SessionStatus("busy", Map.of()));

        // Forward to OpenCode — prefer the persisted opencode_sid so the
        // binding survives backend restarts (otherwise the AI loses context
        // on restart because a fresh OpenCode session gets created).
        String ocSid = session.openCodeSid();
        if (Strings.isBlank(ocSid)) {
            ocSid = sessionMap.openCodeFor(sessionId);
        }
        if (Strings.isBlank(ocSid)) {
            ocSid = gateway.createOpenCodeSession();
            sessions.updateOpenCodeSid(sessionId, ocSid, now);
        }
        // Keep the in-memory map in sync (idempotent) so OpenCodeEventLoop's
        // reverse lookup ocSid→dtSid works for the incoming event stream.
        sessionMap.bind(sessionId, ocSid);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> wireParts = parts.stream()
            .map(this::partForWire)
            .filter(Objects::nonNull)
            .toList();
        body.put("parts", wireParts);
        String model = userPrefs.getCurrentModel();
        if (Strings.isNotBlank(model)) {
            body.put("model", normalizeModel(model));
        }
        gateway.forwardUserMessage(ocSid, body);
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
