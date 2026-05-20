package com.datatalk.application.opencode;

import com.datatalk.domain.part.Message;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Minimal view of the OpenCode SSE event stream. Only the subset we actually
 * translate is modelled; everything else lands in {@link Unknown} and is
 * dropped by the translator.
 */
public sealed interface OcEvent {
    record ServerConnected() implements OcEvent {}
    record SessionStatus(String status, Map<String, Object> retryInfo) implements OcEvent {}
    record SessionCreated(SessionInfo info) implements OcEvent {}
    record SessionUpdated(SessionInfo info) implements OcEvent {}
    record SessionDeleted(SessionInfo info) implements OcEvent {}
    record SessionIdle(SessionInfo info) implements OcEvent {}
    record SessionError(SessionInfo info, String error) implements OcEvent {}
    record SessionCompacted(SessionInfo info) implements OcEvent {}
    record SessionDiff(SessionInfo info, java.util.Map<String, Object> payload) implements OcEvent {}
    record MessageUpdated(Message message) implements OcEvent {}
    record MessagePartUpdated(JsonNode part) implements OcEvent {}
    record MessagePartDelta(String partId, String field, String delta) implements OcEvent {}
    record MessagePartRemoved(String partId) implements OcEvent {}

    /**
     * OpenCode {@code question.asked}: the {@code requestId} is carried under
     * {@code id} (replied/rejected use {@code requestID}); {@code questions} is
     * the raw sub-question array; {@code messageId}/{@code callId} (optional)
     * link the originating tool part.
     */
    record QuestionAsked(String requestId, String sessionId, JsonNode questions,
                         String messageId, String callId) implements OcEvent {}
    record QuestionReplied(String sessionId, String requestId) implements OcEvent {}
    record QuestionRejected(String sessionId, String requestId) implements OcEvent {}

    record Unknown(String type, Map<String, Object> payload) implements OcEvent {}
}
