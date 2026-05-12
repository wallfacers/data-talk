package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses a JSON array payload file and infers column schema.
 *
 * <p>Dot-path expansion rules:
 * <ul>
 *   <li>Top-level key → {@code $.<key>}</li>
 *   <li>Nested object (depth 1) → {@code $.<parent>.<child>}</li>
 *   <li>Depth >= 2 → kept as {@code $.<parent>.<child>} with type JSON</li>
 * </ul>
 */
@Component
public class JsonPayloadParser implements PayloadParser {

    private static final int MAX_SAMPLE_VALUES = 2;

    private final DotPathFlattener flattener;

    public JsonPayloadParser(ObjectMapper om) {
        this.flattener = new DotPathFlattener(om);
    }

    @Override
    public IngestionMapping infer(Path payloadFile, int sampleSize) {
        try {
            byte[] bytes = Files.readAllBytes(payloadFile);
            ObjectMapper om = flattener.objectMapper();
            JsonNode root = om.readTree(bytes);

            ArrayNode array;
            if (root.isArray()) {
                array = (ArrayNode) root;
            } else if (root.isObject()) {
                // Envelope: wrap as single-element array
                ArrayNode wrapped = om.createArrayNode();
                wrapped.add(root);
                array = wrapped;
            } else {
                throw new IllegalArgumentException(
                    "Expected JSON array or object, got: " + root.getNodeType());
            }

            // Sample rows
            int rowsToSample = Math.min(sampleSize, array.size());

            // Collect all dot-path → raw values across sampled rows
            Map<String, List<Object>> columns = new LinkedHashMap<>();
            for (int i = 0; i < rowsToSample; i++) {
                JsonNode row = array.get(i);
                if (!row.isObject()) continue;
                flattener.flatten((ObjectNode) row, columns);
            }

            return buildMapping(columns);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read/parse JSON payload: " + payloadFile, e);
        }
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
