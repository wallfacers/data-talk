package com.datatalk.application.chart;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.PayloadRef;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class ChartArtifactService {

    public record Request(
        String sessionId,
        Map<String, Object> echartsOption,
        String sourceArtifactId,
        String originMessageId,
        String originPartId,
        String callId
    ) {}

    public record Result(String artifactId, int version) {}

    private static final int INITIAL_VERSION = 1;
    private static final String ARTIFACT_KIND = "chart";
    private static final String REST_PRODUCED_BY = "rest:chart";

    private final ArtifactRepository artifacts;
    private final SessionBusRegistry buses;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final IdGenerator ids;

    public ChartArtifactService(
        ArtifactRepository artifacts,
        SessionBusRegistry buses,
        ObjectMapper objectMapper,
        Clock clock,
        IdGenerator ids
    ) {
        this.artifacts = artifacts;
        this.buses = buses;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ids = ids;
    }

    public Result createChartArtifact(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.echartsOption() == null) {
            throw new IllegalArgumentException("echartsOption must not be null");
        }

        String artifactId = ids.nextArtifactId();
        Integer supersedesVersion = request.sourceArtifactId() == null
            ? null
            : artifacts.findLatestById(request.sourceArtifactId())
                .map(ArtifactRecord::version)
                .orElse(null);
        String producedBy = request.callId() == null ? REST_PRODUCED_BY : request.callId();
        String payloadJson = toPayloadJson(request);

        ArtifactRecord artifact = new ArtifactRecord(
            artifactId,
            INITIAL_VERSION,
            request.sessionId(),
            ARTIFACT_KIND,
            producedBy,
            PayloadRef.INLINE_PREFIX + payloadJson,
            payloadJson.length(),
            request.sourceArtifactId(),
            supersedesVersion,
            false,
            clock.millis(),
            request.originMessageId(),
            request.originPartId()
        );
        artifacts.insert(artifact);

        buses.getOrCreate(request.sessionId()).publish(new DtEvent.OntologyUpdated(
            "datatalk.artifact",
            artifactId,
            "upsert",
            buildPatch(artifact)
        ));

        return new Result(artifactId, INITIAL_VERSION);
    }

    private String toPayloadJson(Request request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceArtifactId", request.sourceArtifactId());
        payload.put("echartsOption", request.echartsOption());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chart artifact payload", e);
        }
    }

    private Map<String, Object> buildPatch(ArtifactRecord artifact) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("kind", artifact.kind());
        patch.put("version", artifact.version());
        patch.put("producedBy", artifact.producedBy());
        if (artifact.supersedesId() != null) {
            patch.put("supersedesId", artifact.supersedesId());
        }
        if (artifact.originMessageId() != null) {
            patch.put("originMessageId", artifact.originMessageId());
        }
        if (artifact.originPartId() != null) {
            patch.put("originPartId", artifact.originPartId());
        }
        return patch;
    }
}
