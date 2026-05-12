package com.datatalk.application.ingestion;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.parser.*;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

/**
 * Infers column schema from a payload file associated with an ingestion job.
 * Dispatches to the correct {@link PayloadParser} based on the job's
 * {@link PayloadFormat}, then persists the resulting mapping via
 * {@link IngestionJobRepository#updateMapping}.
 */
@Service
public class IngestionSchemaInferrer {

    private final Map<PayloadFormat, PayloadParser> parsers;
    private final FileArtifactRepository artifactRepo;
    private final IngestionJobRepository jobRepo;
    private final ObjectMapper om;

    public IngestionSchemaInferrer(JsonPayloadParser j, JsonlPayloadParser jl,
                                   CsvPayloadParser c, HtmlTablePayloadParser h,
                                   FileArtifactRepository artifactRepo,
                                   IngestionJobRepository jobRepo, ObjectMapper om) {
        this.parsers = Map.of(
            PayloadFormat.JSON, j, PayloadFormat.JSONL, jl,
            PayloadFormat.CSV, c, PayloadFormat.HTML, h);
        this.artifactRepo = artifactRepo;
        this.jobRepo = jobRepo;
        this.om = om;
    }

    /**
     * Infer column schema for the given ingestion job.
     *
     * @param jobId      the ingestion job ID (must have a payloadArtifactId)
     * @param sampleSize max number of rows to sample from the payload
     * @return the inferred {@link IngestionMapping}
     * @throws IllegalArgumentException if the job or its payload artifact is not found
     */
    public IngestionMapping infer(String jobId, int sampleSize) {
        var job = jobRepo.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));

        if (job.payloadArtifactId() == null || job.payloadArtifactId().isBlank()) {
            throw new IllegalArgumentException("job has no payload artifact: " + jobId);
        }

        FileArtifact artifact = artifactRepo.findById(job.payloadArtifactId())
            .orElseThrow(() -> new IllegalArgumentException(
                "artifact not found: " + job.payloadArtifactId()));

        Path payloadPath = Path.of(artifact.physicalPath());
        PayloadParser parser = parsers.get(job.payloadFormat());
        if (parser == null) {
            throw new UnsupportedOperationException(
                "unsupported payload format: " + job.payloadFormat());
        }

        IngestionMapping mapping = parser.infer(payloadPath, sampleSize);

        try {
            jobRepo.updateMapping(jobId, om.writeValueAsString(mapping), System.currentTimeMillis());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize mapping", e);
        }

        return mapping;
    }
}
