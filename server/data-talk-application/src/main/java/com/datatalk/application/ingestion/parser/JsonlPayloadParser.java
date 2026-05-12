package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses a JSONL (JSON Lines) payload file and infers column schema.
 * Each line is a separate JSON object.
 *
 * <p>Dot-path expansion and type inference follow the same rules as
 * {@link JsonPayloadParser}.</p>
 */
@Component
public class JsonlPayloadParser implements PayloadParser {

    private static final int MAX_SAMPLE_VALUES = 2;

    private final ObjectMapper om;
    private final DotPathFlattener flattener;

    public JsonlPayloadParser(ObjectMapper om) {
        this.om = om;
        this.flattener = new DotPathFlattener(om);
    }

    @Override
    public IngestionMapping infer(Path payloadFile, int sampleSize) {
        Map<String, List<Object>> columns = new LinkedHashMap<>();
        int linesRead = 0;

        try (BufferedReader reader = Files.newBufferedReader(payloadFile)) {
            String line;
            while ((line = reader.readLine()) != null && linesRead < sampleSize) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                JsonNode node = om.readTree(trimmed);
                if (!node.isObject()) continue;

                flattener.flatten((ObjectNode) node, columns);
                linesRead++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read/parse JSONL payload: " + payloadFile, e);
        }

        return buildMapping(columns);
    }

    // ───────── mapping construction (shared pattern) ─────────

    private IngestionMapping buildMapping(Map<String, List<Object>> columns) {
        String mappingId = "map_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        List<MappingColumn> mappingColumns = new ArrayList<>();

        for (Map.Entry<String, List<Object>> entry : columns.entrySet()) {
            String sourcePath = entry.getKey();
            List<Object> values = entry.getValue();

            InferredType type = TypeInferrer.infer(values);
            boolean nullable = TypeInferrer.allNull(values);
            List<String> sampleValues = TypeInferrer.collectSampleValues(values, MAX_SAMPLE_VALUES);

            String targetName = sourcePath.startsWith("$.")
                ? sourcePath.substring(2).replace('.', '_')
                : sourcePath.replace('.', '_');

            mappingColumns.add(new MappingColumn(
                sourcePath, targetName, type, false, sampleValues, nullable));
        }

        return new IngestionMapping(mappingId, mappingColumns);
    }
}
