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
                    columns.get(c).add(val.isEmpty() ? null : val);
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
}
