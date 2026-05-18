package com.datatalk.application.importexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStreamReaderTest {

    @TempDir
    Path tempDir;

    private SqlStreamReader reader = new SqlStreamReader();

    private Path writeSql(String content) throws Exception {
        Path file = tempDir.resolve("test.sql");
        Files.writeString(file, content);
        return file;
    }

    // ── 1. Pure INSERT file (happy path) ──────────────────────────────

    @Test
    void pureInsert_happyPath() throws Exception {
        Path file = writeSql("""
            INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30);
            INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25);
            INSERT INTO users (id, name, age) VALUES (3, 'Charlie', 35);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.columns()).containsExactly("id", "name", "age");
        assertThat(result.sampleRows()).hasSize(3);
        assertThat(allRows).hasSize(3);

        // Verify types
        assertThat(allRows.get(0).get("id")).isEqualTo(1L);
        assertThat(allRows.get(0).get("name")).isEqualTo("Alice");
        assertThat(allRows.get(0).get("age")).isEqualTo(30L);
    }

    // ── 2. mysqldump single-line giant INSERT with many VALUES ────────

    @Test
    void mysqldump_singleLineGiantInsert() throws Exception {
        Path file = writeSql("""
            INSERT INTO orders (order_id, product, qty) VALUES (101,'Widget',5),(102,'Gadget',3),(103,'Doohickey',10),(104,'Thingamajig',7),(105,'Whatchamacallit',2);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.columns()).containsExactly("order_id", "product", "qty");
        assertThat(allRows).hasSize(5);
        assertThat(allRows.get(0).get("product")).isEqualTo("Widget");
        assertThat(allRows.get(4).get("product")).isEqualTo("Whatchamacallit");
    }

    // ── 3. Multi-line INSERT (columns and VALUES on different lines) ──

    @Test
    void multiLineInsert() throws Exception {
        Path file = writeSql("""
            INSERT INTO products
              (id, name, price)
            VALUES
              (1, 'Laptop', 999.99),
              (2, 'Mouse', 29.95),
              (3, 'Keyboard', 79.50);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.columns()).containsExactly("id", "name", "price");
        assertThat(allRows.get(0).get("price")).isEqualTo(999.99);
        assertThat(allRows.get(1).get("price")).isEqualTo(29.95);
    }

    // ── 4. Values containing semicolons or newlines within quotes ────

    @Test
    void valuesWithSemicolonsInQuotes() throws Exception {
        Path file = writeSql("""
            INSERT INTO logs (id, message) VALUES (1, 'Error: timeout; retrying');
            INSERT INTO logs (id, message) VALUES (2, 'All good');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(allRows.get(0).get("message")).isEqualTo("Error: timeout; retrying");
    }

    @Test
    void valuesWithNewlinesInQuotes() throws Exception {
        Path file = writeSql("""
            INSERT INTO notes (id, content) VALUES (1, 'Line one
            Line two
            Line three');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        String content = (String) allRows.get(0).get("content");
        assertThat(content).contains("Line one");
        assertThat(content).contains("Line two");
        assertThat(content).contains("Line three");
    }

    // ── 5. Multi-row VALUES syntax ────────────────────────────────────

    @Test
    void multiRowValues() throws Exception {
        Path file = writeSql("""
            INSERT INTO scores (student, subject, score) VALUES ('Alice','Math',95),('Bob','Math',87),('Charlie','Math',92);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(allRows.get(0).get("student")).isEqualTo("Alice");
        assertThat(allRows.get(1).get("student")).isEqualTo("Bob");
        assertThat(allRows.get(2).get("student")).isEqualTo("Charlie");
    }

    // ── 6. Partial parse failure (warning count / skip non-INSERT) ────

    @Test
    void partialParse_skipsNonInsertStatements() throws Exception {
        Path file = writeSql("""
            CREATE TABLE users (id INT, name VARCHAR(100));
            INSERT INTO users (id, name) VALUES (1, 'Alice');
            UPDATE users SET name = 'Bob' WHERE id = 1;
            INSERT INTO users (id, name) VALUES (2, 'Charlie');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        // Only INSERT statements produce rows
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(allRows).hasSize(2);
    }

    @Test
    void columnCountMismatch_skipsRow() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (a, b) VALUES (1, 2);
            INSERT INTO t (a, b) VALUES (3, 4, 5);
            INSERT INTO t (a, b) VALUES (6, 7);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        // Second INSERT has wrong column count, should be skipped
        assertThat(result.totalRows()).isEqualTo(2);
    }

    // ── 7. Empty file ─────────────────────────────────────────────────

    @Test
    void emptyFile() throws Exception {
        Path file = writeSql("");

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(0);
        assertThat(result.columns()).isEmpty();
        assertThat(result.sampleRows()).isEmpty();
    }

    @Test
    void fileWithOnlyComments() throws Exception {
        Path file = writeSql("""
            -- This is a comment
            /* block comment */
            -- another comment
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(0);
    }

    // ── 8. Column name mismatch between INSERTs ───────────────────────

    @Test
    void columnMismatch_secondInsertHasDifferentCols_stillParsedWithFirstSchema() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, name) VALUES (1, 'Alice');
            INSERT INTO t (x, y) VALUES (2, 'Bob');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        // Both rows parsed using the first INSERT's column schema (id, name)
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.columns()).containsExactly("id", "name");
        assertThat(allRows.get(1).get("id")).isEqualTo(2L);
        assertThat(allRows.get(1).get("name")).isEqualTo("Bob");
    }

    // ── 9. Single statement exceeding 10MB ────────────────────────────

    @Test
    void statementExceeding10MB_skipped() throws Exception {
        // Build a string that simulates a giant INSERT
        // We create a statement body > 10MB with no semicolons
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO giant (data) VALUES ('");
        // Fill to > 10MB
        String filler = "x".repeat(1024);
        for (int i = 0; i < 10 * 1024 + 1; i++) { // 10*1024 KB + 1KB > 10MB
            sb.append(filler);
        }
        sb.append("')");

        // Write it without semicolon — it will be the trailing statement
        Path file = writeSql(sb.toString());

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        // Statement should be skipped
        assertThat(result.totalRows()).isEqualTo(0);
        assertThat(allRows).isEmpty();
    }

    // ── Value type parsing ────────────────────────────────────────────

    @Test
    void valueTypeParsing_allTypes() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (int_col, dec_col, str_col, null_col, bool_col, bare_str)
            VALUES (42, 3.14, 'hello', NULL, TRUE, some_text);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        Map<String, Object> row = allRows.get(0);
        assertThat(row.get("int_col")).isEqualTo(42L);
        assertThat(row.get("dec_col")).isEqualTo(3.14);
        assertThat(row.get("str_col")).isEqualTo("hello");
        assertThat(row.get("null_col")).isNull();
        assertThat(row.get("bool_col")).isEqualTo(true);
        assertThat(row.get("bare_str")).isEqualTo("some_text");
    }

    @Test
    void escapedQuotes_inValues() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, msg) VALUES (1, 'it''s a test');
            INSERT INTO t (id, msg) VALUES (2, "he said \\"hi\\"");
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(allRows.get(0).get("msg")).isEqualTo("it's a test");
        assertThat(allRows.get(1).get("msg")).isEqualTo("he said \"hi\"");
    }

    // ── Schema prefix in table name ───────────────────────────────────

    @Test
    void schemaPrefixInTableName() throws Exception {
        Path file = writeSql("""
            INSERT INTO mydb.users (id, name) VALUES (1, 'Alice');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(allRows.get(0).get("name")).isEqualTo("Alice");
    }

    @Test
    void quotedSchemaPrefix() throws Exception {
        Path file = writeSql("""
            INSERT INTO "public"."users" (id, name) VALUES (1, 'Alice');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(allRows.get(0).get("name")).isEqualTo("Alice");
    }

    // ── DDL type inference ────────────────────────────────────────────

    @Test
    void inferDdlTypesFromTokens() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, score, name, active) VALUES (1, 3.14, 'Alice', TRUE);
            INSERT INTO t (id, score, name, active) VALUES (2, 2.71, 'Bob', FALSE);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        List<String> ddlTypes = reader.inferDdlTypesFromTokens(result.columns(), result.sampleRows(), null);

        assertThat(ddlTypes).containsExactly("BIGINT", "DOUBLE", "VARCHAR(255)", "BOOLEAN");
    }

    @Test
    void inferDdlTypes_withColumnOverrides() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, name) VALUES (1, 'Alice');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        Map<String, String> overrides = Map.of("id", "TEXT");
        List<String> ddlTypes = reader.inferDdlTypesFromTokens(result.columns(), result.sampleRows(), overrides);

        assertThat(ddlTypes).containsExactly("TEXT", "VARCHAR(255)");
    }

    @Test
    void inferDdlTypes_nullOnlyColumn_defaultsToVarchar() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, nullable_col) VALUES (1, NULL);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        List<String> ddlTypes = reader.inferDdlTypesFromTokens(result.columns(), result.sampleRows(), null);

        assertThat(ddlTypes).containsExactly("BIGINT", "VARCHAR(255)");
    }

    // ── Batch consumer ────────────────────────────────────────────────

    @Test
    void batchConsumer_calledAtBatchSize() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO t (id, val) VALUES ");
        for (int i = 1; i <= 5; i++) {
            if (i > 1) sb.append(",");
            sb.append("(").append(i).append(",'v").append(i).append("')");
        }
        sb.append(";");
        Path file = writeSql(sb.toString());

        List<List<Map<String, Object>>> batches = new ArrayList<>();
        var result = reader.stream(file, 2, batch -> batches.add(new ArrayList<>(batch)), null, null);

        assertThat(result.totalRows()).isEqualTo(5);
        // With batch size 2 and 5 rows: batch sizes should be 2, 2, 1
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(2);
        assertThat(batches.get(2)).hasSize(1);
    }

    // ── Quoted column names ───────────────────────────────────────────

    @Test
    void quotedColumnNames() throws Exception {
        Path file = writeSql("""
            INSERT INTO t ("id", "name") VALUES (1, 'Alice');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.columns()).containsExactly("id", "name");
    }

    @Test
    void backtickQuotedColumnNames() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (`id`, `name`) VALUES (1, 'Alice');
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.columns()).containsExactly("id", "name");
    }

    // ── Comments between statements ───────────────────────────────────

    @Test
    void commentsBetweenStatements() throws Exception {
        Path file = writeSql("""
            -- header comment
            /* multi-line
               comment */
            INSERT INTO t (id) VALUES (1);
            -- trailing comment
            INSERT INTO t (id) VALUES (2);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        var result = reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(result.totalRows()).isEqualTo(2);
    }

    // ── FALSE boolean ─────────────────────────────────────────────────

    @Test
    void falseBoolean() throws Exception {
        Path file = writeSql("""
            INSERT INTO t (id, active) VALUES (1, FALSE);
            """);

        List<Map<String, Object>> allRows = new ArrayList<>();
        reader.stream(file, 100, allRows::addAll, null, null);

        assertThat(allRows.get(0).get("active")).isEqualTo(false);
    }
}
