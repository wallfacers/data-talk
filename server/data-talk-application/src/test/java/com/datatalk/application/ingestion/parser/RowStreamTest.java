package com.datatalk.application.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RowStreamTest {

    private final ObjectMapper om = new ObjectMapper();
    private final JsonPayloadParser jsonParser = new JsonPayloadParser(om);
    private final JsonlPayloadParser jsonlParser = new JsonlPayloadParser(om);
    private final CsvPayloadParser csvParser = new CsvPayloadParser();

    @TempDir
    Path tempDir;

    @Test
    void jsonlStreamsThreeRows() throws Exception {
        Path file = tempDir.resolve("data.jsonl");
        Files.writeString(file, """
            {"name":"Alice","age":30}
            {"name":"Bob","age":25}
            {"name":"Charlie","age":35}
            """);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (RowStream rs = jsonlParser.openRowStream(file)) {
            while (rs.hasNext()) {
                rows.add(rs.next());
            }
        }

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("name", "Alice")
            .containsEntry("age", 30);
        assertThat(rows.get(2))
            .containsEntry("name", "Charlie")
            .containsEntry("age", 35);
    }

    @Test
    void jsonArrayStreamsRowByRow() throws Exception {
        Path file = tempDir.resolve("data.json");
        Files.writeString(file, """
            [
              {"city":"Tokyo","pop":13960000},
              {"city":"London","pop":8982000},
              {"city":"Paris","pop":2161000}
            ]
            """);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (RowStream rs = jsonParser.openRowStream(file)) {
            while (rs.hasNext()) {
                rows.add(rs.next());
            }
        }

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("city", "Tokyo")
            .containsEntry("pop", 13960000);
        assertThat(rows.get(2))
            .containsEntry("city", "Paris");
    }

    @Test
    void csvStreamsWithHeaderRow() throws Exception {
        Path file = tempDir.resolve("data.csv");
        Files.writeString(file, """
            id,name,score
            1,Alice,95
            2,Bob,87
            3,Charlie,72
            """);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (RowStream rs = csvParser.openRowStream(file)) {
            while (rs.hasNext()) {
                rows.add(rs.next());
            }
        }

        assertThat(rows).hasSize(3);
        // BUG-0022: CSV streaming coerces numeric cells so JDBC insert receives
        // typed values, matching the inferred column types.
        assertThat(rows.get(0))
            .containsEntry("id", 1)
            .containsEntry("name", "Alice")
            .containsEntry("score", 95);
        assertThat(rows.get(2))
            .containsEntry("name", "Charlie")
            .containsEntry("score", 72);
    }

    @Test
    void emptyJsonlReturnsNoRows() throws Exception {
        Path file = tempDir.resolve("empty.jsonl");
        Files.writeString(file, "");

        try (RowStream rs = jsonlParser.openRowStream(file)) {
            assertThat(rs.hasNext()).isFalse();
        }
    }

    @Test
    void emptyCsvReturnsNoRows() throws Exception {
        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, "");

        try (RowStream rs = csvParser.openRowStream(file)) {
            assertThat(rs.hasNext()).isFalse();
        }
    }

    @Test
    void emptyJsonArrayReturnsNoRows() throws Exception {
        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, "[]");

        try (RowStream rs = jsonParser.openRowStream(file)) {
            assertThat(rs.hasNext()).isFalse();
        }
    }
}
