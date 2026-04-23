package com.datatalk.adapter.rest;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}/artifacts/chart")
public class ChartArtifactController {

    static final int MAX_ECHARTS_OPTION_BYTES = 256 * 1024;

    private final ChartArtifactService chartArtifactService;
    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public ChartArtifactController(
        ChartArtifactService chartArtifactService,
        SessionRepository sessionRepository,
        ObjectMapper objectMapper
    ) {
        this.chartArtifactService = chartArtifactService;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createChartArtifact(
        @PathVariable String sessionId,
        @RequestBody(required = false) CreateChartArtifactRequest request
    ) {
        if (request == null || request.echartsOption() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "echartsOption is required"));
        }

        if (serializedSize(request.echartsOption()) > MAX_ECHARTS_OPTION_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "echartsOption exceeds 256KB"));
        }

        if (sessionRepository.findById(sessionId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ChartArtifactService.Result result = chartArtifactService.createChartArtifact(
            new ChartArtifactService.Request(
                sessionId,
                request.echartsOption(),
                request.sourceArtifactId(),
                request.originMessageId(),
                request.originPartId(),
                null
            )
        );

        return ResponseEntity.ok(new CreateChartArtifactResponse(result.artifactId(), result.version()));
    }

    private int serializedSize(Map<String, Object> echartsOption) {
        try {
            return objectMapper.writeValueAsBytes(echartsOption).length;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("echartsOption must be serializable", e);
        }
    }

    public record CreateChartArtifactRequest(
        Map<String, Object> echartsOption,
        String sourceArtifactId,
        String originMessageId,
        String originPartId
    ) {}

    public record CreateChartArtifactResponse(String artifactId, int version) {}
}
