package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
            // BUG-0033: nullable should be true if ANY value is null, not only when ALL are.
            // Aligns with CSV/HTML parsers; otherwise DDL emits NOT NULL for a column that
            // legitimately contains nulls, breaking INSERTs.
            boolean nullable = TypeInferrer.allNull(values) || values.stream().anyMatch(java.util.Objects::isNull);
            List<String> sampleValues = TypeInferrer.collectSampleValues(values, MAX_SAMPLE_VALUES);

            String targetName = sourcePath.startsWith("$.")
                ? sourcePath.substring(2).replace('.', '_')
                : sourcePath.replace('.', '_');

            mappingColumns.add(new MappingColumn(
                sourcePath, targetName, type, false, sampleValues, nullable));
        }

        return new IngestionMapping(mappingId, mappingColumns);
    }

    // ───────── streaming ─────────

    @Override
    public RowStream openRowStream(Path payloadFile) {
        ObjectMapper objectMapper = flattener.objectMapper();
        JsonParser jp;
        try {
            jp = objectMapper.getFactory().createParser(payloadFile.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to open JSON file: " + payloadFile, e);
        }

        try {
            JsonToken token = jp.nextToken();
            if (token == JsonToken.START_ARRAY) {
                return new JsonArrayRowStream(jp, objectMapper);
            } else if (token == JsonToken.START_OBJECT) {
                // Single-object envelope: wrap into a single-row stream
                JsonNode node = objectMapper.readTree(jp);
                jp.close();
                Map<String, Object> row = new LinkedHashMap<>();
                if (node.isObject()) {
                    node.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                }
                final java.util.Iterator<Map<String, Object>> iter = List.of(row).iterator();
                return new RowStream() {
                    @Override public boolean hasNext() { return iter.hasNext(); }
                    @Override public Map<String, Object> next() { return iter.next(); }
                    @Override public void close() {}
                };
            } else {
                jp.close();
                return new RowStream() {
                    @Override public boolean hasNext() { return false; }
                    @Override public Map<String, Object> next() { throw new NoSuchElementException(); }
                    @Override public void close() {}
                };
            }
        } catch (IOException e) {
            try { jp.close(); } catch (IOException ignored) {}
            throw new RuntimeException("Failed to parse JSON file: " + payloadFile, e);
        }
    }

    private static Object convertNode(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isNull()) return null;
        return node.asText();
    }

    private static class JsonArrayRowStream implements RowStream {
        private final JsonParser jp;
        private final ObjectMapper om;
        private boolean exhausted = false;

        JsonArrayRowStream(JsonParser jp, ObjectMapper om) {
            this.jp = jp;
            this.om = om;
        }

        @Override
        public boolean hasNext() {
            if (exhausted) return false;
            try {
                JsonToken token = jp.nextToken();
                if (token == JsonToken.END_ARRAY || token == null) {
                    exhausted = true;
                    return false;
                }
                return true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to read JSON token", e);
            }
        }

        @Override
        public Map<String, Object> next() {
            if (exhausted) throw new NoSuchElementException();
            try {
                JsonNode node = jp.readValueAsTree();
                Map<String, Object> row = new LinkedHashMap<>();
                if (node.isObject()) {
                    node.fields().forEachRemaining(f -> row.put(f.getKey(), JsonPayloadParser.convertNode(f.getValue())));
                }
                return row;
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse JSON array element", e);
            }
        }

        @Override
        public void close() {
            try { jp.close(); } catch (IOException ignored) {}
        }
    }
}
