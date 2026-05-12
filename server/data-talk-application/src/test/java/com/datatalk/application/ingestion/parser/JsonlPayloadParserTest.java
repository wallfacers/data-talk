package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class JsonlPayloadParserTest {

    @TempDir Path tempDir;

    ObjectMapper om = new ObjectMapper();
    JsonlPayloadParser parser;

    @BeforeEach
    void setup() {
        parser = new JsonlPayloadParser(om);
    }

    private Path writeJsonl(String content) throws Exception {
        Path file = tempDir.resolve("payload.jsonl");
        Files.writeString(file, content);
        return file;
    }

    private Map<String, MappingColumn> columnMap(IngestionMapping mapping) {
        return mapping.columns().stream()
            .collect(Collectors.toMap(MappingColumn::sourcePath, Function.identity()));
    }

    // ───────── basic JSONL parsing ─────────

    @Test
    void parsesJsonlLines() throws Exception {
        Path file = writeJsonl("""
            {"id": 1, "name": "alice"}
            {"id": 2, "name": "bob"}
            {"id": 3, "name": "charlie"}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols).containsKeys("$.id", "$.name");
        assertThat(cols.get("$.id").type()).isEqualTo(InferredType.INTEGER_32);
        assertThat(cols.get("$.name").type()).isEqualTo(InferredType.STRING_64);
    }

    // ───────── integer + string mixed → STRING_64 ─────────

    @Test
    void integerAndStringMixedResolvesToString64() throws Exception {
        Path file = writeJsonl("""
            {"val": 42}
            {"val": "hello"}
            {"val": 99}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.val");

        assertThat(col.type()).isEqualTo(InferredType.STRING_64);
    }

    // ───────── dot-path 1-level expansion ─────────

    @Test
    void nestedObjectExpandsOneLevel() throws Exception {
        Path file = writeJsonl("""
            {"name": "alice", "address": {"city": "NYC", "zip": "10001"}}
            {"name": "bob",   "address": {"city": "LA",  "zip": "90001"}}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols).containsKeys("$.name", "$.address.city", "$.address.zip");
        assertThat(cols.get("$.address.city").targetName()).isEqualTo("address_city");
    }

    // ───────── deep nesting → JSON type ─────────

    @Test
    void deepNestingBecomesJsonType() throws Exception {
        Path file = writeJsonl("""
            {"meta": {"tags": {"primary": "red"}}}
            {"meta": {"tags": {"primary": "green"}}}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.meta.tags");

        assertThat(col.type()).isEqualTo(InferredType.JSON);
    }

    // ───────── all-null → STRING_256 nullable ─────────

    @Test
    void allNullColumnResolvesToString256Nullable() throws Exception {
        Path file = writeJsonl("""
            {"id": 1, "email": null}
            {"id": 2, "email": null}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.email");

        assertThat(col.type()).isEqualTo(InferredType.STRING_256);
        assertThat(col.nullable()).isTrue();
    }

    // ───────── boolean detection ─────────

    @Test
    void booleanValuesDetected() throws Exception {
        Path file = writeJsonl("""
            {"active": true}
            {"active": false}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.active");

        assertThat(col.type()).isEqualTo(InferredType.BOOLEAN);
        assertThat(col.sampleValues()).containsExactly("true", "false");
    }

    // ───────── date detection ─────────

    @Test
    void dateStringDetectedAsDate() throws Exception {
        Path file = writeJsonl("""
            {"born": "2024-01-15"}
            {"born": "2023-06-30"}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.born");

        assertThat(col.type()).isEqualTo(InferredType.DATE);
    }

    // ───────── timestamp detection ─────────

    @Test
    void isoTimestampDetected() throws Exception {
        Path file = writeJsonl("""
            {"created": "2024-01-15T10:30:00Z"}
            {"created": "2023-06-30T14:00:00+08:00"}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.created");

        assertThat(col.type()).isEqualTo(InferredType.TIMESTAMP);
    }

    // ───────── sampleSize limits lines processed ─────────

    @Test
    void sampleSizeLimitsLinesProcessed() throws Exception {
        Path file = writeJsonl("""
            {"v": "first"}
            {"v": "second"}
            {"v": "third"}
            """);

        IngestionMapping mapping = parser.infer(file, 1);
        MappingColumn col = columnMap(mapping).get("$.v");

        assertThat(col.sampleValues()).containsExactly("first");
    }

    // ───────── blank lines are skipped ─────────

    @Test
    void blankLinesSkipped() throws Exception {
        Path file = writeJsonl("""
            {"x": 1}

            {"x": 2}

            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.x");

        assertThat(col.sampleValues()).hasSize(2);
    }

    // ───────── columns accumulated across lines ─────────

    @Test
    void columnsAccumulatedAcrossLines() throws Exception {
        // Line 1 has "a", line 2 has "b", line 3 has "a" again
        Path file = writeJsonl("""
            {"key": "val1"}
            {"key": "val2"}
            {"key": "val1"}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.key");

        // sampleValues should have 2 distinct values in encounter order
        assertThat(col.sampleValues()).containsExactly("val1", "val2");
    }

    // ───────── mappingId format ─────────

    @Test
    void mappingIdHasExpectedPrefix() throws Exception {
        Path file = writeJsonl("{\"a\":1}\n");
        IngestionMapping mapping = parser.infer(file, 10);

        assertThat(mapping.mappingId()).startsWith("map_");
    }

    // ───────── inconsistent schemas across lines ─────────

    @Test
    void inconsistentKeysAcrossLines() throws Exception {
        Path file = writeJsonl("""
            {"a": 1}
            {"b": "two"}
            {"a": 3, "b": "four"}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols).containsKeys("$.a", "$.b");
        // $.a is missing from line 2, but present in lines 1 and 3
        assertThat(cols.get("$.a").type()).isEqualTo(InferredType.INTEGER_32);
    }

    // ───────── integer type detection ─────────

    @Test
    void integerValuesDetected() throws Exception {
        Path file = writeJsonl("""
            {"count": 0}
            {"count": 42}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        assertThat(columnMap(mapping).get("$.count").type()).isEqualTo(InferredType.INTEGER_32);
    }

    // ───────── decimal type detection ─────────

    @Test
    void decimalValuesDetected() throws Exception {
        Path file = writeJsonl("""
            {"price": 19.99}
            {"price": 5.50}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        assertThat(columnMap(mapping).get("$.price").type()).isEqualTo(InferredType.DECIMAL);
    }

    // ───────── long type detection ─────────

    @Test
    void longValuesDetected() throws Exception {
        Path file = writeJsonl("""
            {"bigId": 9223372036854775807}
            {"bigId": 100}
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        assertThat(columnMap(mapping).get("$.bigId").type()).isEqualTo(InferredType.INTEGER_64);
    }
}
