package com.datatalk.domain.event;

import com.datatalk.domain.part.Message;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DtEvent.Connected.class, name = "connected"),
        @JsonSubTypes.Type(value = DtEvent.Disconnected.class, name = "disconnected"),
        @JsonSubTypes.Type(value = DtEvent.SessionStatus.class, name = "session.status"),
        @JsonSubTypes.Type(value = DtEvent.SessionCreated.class, name = "session.created"),
        @JsonSubTypes.Type(value = DtEvent.SessionMetaUpdated.class, name = "session.meta.updated"),
        @JsonSubTypes.Type(value = DtEvent.SessionDeleted.class, name = "session.deleted"),
        @JsonSubTypes.Type(value = DtEvent.SessionIdle.class, name = "session.idle"),
        @JsonSubTypes.Type(value = DtEvent.SessionError.class, name = "session.error"),
        @JsonSubTypes.Type(value = DtEvent.SessionCompacted.class, name = "session.compacted"),
        @JsonSubTypes.Type(value = DtEvent.SessionDiff.class, name = "session.diff"),
        @JsonSubTypes.Type(value = DtEvent.SessionStarted.class, name = "session.started"),
        @JsonSubTypes.Type(value = DtEvent.SessionEnded.class, name = "session.ended"),
        @JsonSubTypes.Type(value = DtEvent.AgentStatus.class, name = "agent.status"),
        @JsonSubTypes.Type(value = DtEvent.TaskComplete.class, name = "task.complete"),
        @JsonSubTypes.Type(value = DtEvent.MessageCreated.class, name = "message.created"),
        @JsonSubTypes.Type(value = DtEvent.MessageUpdated.class, name = "message.updated"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartCreated.class, name = "message.part.created"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartUpdated.class, name = "message.part.updated"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartDelta.class, name = "message.part.delta"),
        @JsonSubTypes.Type(value = DtEvent.MessagePartRemoved.class, name = "message.part.removed"),
        @JsonSubTypes.Type(value = DtEvent.ActionInvoke.class, name = "action.invoke"),
        @JsonSubTypes.Type(value = DtEvent.ActionCancel.class, name = "action.cancel"),
        @JsonSubTypes.Type(value = DtEvent.ActionResponse.class, name = "action.response"),
        @JsonSubTypes.Type(value = DtEvent.ArtifactSnapshot.class, name = "artifact.snapshot"),
        @JsonSubTypes.Type(value = DtEvent.OntologyUpdated.class, name = "ontology.updated"),
        @JsonSubTypes.Type(value = DtEvent.Heartbeat.class, name = "heartbeat"),
        @JsonSubTypes.Type(value = DtEvent.PingPong.class, name = "ping"),
        @JsonSubTypes.Type(value = DtEvent.StreamError.class, name = "error")
})
public sealed interface DtEvent {

    @JsonTypeName("connected")
    record Connected(String sessionId, int serverRev) implements DtEvent {}
    @JsonTypeName("disconnected")
    record Disconnected(String sessionId, String reason) implements DtEvent {}

    @JsonTypeName("session.status")
    record SessionStatus(String status, Map<String, Object> retryInfo) implements DtEvent {}
    @JsonTypeName("session.created")
    record SessionCreated(String sessionId, String title, long version) implements DtEvent {}
    @JsonTypeName("session.meta.updated")
    record SessionMetaUpdated(String sessionId, String title, boolean titleLocked, long version) implements DtEvent {}
    @JsonTypeName("session.deleted")
    record SessionDeleted(String sessionId) implements DtEvent {}
    @JsonTypeName("session.idle")
    record SessionIdle(String sessionId) implements DtEvent {}
    @JsonTypeName("session.error")
    record SessionError(String sessionId, String error) implements DtEvent {}
    @JsonTypeName("session.compacted")
    record SessionCompacted(String sessionId) implements DtEvent {}
    @JsonTypeName("session.diff")
    record SessionDiff(String sessionId, Map<String, Object> payload) implements DtEvent {}
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

    @JsonTypeName("message.part.created")
    record MessagePartCreated(JsonNode part) implements DtEvent {}
    @JsonTypeName("message.part.updated")
    record MessagePartUpdated(JsonNode part) implements DtEvent {}
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
            case SessionCreated sCreated     -> "session.created";
            case SessionMetaUpdated sMetaUpd -> "session.meta.updated";
            case SessionDeleted sDeleted     -> "session.deleted";
            case SessionIdle sIdle           -> "session.idle";
            case SessionError sError          -> "session.error";
            case SessionCompacted sCompacted -> "session.compacted";
            case SessionDiff sDiff            -> "session.diff";
            case SessionStarted ss        -> "session.started";
            case SessionEnded se          -> "session.ended";
            case AgentStatus as           -> "agent.status";
            case TaskComplete tc          -> "task.complete";
            case MessageCreated mc        -> "message.created";
            case MessageUpdated mu        -> "message.updated";
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
