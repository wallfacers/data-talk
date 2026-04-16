package com.datatalk.domain.event;

import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sealed hierarchy of all domain events emitted by the Data Talk platform.
 * Jackson type discriminator property "type" maps to a stable string value.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DtEvent.Connected.class, name = "connected"),
        @JsonSubTypes.Type(value = DtEvent.Disconnected.class, name = "disconnected"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartCreated.class, name = "message.part.created"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartUpdated.class, name = "message.part.updated"),
        @JsonSubTypes.Type(value = DtEvent.MessageCreated.class, name = "message.created"),
        @JsonSubTypes.Type(value = DtEvent.MessageCompleted.class, name = "message.completed"),
        @JsonSubTypes.Type(value = DtEvent.SessionStarted.class, name = "session.started"),
        @JsonSubTypes.Type(value = DtEvent.SessionEnded.class, name = "session.ended"),
        @JsonSubTypes.Type(value = DtEvent.AgentStatus.class, name = "agent.status"),
        @JsonSubTypes.Type(value = DtEvent.TaskComplete.class, name = "task.complete"),
        @JsonSubTypes.Type(value = DtEvent.ActionInvoke.class, name = "action.invoke"),
        @JsonSubTypes.Type(value = DtEvent.ActionResponse.class, name = "action.response"),
        @JsonSubTypes.Type(value = DtEvent.StreamError.class, name = "error"),
        @JsonSubTypes.Type(value = DtEvent.PingPong.class, name = "ping")
})
public sealed interface DtEvent
        permits DtEvent.Connected, DtEvent.Disconnected,
                DtEvent.MessagePartCreated, DtEvent.MessagePartUpdated,
                DtEvent.MessageCreated, DtEvent.MessageCompleted,
                DtEvent.SessionStarted, DtEvent.SessionEnded,
                DtEvent.AgentStatus, DtEvent.TaskComplete,
                DtEvent.ActionInvoke, DtEvent.ActionResponse,
                DtEvent.StreamError, DtEvent.PingPong {

    // -- Connection events --

    record Connected(String sessionId, int version) implements DtEvent {}

    record Disconnected(String sessionId, String reason) implements DtEvent {}

    // -- Streaming message part events --

    record MessagePartCreated(Part part) implements DtEvent {}

    record MessagePartUpdated(Part part) implements DtEvent {}

    // -- Message boundary events --

    record MessageCreated(String sessionId, String messageId,
                          String role, Instant timestamp) implements DtEvent {}

    record MessageCompleted(String sessionId, String messageId) implements DtEvent {}

    // -- Session events --

    record SessionStarted(String sessionId, String userId,
                          Instant timestamp) implements DtEvent {}

    record SessionEnded(String sessionId, String reason) implements DtEvent {}

    // -- Agent events --

    record AgentStatus(String sessionId, String status,
                       String detail) implements DtEvent {}

    record TaskComplete(String sessionId, String taskId,
                        String result) implements DtEvent {}

    // -- Action events --

    record ActionInvoke(String callId, String action,
                        Map<String, Object> args, int timeoutMs) implements DtEvent {}

    record ActionResponse(String callId, boolean success,
                          Map<String, Object> result, String error) implements DtEvent {}

    // -- Error event --

    record StreamError(ErrorInfo error, boolean fatal) implements DtEvent {}

    // -- Heartbeat --

    record PingPong(Instant timestamp) implements DtEvent {}
}
