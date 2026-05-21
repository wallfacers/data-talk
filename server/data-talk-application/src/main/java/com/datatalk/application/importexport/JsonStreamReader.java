package com.datatalk.application.importexport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class JsonStreamReader {

    private static final Logger log = LoggerFactory.getLogger(JsonStreamReader.class);

    private final ObjectMapper objectMapper;

    JsonStreamReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    record StreamReadResult(int totalRows, List<String> columns, List<Map<String, Object>> sampleRows) {}

    StreamReadResult stream(File file, int batchSize, Consumer<List<Map<String, Object>>> batchConsumer,
                            Map<String, String> columnMappings, Map<String, String> columnTypes) {
        Set<String> columnSet = new LinkedHashSet<>();
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        int totalRows = 0;

        try (JsonParser parser = objectMapper.getFactory().createParser(file)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new RuntimeException("JSON file must contain an array of objects");
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String rawKey = parser.getCurrentName();
                    parser.nextToken();

                    Object value = readJsonValue(parser);
                    String mappedKey = (columnMappings != null && columnMappings.containsKey(rawKey))
                        ? columnMappings.get(rawKey) : rawKey;

                    columnSet.add(mappedKey);
                    row.put(mappedKey, value);
                }

                if (totalRows < 3) {
                    sampleRows.add(new LinkedHashMap<>(row));
                }
                batch.add(row);
                totalRows++;

                if (batch.size() >= batchSize) {
                    batchConsumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                batchConsumer.accept(batch);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + e.getMessage(), e);
        }

        List<String> columns = new ArrayList<>(columnSet);
        log.info("JSON stream read complete: {} rows, {} columns", totalRows, columns.size());
        return new StreamReadResult(totalRows, columns, sampleRows);
    }

    private Object readJsonValue(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        return switch (token) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NUMBER_INT -> {
                long val = parser.getLongValue();
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    yield (int) val;
                }
                yield val;
            }
            case VALUE_NUMBER_FLOAT -> parser.getDoubleValue();
            case VALUE_STRING -> parser.getText();
            default -> parser.getText();
        };
    }

    /**
     * Infers DDL column types from JSON value types in sample rows.
     */
    List<String> inferDdlTypes(List<String> columns, List<Map<String, Object>> sampleRows,
                               Map<String, String> columnTypes) {
        List<String> ddlTypes = new ArrayList<>();
        for (String col : columns) {
            if (columnTypes != null && columnTypes.containsKey(col)) {
                ddlTypes.add(columnTypes.get(col));
                continue;
            }
            String inferred = "VARCHAR(255)";
            for (Map<String, Object> row : sampleRows) {
                Object val = row.get(col);
                if (val == null) continue;
                if (val instanceof Integer || val instanceof Long) {
                    inferred = "BIGINT";
                    break;
                }
                if (val instanceof Double || val instanceof Float) {
                    inferred = "DOUBLE";
                    break;
                }
                if (val instanceof Boolean) {
                    inferred = "BOOLEAN";
                    break;
                }
            }
            ddlTypes.add(inferred);
        }
        return ddlTypes;
    }
}
