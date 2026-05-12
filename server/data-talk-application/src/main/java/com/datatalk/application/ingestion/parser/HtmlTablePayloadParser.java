package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

@Component
public class HtmlTablePayloadParser implements PayloadParser {

    @Override
    public IngestionMapping infer(Path payloadFile, int sampleSize) {
        try {
            Document doc = Jsoup.parse(payloadFile.toFile(), "UTF-8");
            Element table = doc.selectFirst("table");
            if (table == null) {
                return new IngestionMapping("map_" + UUID.randomUUID(), List.of());
            }

            Elements headerCells = table.select("thead tr th");
            if (headerCells.isEmpty()) {
                Elements firstRow = table.select("tr").first().select("td, th");
                headerCells = firstRow;
            }

            List<String> headers = new ArrayList<>();
            for (Element cell : headerCells) {
                headers.add(cell.text().trim());
            }
            if (headers.isEmpty()) {
                return new IngestionMapping("map_" + UUID.randomUUID(), List.of());
            }

            int colCount = headers.size();
            List<List<Object>> columns = new ArrayList<>();
            for (int i = 0; i < colCount; i++) columns.add(new ArrayList<>());

            Elements dataRows = table.select("tbody tr");
            if (dataRows.isEmpty()) {
                dataRows = table.select("tr");
                // Skip header row
                if (!dataRows.isEmpty()) dataRows = new Elements(dataRows.subList(1, dataRows.size()));
            }

            int limit = Math.min(sampleSize, dataRows.size());
            for (int r = 0; r < limit; r++) {
                Elements cells = dataRows.get(r).select("td");
                for (int c = 0; c < colCount; c++) {
                    String val = c < cells.size() ? cells.get(c).text().trim() : null;
                    // BUG-0022: coerce numeric / boolean cell text so TypeInferrer
                    // can vote INTEGER / DECIMAL / BOOLEAN for HTML <table> cells.
                    columns.get(c).add(
                        (val == null || val.isEmpty()) ? null : TabularValueCoercer.coerce(val));
                }
            }

            List<MappingColumn> mappingColumns = new ArrayList<>();
            for (int c = 0; c < colCount; c++) {
                String header = headers.get(c);
                List<Object> values = columns.get(c);
                InferredType type = TypeInferrer.infer(values);
                List<String> samples = TypeInferrer.collectSampleValues(values, 2);
                boolean nullable = TypeInferrer.allNull(values) || values.stream().anyMatch(Objects::isNull);
                mappingColumns.add(new MappingColumn(header, header, type, false, samples, nullable));
            }

            return new IngestionMapping("map_" + UUID.randomUUID(), mappingColumns);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HTML table: " + payloadFile, e);
        }
    }

    // streaming intentionally deferred — HTML payload bounded to 50MB hard cap

    @Override
    public RowStream openRowStream(Path payloadFile) {
        try {
            Document doc = Jsoup.parse(payloadFile.toFile(), "UTF-8");
            Element table = doc.selectFirst("table");
            if (table == null) {
                return new RowStream() {
                    @Override public boolean hasNext() { return false; }
                    @Override public Map<String, Object> next() { throw new NoSuchElementException(); }
                    @Override public void close() {}
                };
            }

            Elements headerCells = table.select("thead tr th");
            if (headerCells.isEmpty()) {
                Elements firstRow = table.select("tr").first().select("td, th");
                headerCells = firstRow;
            }

            List<String> headers = new ArrayList<>();
            for (Element cell : headerCells) {
                headers.add(cell.text().trim());
            }
            if (headers.isEmpty()) {
                return new RowStream() {
                    @Override public boolean hasNext() { return false; }
                    @Override public Map<String, Object> next() { throw new NoSuchElementException(); }
                    @Override public void close() {}
                };
            }

            int colCount = headers.size();
            Elements dataRows = table.select("tbody tr");
            if (dataRows.isEmpty()) {
                dataRows = table.select("tr");
                if (!dataRows.isEmpty()) dataRows = new Elements(dataRows.subList(1, dataRows.size()));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Element tr : dataRows) {
                Elements cells = tr.select("td");
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 0; c < colCount; c++) {
                    String val = c < cells.size() ? cells.get(c).text().trim() : null;
                    // BUG-0022: streaming ingest mirrors the inference coercion so
                    // typed columns receive Java primitives for the JDBC insert.
                    row.put(headers.get(c),
                        (val == null || val.isEmpty()) ? null : TabularValueCoercer.coerce(val));
                }
                rows.add(row);
            }

            final java.util.Iterator<Map<String, Object>> iter = rows.iterator();
            return new RowStream() {
                @Override public boolean hasNext() { return iter.hasNext(); }
                @Override public Map<String, Object> next() { return iter.next(); }
                @Override public void close() {}
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse HTML table: " + payloadFile, e);
        }
    }
}
