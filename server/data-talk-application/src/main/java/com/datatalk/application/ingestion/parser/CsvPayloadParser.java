package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class CsvPayloadParser implements PayloadParser {

    @Override
    public IngestionMapping infer(Path payloadFile, int sampleSize) {
        List<String[]> rows = readCsvRows(payloadFile);
        if (rows.isEmpty()) {
            return new IngestionMapping("map_" + UUID.randomUUID(), List.of());
        }

        String[] headers = rows.get(0);
        int colCount = headers.length;
        List<List<Object>> columns = new ArrayList<>();
        for (int i = 0; i < colCount; i++) columns.add(new ArrayList<>());

        int limit = Math.min(sampleSize, rows.size() - 1);
        for (int r = 1; r <= limit; r++) {
            String[] row = rows.get(r);
            for (int c = 0; c < colCount; c++) {
                String val = c < row.length ? row[c] : null;
                // BUG-0022: coerce numeric / boolean cell strings so TypeInferrer
                // can vote INTEGER / DECIMAL / BOOLEAN instead of STRING.
                columns.get(c).add(TabularValueCoercer.coerce(val));
            }
        }

        List<MappingColumn> mappingColumns = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            String header = headers[c].trim();
            List<Object> values = columns.get(c);
            InferredType type = TypeInferrer.infer(values);
            List<String> samples = TypeInferrer.collectSampleValues(values, 2);
            boolean nullable = TypeInferrer.allNull(values) || values.stream().anyMatch(Objects::isNull);
            // BUG-0028: emit sourcePath as `$.<header>` to align with JSON/JSONL paths.
            // The `$.` prefix is the canonical sourcePath form across all parsers so
            // mapping consumers don't need format-specific logic.
            mappingColumns.add(new MappingColumn("$." + header, header, type, false, samples, nullable));
        }

        return new IngestionMapping("map_" + UUID.randomUUID(), mappingColumns);
    }

    private List<String[]> readCsvRows(Path file) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file: " + file, e);
        }
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(ch);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    // ───────── streaming ─────────

    @Override
    public RowStream openRowStream(Path payloadFile) {
        BufferedReader reader;
        try {
            reader = Files.newBufferedReader(payloadFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open CSV file: " + payloadFile, e);
        }

        // Read header line
        String[] headers;
        try {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                reader.close();
                return new RowStream() {
                    @Override public boolean hasNext() { return false; }
                    @Override public Map<String, Object> next() { throw new NoSuchElementException(); }
                    @Override public void close() {}
                };
            }
            headers = parseCsvLine(headerLine);
        } catch (IOException e) {
            try { reader.close(); } catch (IOException ignored) {}
            throw new RuntimeException("Failed to read CSV header: " + payloadFile, e);
        }

        final String[] hdr = headers;
        return new RowStream() {
            private String nextLine;

            @Override
            public boolean hasNext() {
                if (nextLine != null) return true;
                try {
                    while ((nextLine = reader.readLine()) != null) {
                        if (!nextLine.isBlank()) return true;
                    }
                    return false;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read CSV line", e);
                }
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) throw new NoSuchElementException();
                String line = nextLine;
                nextLine = null;
                String[] vals = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 0; c < hdr.length; c++) {
                    // BUG-0022: streaming ingest path mirrors the inference coercion so
                    // declared INTEGER / DECIMAL columns receive correctly-typed values
                    // for the JDBC insert.
                    row.put(hdr[c].trim(),
                        c < vals.length ? TabularValueCoercer.coerce(vals[c]) : null);
                }
                return row;
            }

            @Override
            public void close() {
                try { reader.close(); } catch (IOException ignored) {}
            }
        };
    }
}
