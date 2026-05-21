package com.datatalk.application.importexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class CsvStreamReader {

    private static final Logger log = LoggerFactory.getLogger(CsvStreamReader.class);

    record StreamReadResult(int totalRows, List<String> columns, List<Map<String, Object>> sampleRows) {}

    StreamReadResult stream(Path file, int batchSize, Consumer<List<Map<String, Object>>> batchConsumer,
                            Map<String, String> columnMappings, Map<String, String> columnTypes) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        int totalRows = 0;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // Skip UTF-8 BOM if present
            reader.mark(3);
            int first = reader.read();
            if (first != 0xFEFF) {
                reader.reset();
            }

            // Read header line
            String headerLine = readNonEmptyLine(reader);
            if (headerLine == null) {
                return new StreamReadResult(0, List.of(), List.of());
            }

            List<String> rawColumns = parseCsvLine(headerLine);
            // Apply column mappings
            for (String col : rawColumns) {
                String mapped = (columnMappings != null && columnMappings.containsKey(col))
                    ? columnMappings.get(col) : col;
                columns.add(mapped);
            }

            // Read data lines
            String line;
            while ((line = readNonEmptyLine(reader)) != null) {
                List<String> values = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String rawValue = i < values.size() ? values.get(i) : "";
                    row.put(columns.get(i), convertValue(rawValue, columns.get(i), columnTypes));
                }

                if (totalRows < 3) {
                    sampleRows.add(row);
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
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }

        log.info("CSV stream read complete: {} rows, {} columns", totalRows, columns.size());
        return new StreamReadResult(totalRows, columns, sampleRows);
    }

    private String readNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        // Escaped double-quote
                        field.append('"');
                        i += 2;
                    } else {
                        // End of quoted field
                        inQuotes = false;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
            }
        }
        // Add last field
        fields.add(field.toString());

        return fields;
    }

    private Object convertValue(String rawValue, String columnName, Map<String, String> columnTypes) {
        // If column type is explicitly overridden, keep as string (driver will convert)
        if (columnTypes != null && columnTypes.containsKey(columnName)) {
            return rawValue.isEmpty() ? null : rawValue;
        }

        if (rawValue.isEmpty()) {
            return null;
        }

        // Try Long
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ignored) {}

        // Try Double
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {}

        return rawValue;
    }

    /**
     * Infers DDL column types from sample rows.
     */
    List<String> inferDdlTypes(List<String> columns, List<Map<String, Object>> sampleRows,
                               Map<String, String> columnTypes) {
        List<String> ddlTypes = new ArrayList<>();
        for (String col : columns) {
            if (columnTypes != null && columnTypes.containsKey(col)) {
                ddlTypes.add(columnTypes.get(col));
                continue;
            }
            // Infer from sample values
            String inferred = "VARCHAR(255)";
            for (Map<String, Object> row : sampleRows) {
                Object val = row.get(col);
                if (val == null) continue;
                if (val instanceof Long) {
                    inferred = "BIGINT";
                    break;
                }
                if (val instanceof Double) {
                    inferred = "DOUBLE";
                    break;
                }
                // Keep VARCHAR(255) for strings
            }
            ddlTypes.add(inferred);
        }
        return ddlTypes;
    }
}
