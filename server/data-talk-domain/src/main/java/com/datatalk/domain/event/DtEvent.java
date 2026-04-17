package com.datatalk.domain.event;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface DtEvent {

    @JsonTypeName("connected")
    record Connected(String sessionId, int serverRev) implements DtEvent {}
    @JsonTypeName("disconnected")
    record Disconnected(String sessionId, String reason) implements DtEvent {}

    @JsonTypeName("session.status")
    record SessionStatus(String status, Map<String, Object> retryInfo) implements DtEvent {}
    @JsonTypeName("session.started")
    record SessionStarted(String sessionId, String userId) implements DtEvent {}
    @JsonTypeName("session.ended")
    record SessionEnded(String sessionId, String reason) implements DtEvent {}
    @JsonTypeName("agent.status")
    record AgentStatus(String sessionId, String status, String detail) implements DtEvent {}
    @JsonTypeName("task.complete")
    record TaskComplete(String sessionId, String taskId, String result) implements DtEvent {}

    @JsonTypeName("message.created")
    record MessageCreated(Message message) implements DtEvent {}
    @JsonTypeName("message.updated")
    record MessageUpdated(Message message) implements DtEvent {}
    @JsonTypeName("message.completed")
    record MessageCompleted(String sessionId, String messageId) implements DtEvent {}

    @JsonTypeName("message.part.created")
    record MessagePartCreated(Part part) implements DtEvent {}
    @JsonTypeName("message.part.updated")
    record MessagePartUpdated(Part part) implements DtEvent {}
    @JsonTypeName("message.part.delta")
    record MessagePartDelta(String partId, String field, String delta) implements DtEvent {}
    @JsonTypeName("message.part.removed")
    record MessagePartRemoved(String partId) implements DtEvent {}

    @JsonTypeName("action.invoke")
    record ActionInvoke(String callId, String actionId, Map<String, Object> input, int timeoutMs) implements DtEvent {}
    @JsonTypeName("action.cancel")
    record ActionCancel(String callId, String reason) implements DtEvent {}
    @JsonTypeName("action.response")
    record ActionResponse(String callId, boolean success, Map<String, Object> result, String error) implements DtEvent {}

    @JsonTypeName("artifact.snapshot")
    record ArtifactSnapshot(List<Map<String, Object>> artifacts) implements DtEvent {}
    @JsonTypeName("ontology.updated")
    record OntologyUpdated(String objectType, String id, String op, Map<String, Object> patch) implements DtEvent {}

    @JsonTypeName("heartbeat")
    record Heartbeat(long ts) implements DtEvent {}
    @JsonTypeName("ping")
    record PingPong(long ts) implements DtEvent {}

    @JsonTypeName("error")
    record StreamError(ErrorInfo error, boolean fatal) implements DtEvent {}

    /** SSE wire event name / persistence type name. Exhaustive switch on sealed interface. */
    default String typeName() {
        return switch (this) {
            case Connected c              -> "connected";
            case Disconnected d           -> "disconnected";
            case SessionStatus s          -> "session.status";
            case SessionStarted ss        -> "session.started";
            case SessionEnded se          -> "session.ended";
            case AgentStatus as           -> "agent.status";
            case TaskComplete tc          -> "task.complete";
            case MessageCreated mc        -> "message.created";
            case MessageUpdated mu        -> "message.updated";
            case MessageCompleted mc      -> "message.completed";
            case MessagePartCreated pc    -> "message.part.created";
            case MessagePartUpdated pu    -> "message.part.updated";
            case MessagePartDelta pd      -> "message.part.delta";
            case MessagePartRemoved pr    -> "message.part.removed";
            case ActionInvoke ai          -> "action.invoke";
            case ActionCancel ac          -> "action.cancel";
            case ActionResponse ar        -> "action.response";
            case ArtifactSnapshot as      -> "artifact.snapshot";
            case OntologyUpdated ou       -> "ontology.updated";
            case Heartbeat hb             -> "heartbeat";
            case PingPong pp              -> "ping";
            case StreamError se           -> "error";
        };
    }
}
