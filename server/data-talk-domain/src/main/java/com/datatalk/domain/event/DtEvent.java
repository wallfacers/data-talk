package com.datatalk.domain.event;

import com.datatalk.domain.part.Message;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeIdResolver(DtEventTypeIdResolver.class)
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

    @JsonTypeName("file_artifact.detected")
    record FileArtifactDetected(
        String fileArtifactId,
        String sessionId,
        String filename,
        String kind,
        String status,
        long sizeBytes
    ) implements DtEvent {}

    @JsonTypeName("file_artifact.archive_requested")
    record FileArtifactArchiveRequested(
        String fileArtifactId,
        String sessionId,
        String kind,
        String title,
        String summary
    ) implements DtEvent {}

    @JsonTypeName("file_artifact.archived")
    record FileArtifactArchived(
        String fileArtifactId,
        String sessionId,
        String connectionId,
        String filename,
        String physicalPath
    ) implements DtEvent {}

    @JsonTypeName("file_artifact.discarded")
    record FileArtifactDiscarded(String fileArtifactId, String reason) implements DtEvent {}

    @JsonTypeName("legacy.migrated")
    record LegacyMigrated(int filesMovedCount) implements DtEvent {}

    @JsonTypeName("ingestion.job.created")
    record IngestionJobCreated(String jobId, String sourceUrl) implements DtEvent {}

    @JsonTypeName("ingestion.payload.fetched")
    record IngestionPayloadFetched(String jobId, String payloadArtifactId,
                                    int rowCount, long bytesFetched) implements DtEvent {}

    @JsonTypeName("ingestion.mapping.proposed")
    record IngestionMappingProposed(String jobId, String mappingId, int columnCount) implements DtEvent {}

    @JsonTypeName("ingestion.job.confirmed")
    record IngestionJobConfirmed(String jobId, String tokenId) implements DtEvent {}

    @JsonTypeName("ingestion.write.started")
    record IngestionWriteStarted(String jobId, String targetTable) implements DtEvent {}

    @JsonTypeName("ingestion.write.progress")
    record IngestionWriteProgress(String jobId, int rowsInserted, int totalRows) implements DtEvent {}

    @JsonTypeName("ingestion.completed")
    record IngestionCompleted(String jobId, String targetTable, int finalRowCount, long durationMs) implements DtEvent {}

    @JsonTypeName("ingestion.failed")
    record IngestionFailed(String jobId, String phase, String errorMessage) implements DtEvent {}

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
            case FileArtifactDetected fad -> "file_artifact.detected";
            case FileArtifactArchiveRequested far -> "file_artifact.archive_requested";
            case FileArtifactArchived fa  -> "file_artifact.archived";
            case FileArtifactDiscarded fd -> "file_artifact.discarded";
            case LegacyMigrated lm        -> "legacy.migrated";
            case IngestionJobCreated ijc  -> "ingestion.job.created";
            case IngestionPayloadFetched ipf -> "ingestion.payload.fetched";
            case IngestionMappingProposed imp -> "ingestion.mapping.proposed";
            case IngestionJobConfirmed ijconf -> "ingestion.job.confirmed";
            case IngestionWriteStarted iws -> "ingestion.write.started";
            case IngestionWriteProgress iwp -> "ingestion.write.progress";
            case IngestionCompleted ic    -> "ingestion.completed";
            case IngestionFailed ifl      -> "ingestion.failed";
            case Heartbeat hb             -> "heartbeat";
            case PingPong pp              -> "ping";
            case StreamError se           -> "error";
        };
    }
}

final class DtEventTypeIdResolver extends TypeIdResolverBase {

    private Map<String, JavaType> typeById = Map.of();
    private Map<Class<?>, String> idByType = Map.of();

    @Override
    public void init(JavaType baseType) {
        super.init(baseType);
        Map<String, JavaType> discoveredTypes = new LinkedHashMap<>();
        Map<Class<?>, String> discoveredIds = new LinkedHashMap<>();
        for (Class<?> subtype : DtEvent.class.getPermittedSubclasses()) {
            JsonTypeName typeName = subtype.getAnnotation(JsonTypeName.class);
            if (typeName == null || typeName.value().isBlank()) {
                throw new IllegalStateException("DtEvent subtype missing @JsonTypeName: " + subtype.getName());
            }
            if (discoveredTypes.put(typeName.value(), TypeFactory.defaultInstance().constructType(subtype)) != null) {
                throw new IllegalStateException("Duplicate DtEvent type id: " + typeName.value());
            }
            discoveredIds.put(subtype, typeName.value());
        }
        this.typeById = Map.copyOf(discoveredTypes);
        this.idByType = Map.copyOf(discoveredIds);
    }

    @Override
    public String idFromValue(Object value) {
        return idByType.get(value.getClass());
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idByType.get(suggestedType);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        return typeById.get(id);
    }

    @Override
    public com.fasterxml.jackson.annotation.JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.NAME;
    }

    @Override
    public String getDescForKnownTypeIds() {
        return String.join(", ", typeById.keySet());
    }
}
