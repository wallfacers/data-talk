package com.datatalk.application.opencode;

import com.datatalk.domain.event.DtEvent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates OpenCode SSE frames into DataTalk {@link DtEvent}s (spec §3.9).
 * Tracks which {@code (sessionId, partId, messageId)} triples have been seen
 * so the first {@code message.part.updated} becomes a {@code part.created}.
 */
@Component
public class OpenCodeEventTranslator {

    private final Map<String, Set<String>> seenParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenMessages = new ConcurrentHashMap<>();
    private final SessionTitleSyncer titleSyncer;

    public OpenCodeEventTranslator(SessionTitleSyncer titleSyncer) {
        this.titleSyncer = titleSyncer;
    }

    public List<DtEvent> translate(String sessionId, OcEvent in) {
        return switch (in) {
            case OcEvent.ServerConnected c -> Collections.emptyList();

            case OcEvent.SessionStatus s ->
                List.of(new DtEvent.SessionStatus(s.status(), s.retryInfo()));

            case OcEvent.SessionCreated c ->
                List.of(new DtEvent.SessionCreated(sessionId, c.info().title(), c.info().version()));

            case OcEvent.SessionUpdated u -> {
                boolean applied = titleSyncer.apply(u.info().id(), u.info().title());
                if (applied) {
                    yield List.of(new DtEvent.SessionMetaUpdated(sessionId, u.info().title(), false, u.info().version()));
                }
                yield Collections.emptyList();
            }

            case OcEvent.SessionDeleted d ->
                List.of(new DtEvent.SessionDeleted(sessionId));

            case OcEvent.SessionIdle i ->
                List.of(new DtEvent.SessionIdle(sessionId));

            case OcEvent.SessionError e ->
                List.of(new DtEvent.SessionError(sessionId, e.error()));

            case OcEvent.SessionCompacted c ->
                List.of(new DtEvent.SessionCompacted(sessionId));

            case OcEvent.SessionDiff d ->
                List.of(new DtEvent.SessionDiff(sessionId, d.payload()));

            case OcEvent.MessageUpdated m -> {
                Set<String> msgs = seenMessages.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                if (msgs.add(m.message().id())) {
                    yield List.of(new DtEvent.MessageCreated(m.message()));
                }
                yield List.of(new DtEvent.MessageUpdated(m.message()));
            }

            case OcEvent.MessagePartUpdated p -> {
                Set<String> parts = seenParts.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                String partId = p.part().path("id").asText();
                if (parts.add(partId)) {
                    yield List.of(new DtEvent.MessagePartCreated(p.part()));
                }
                yield List.of(new DtEvent.MessagePartUpdated(p.part()));
            }

            case OcEvent.MessagePartDelta d ->
                List.of(new DtEvent.MessagePartDelta(d.partId(), d.field(), d.delta()));

            case OcEvent.MessagePartRemoved r ->
                List.of(new DtEvent.MessagePartRemoved(r.partId()));

            case OcEvent.QuestionAsked q ->
                List.of(new DtEvent.QuestionAsked(sessionId, q.requestId(), q.questions(), q.messageId(), q.callId()));

            case OcEvent.QuestionReplied q ->
                List.of(new DtEvent.QuestionReplied(sessionId, q.requestId()));

            case OcEvent.QuestionRejected q ->
                List.of(new DtEvent.QuestionRejected(sessionId, q.requestId()));

            case OcEvent.Unknown u -> Collections.emptyList();
        };
    }

    /** Forget all tracking state for a session. Call when a session closes or resets. */
    public void forget(String sessionId) {
        seenParts.remove(sessionId);
        seenMessages.remove(sessionId);
    }
}
