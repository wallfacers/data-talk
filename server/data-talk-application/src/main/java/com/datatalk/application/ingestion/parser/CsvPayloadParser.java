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
                columns.get(c).add(val);
            }
        }

        List<MappingColumn> mappingColumns = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            String header = headers[c].trim();
            List<Object> values = columns.get(c);
            InferredType type = TypeInferrer.infer(values);
            List<String> samples = TypeInferrer.collectSampleValues(values, 2);
            boolean nullable = TypeInferrer.allNull(values) || values.stream().anyMatch(Objects::isNull);
            mappingColumns.add(new MappingColumn(header, header, type, false, samples, nullable));
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
}
