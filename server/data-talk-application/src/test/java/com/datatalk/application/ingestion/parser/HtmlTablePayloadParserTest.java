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

class HtmlTablePayloadParserTest {

    @TempDir Path tempDir;
    HtmlTablePayloadParser parser;

    @BeforeEach
    void setup() {
        parser = new HtmlTablePayloadParser();
    }

    private Path writeHtml(String content) throws Exception {
        Path file = tempDir.resolve("payload.html");
        Files.writeString(file, content);
        return file;
    }

    private Map<String, MappingColumn> columnMap(IngestionMapping m) {
        return m.columns().stream().collect(Collectors.toMap(MappingColumn::sourcePath, Function.identity()));
    }

    // BUG-0022: numeric column from HTML table is INTEGER_32, not STRING.

    @Test
    void integerColumnFromHtmlInferredAsInteger32() throws Exception {
        Path file = writeHtml("""
            <html><body><table>
              <thead><tr><th>rank</th><th>player</th></tr></thead>
              <tbody>
                <tr><td>1</td><td>Ada</td></tr>
                <tr><td>2</td><td>Bea</td></tr>
              </tbody>
            </table></body></html>
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("$.rank").type()).isEqualTo(InferredType.INTEGER_32);
        assertThat(cols.get("$.player").type()).isEqualTo(InferredType.STRING_64);
    }

    // BUG-0023: HTML cell > 2^31 promotes to INTEGER_64.

    @Test
    void largeIntegerHtmlCellPromotesToInteger64() throws Exception {
        Path file = writeHtml("""
            <html><body><table>
              <thead><tr><th>v</th></tr></thead>
              <tbody>
                <tr><td>3000000000</td></tr>
                <tr><td>100</td></tr>
              </tbody>
            </table></body></html>
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("$.v").type()).isEqualTo(InferredType.INTEGER_64);
    }

    // BUG-0022: decimal HTML cell coerces to DECIMAL.

    @Test
    void decimalCellInferredAsDecimal() throws Exception {
        Path file = writeHtml("""
            <html><body><table>
              <thead><tr><th>score</th></tr></thead>
              <tbody>
                <tr><td>99.5</td></tr>
                <tr><td>12.0</td></tr>
              </tbody>
            </table></body></html>
            """);

        IngestionMapping mapping = parser.infer(file, 100);
        Map<String, MappingColumn> cols = columnMap(mapping);

        assertThat(cols.get("$.score").type()).isEqualTo(InferredType.DECIMAL);
    }
}
