package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class CsvPayloadParserTest {

    @TempDir Path tempDir;
    CsvPayloadParser parser;

    @BeforeEach
    void setup() {
        parser = new CsvPayloadParser();
    }

    private Path writeCsv(String content) throws Exception {
        Path file = tempDir.resolve("payload.csv");
        Files.writeString(file, content);
        return file;
    }

    private Map<String, MappingColumn> columnMap(IngestionMapping m) {
        return m.columns().stream().collect(Collectors.toMap(MappingColumn::sourcePath, Function.identity()));
    }

    // BUG-0022: numeric column is inferred as INTEGER_32, not STRING.

    @Test
    void integerColumnInferredAsInteger32() throws Exception {
        Path file = writeCsv("""
            id,name
            1,alice
            2,bob
            3,carol
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("id").type()).isEqualTo(InferredType.INTEGER_32);
        assertThat(cols.get("name").type()).isEqualTo(InferredType.STRING_64);
    }

    // BUG-0022: decimal column is inferred as DECIMAL.

    @Test
    void decimalColumnInferredAsDecimal() throws Exception {
        Path file = writeCsv("""
            id,total
            1,100.50
            2,75.25
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("total").type()).isEqualTo(InferredType.DECIMAL);
    }

    // BUG-0022: boolean column is inferred as BOOLEAN.

    @Test
    void booleanColumnInferredAsBoolean() throws Exception {
        Path file = writeCsv("""
            id,active
            1,true
            2,false
            3,true
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("active").type()).isEqualTo(InferredType.BOOLEAN);
    }

    // BUG-0023: column with value > 2^31 promotes to INTEGER_64.

    @Test
    void largeIntegerColumnPromotesToInteger64() throws Exception {
        Path file = writeCsv("""
            v
            3000000000
            2147483648
            100
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("v").type()).isEqualTo(InferredType.INTEGER_64);
    }

    // Mixed types fall back to STRING_64.

    @Test
    void mixedStringAndNumberColumnFallsBackToString() throws Exception {
        Path file = writeCsv("""
            id
            1
            abc
            2
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        // 1 → Integer, abc → String, 2 → Integer. Widest STRING_*.
        assertThat(cols.get("id").type().name()).startsWith("STRING");
    }
}
