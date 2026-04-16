package com.datatalk.domain.event;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DtEvent.Connected.class,           name = "connected"),
    @JsonSubTypes.Type(value = DtEvent.Disconnected.class,        name = "disconnected"),
    @JsonSubTypes.Type(value = DtEvent.SessionStatus.class,       name = "session.status"),
    @JsonSubTypes.Type(value = DtEvent.SessionStarted.class,      name = "session.started"),
    @JsonSubTypes.Type(value = DtEvent.SessionEnded.class,        name = "session.ended"),
    @JsonSubTypes.Type(value = DtEvent.AgentStatus.class,         name = "agent.status"),
    @JsonSubTypes.Type(value = DtEvent.TaskComplete.class,        name = "task.complete"),
    @JsonSubTypes.Type(value = DtEvent.MessageCreated.class,      name = "message.created"),
    @JsonSubTypes.Type(value = DtEvent.MessageUpdated.class,      name = "message.updated"),
    @JsonSubTypes.Type(value = DtEvent.MessageCompleted.class,    name = "message.completed"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartCreated.class,  name = "message.part.created"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartUpdated.class,  name = "message.part.updated"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartDelta.class,    name = "message.part.delta"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartRemoved.class,  name = "message.part.removed"),
    @JsonSubTypes.Type(value = DtEvent.ActionInvoke.class,        name = "action.invoke"),
    @JsonSubTypes.Type(value = DtEvent.ActionCancel.class,        name = "action.cancel"),
    @JsonSubTypes.Type(value = DtEvent.ActionResponse.class,      name = "action.response"),
    @JsonSubTypes.Type(value = DtEvent.ArtifactSnapshot.class,    name = "artifact.snapshot"),
    @JsonSubTypes.Type(value = DtEvent.OntologyUpdated.class,     name = "ontology.updated"),
    @JsonSubTypes.Type(value = DtEvent.Heartbeat.class,           name = "heartbeat"),
    @JsonSubTypes.Type(value = DtEvent.PingPong.class,            name = "ping"),
    @JsonSubTypes.Type(value = DtEvent.StreamError.class,         name = "error")
})
public sealed interface DtEvent {

    record Connected(String sessionId, int serverRev) implements DtEvent {}
    record Disconnected(String sessionId, String reason) implements DtEvent {}

    record SessionStatus(String status, Map<String, Object> retryInfo) implements DtEvent {}
    record SessionStarted(String sessionId, String userId) implements DtEvent {}
    record SessionEnded(String sessionId, String reason) implements DtEvent {}
    record AgentStatus(String sessionId, String status, String detail) implements DtEvent {}
    record TaskComplete(String sessionId, String taskId, String result) implements DtEvent {}

    record MessageCreated(Message message) implements DtEvent {}
    record MessageUpdated(Message message) implements DtEvent {}
    record MessageCompleted(String sessionId, String messageId) implements DtEvent {}

    record MessagePartCreated(Part part) implements DtEvent {}
    record MessagePartUpdated(Part part) implements DtEvent {}
    record MessagePartDelta(String partId, String field, String delta) implements DtEvent {}
    record MessagePartRemoved(String partId) implements DtEvent {}

    record ActionInvoke(String callId, String actionId, Map<String, Object> input, int timeoutMs) implements DtEvent {}
    record ActionCancel(String callId, String reason) implements DtEvent {}
    record ActionResponse(String callId, boolean success, Map<String, Object> result, String error) implements DtEvent {}

    record ArtifactSnapshot(List<Map<String, Object>> artifacts) implements DtEvent {}
    record OntologyUpdated(String objectType, String id, String op, Map<String, Object> patch) implements DtEvent {}

    record Heartbeat(long ts) implements DtEvent {}
    record PingPong(long ts) implements DtEvent {}

    record StreamError(ErrorInfo error, boolean fatal) implements DtEvent {}
}
