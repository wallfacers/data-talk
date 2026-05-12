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

class JsonPayloadParserTest {

    @TempDir Path tempDir;

    ObjectMapper om = new ObjectMapper();
    JsonPayloadParser parser;

    @BeforeEach
    void setup() {
        parser = new JsonPayloadParser(om);
    }

    private Path writeJson(String content) throws Exception {
        Path file = tempDir.resolve("payload.json");
        Files.writeString(file, content);
        return file;
    }

    private Map<String, MappingColumn> columnMap(IngestionMapping mapping) {
        return mapping.columns().stream()
            .collect(Collectors.toMap(MappingColumn::sourcePath, Function.identity()));
    }

    // ───────── integer + string mixed → STRING_64 ─────────

    @Test
    void integerAndStringMixedResolvesToString64() throws Exception {
        Path file = writeJson("""
            [
              {"val": 42},
              {"val": "hello"},
              {"val": 99}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.val");

        assertThat(col).isNotNull();
        assertThat(col.type()).isEqualTo(InferredType.STRING_64);
        assertThat(col.nullable()).isFalse();
    }

    // ───────── dot-path 1-level expansion ─────────

    @Test
    void nestedObjectExpandsOneLevel() throws Exception {
        Path file = writeJson("""
            [
              {"name": "alice", "address": {"city": "NYC", "zip": "10001"}},
              {"name": "bob",   "address": {"city": "LA",  "zip": "90001"}}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols).containsKeys("$.name", "$.address.city", "$.address.zip");
        assertThat(cols.get("$.name").type()).isEqualTo(InferredType.STRING_64);
        assertThat(cols.get("$.address.city").type()).isEqualTo(InferredType.STRING_64);
        assertThat(cols.get("$.address.zip").type()).isEqualTo(InferredType.STRING_64);

        // targetName uses underscore replacement
        assertThat(cols.get("$.address.city").targetName()).isEqualTo("address_city");
    }

    // ───────── deep nesting (depth >= 2) → JSON type ─────────

    @Test
    void deepNestingBecomesJsonType() throws Exception {
        Path file = writeJson("""
            [
              {"meta": {"tags": {"primary": "red", "secondary": "blue"}}},
              {"meta": {"tags": {"primary": "green", "secondary": "yellow"}}}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        // "meta" is expanded at depth 0 → meta.tags is depth 1 nested object → depth >= 2 threshold
        // Actually: meta is depth 0 object, so it expands. meta.tags is depth 1 object.
        // At depth >= 1, nested objects are NOT expanded further → treated as JSON blob
        assertThat(cols).containsKey("$.meta.tags");
        assertThat(cols.get("$.meta.tags").type()).isEqualTo(InferredType.JSON);
    }

    // ───────── all-null column → STRING_256 nullable ─────────

    @Test
    void allNullColumnResolvesToString256Nullable() throws Exception {
        Path file = writeJson("""
            [
              {"id": 1, "email": null},
              {"id": 2, "email": null},
              {"id": 3, "email": null}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn email = columnMap(mapping).get("$.email");

        assertThat(email).isNotNull();
        assertThat(email.type()).isEqualTo(InferredType.STRING_256);
        assertThat(email.nullable()).isTrue();
        assertThat(email.sampleValues()).isEmpty();
    }

    // ───────── boolean detection ─────────

    @Test
    void booleanValuesDetected() throws Exception {
        Path file = writeJson("""
            [
              {"active": true},
              {"active": false},
              {"active": true}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.active");

        assertThat(col.type()).isEqualTo(InferredType.BOOLEAN);
        assertThat(col.sampleValues()).containsExactly("true", "false");
    }

    // ───────── date detection ─────────

    @Test
    void dateStringDetectedAsDate() throws Exception {
        Path file = writeJson("""
            [
              {"born": "2024-01-15"},
              {"born": "2023-06-30"}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.born");

        assertThat(col.type()).isEqualTo(InferredType.DATE);
    }

    // ───────── timestamp detection ─────────

    @Test
    void isoTimestampDetected() throws Exception {
        Path file = writeJson("""
            [
              {"created": "2024-01-15T10:30:00Z"},
              {"created": "2023-06-30T14:00:00+08:00"}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.created");

        assertThat(col.type()).isEqualTo(InferredType.TIMESTAMP);
    }

    // ───────── integer detection ─────────

    @Test
    void integerValuesDetected() throws Exception {
        Path file = writeJson("""
            [
              {"count": 0},
              {"count": 42},
              {"count": 100}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.count");

        assertThat(col.type()).isEqualTo(InferredType.INTEGER_32);
    }

    // ───────── long detection ─────────

    @Test
    void longValuesDetected() throws Exception {
        Path file = writeJson("""
            [
              {"bigId": 9223372036854775807},
              {"bigId": 100}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.bigId");

        assertThat(col.type()).isEqualTo(InferredType.INTEGER_64);
    }

    // ───────── decimal detection ─────────

    @Test
    void decimalValuesDetected() throws Exception {
        Path file = writeJson("""
            [
              {"price": 19.99},
              {"price": 5.50}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.price");

        assertThat(col.type()).isEqualTo(InferredType.DECIMAL);
    }

    // ───────── string capacity ─────────

    @Test
    void longStringBecomesString256() throws Exception {
        String longStr = "a".repeat(100);
        Path file = writeJson("""
            [
              {"desc": \"""" + longStr + """
            "}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.desc");

        assertThat(col.type()).isEqualTo(InferredType.STRING_256);
    }

    // ───────── sampleValues limited to 2 distinct ─────────

    @Test
    void sampleValuesCappedAtTwo() throws Exception {
        Path file = writeJson("""
            [
              {"x": "a"},
              {"x": "b"},
              {"x": "c"},
              {"x": "d"}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.x");

        assertThat(col.sampleValues()).hasSize(2);
        assertThat(col.sampleValues()).containsExactly("a", "b");
    }

    // ───────── sampleSize limits rows processed ─────────

    @Test
    void sampleSizeLimitsRowsProcessed() throws Exception {
        Path file = writeJson("""
            [
              {"v": "first"},
              {"v": "second"},
              {"v": "third"},
              {"v": "fourth"}
            ]
            """);

        // Only sample 1 row
        IngestionMapping mapping = parser.infer(file, 1);
        MappingColumn col = columnMap(mapping).get("$.v");

        assertThat(col.sampleValues()).containsExactly("first");
    }

    // ───────── mappingId format ─────────

    @Test
    void mappingIdHasExpectedPrefix() throws Exception {
        Path file = writeJson("[{\"a\":1}]");
        IngestionMapping mapping = parser.infer(file, 10);

        assertThat(mapping.mappingId()).startsWith("map_");
    }

    // ───────── skip is always false ─────────

    @Test
    void skipDefaultsToFalse() throws Exception {
        Path file = writeJson("[{\"a\":1}]");
        IngestionMapping mapping = parser.infer(file, 10);

        assertThat(mapping.columns()).allSatisfy(col ->
            assertThat(col.skip()).isFalse());
    }

    // ───────── nullable when some values are null ─────────

    @Test
    void nullableWhenMixedNullAndValue() throws Exception {
        Path file = writeJson("""
            [
              {"status": "active"},
              {"status": null},
              {"status": "inactive"}
            ]
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        MappingColumn col = columnMap(mapping).get("$.status");

        assertThat(col.type()).isEqualTo(InferredType.STRING_64);
        // BUG-0033: any-null → nullable, aligning JSON parser with CSV/HTML.
        assertThat(col.nullable()).isTrue();
    }
}
