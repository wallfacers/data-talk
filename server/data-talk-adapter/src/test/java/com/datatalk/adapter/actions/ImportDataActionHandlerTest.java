package com.datatalk.adapter.actions;

import com.datatalk.application.importexport.DataImportService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportDataActionHandlerTest {

    private DataImportService importService;
    private ImportDataActionHandler handler;
    private ActionContext ctx;

    @BeforeEach
    void setUp() {
        importService = mock(DataImportService.class);
        handler = new ImportDataActionHandler(importService);
        ctx = new ActionContext("s1", "call1", null, null);
    }

    // ── file import ──────────────────────────────────────────────────

    @Test
    void fileImport_delegatesAndReturnsResultMap() {
        DataImportService.ImportResult mockResult = new DataImportService.ImportResult(
            42, "target_table",
            List.of(
                new DataImportService.ColumnInfo("id", "INTEGER"),
                new DataImportService.ColumnInfo("name", "VARCHAR(255)")
            ),
            List.of("warning: 2 rows skipped"),
            List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob")),
            "import-123"
        );

        when(importService.importFromFile(eq("file-1"), eq("conn-1"), eq("target_table"),
            eq(true), isNull(), isNull())).thenReturn(mockResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "file-1"),
            "target", Map.of("connectionId", "conn-1", "tableName", "target_table")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("rowsImported", 42);
        assertThat(result).containsEntry("tableName", "target_table");
        assertThat(result).containsEntry("importId", "import-123");
        assertThat(result).containsEntry("warnings", List.of("warning: 2 rows skipped"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0)).containsEntry("name", "id").containsEntry("type", "INTEGER");
        assertThat(columns.get(1)).containsEntry("name", "name").containsEntry("type", "VARCHAR(255)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sampleRows = (List<Map<String, Object>>) result.get("sampleRows");
        assertThat(sampleRows).hasSize(2);

        verify(importService).importFromFile("file-1", "conn-1", "target_table",
            true, null, null);
    }

    @Test
    void fileImport_withColumnMappingsAndTypes() {
        DataImportService.ImportResult mockResult = new DataImportService.ImportResult(
            10, "tbl", List.of(), List.of(), List.of(), "id-1"
        );
        Map<String, String> mappings = Map.of("old_col", "new_col");
        Map<String, String> types = Map.of("new_col", "TEXT");

        when(importService.importFromFile(eq("f1"), eq("c1"), eq("tbl"),
            eq(false), eq(mappings), eq(types))).thenReturn(mockResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1"),
            "target", Map.of("connectionId", "c1", "tableName", "tbl"),
            "createTable", false,
            "columnMappings", mappings,
            "columnTypes", types
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).doesNotContainKey("error");
        verify(importService).importFromFile("f1", "c1", "tbl", false, mappings, types);
    }

    // ── query import ─────────────────────────────────────────────────

    @Test
    void queryImport_delegatesAndReturnsResultMap() {
        DataImportService.ImportResult mockResult = new DataImportService.ImportResult(
            100, "dest_table",
            List.of(new DataImportService.ColumnInfo("col_a", "TEXT")),
            List.of(),
            List.of(),
            "import-456"
        );

        when(importService.importFromQuery(eq("src-conn"), eq("SELECT * FROM src"),
            eq("dst-conn"), eq("dest_table"), eq(true))).thenReturn(mockResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("type", "query", "connectionId", "src-conn", "sql", "SELECT * FROM src"),
            "target", Map.of("connectionId", "dst-conn", "tableName", "dest_table")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsEntry("rowsImported", 100);
        assertThat(result).containsEntry("tableName", "dest_table");
        assertThat(result).containsEntry("importId", "import-456");
        assertThat(result).doesNotContainKey("error");

        verify(importService).importFromQuery("src-conn", "SELECT * FROM src",
            "dst-conn", "dest_table", true);
    }

    // ── validation errors ────────────────────────────────────────────

    @Test
    void invalidSourceType_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "unknown"),
            "target", Map.of("connectionId", "c1", "tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("Invalid source type");
        verifyNoInteractions(importService);
    }

    @Test
    void missingSource_returnsError() {
        Map<String, Object> input = Map.of(
            "target", Map.of("connectionId", "c1", "tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("source is required");
        verifyNoInteractions(importService);
    }

    @Test
    void missingTarget_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("target is required");
        verifyNoInteractions(importService);
    }

    @Test
    void missingTargetConnectionId_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1"),
            "target", Map.of("tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("target.connectionId is required");
        verifyNoInteractions(importService);
    }

    @Test
    void missingTargetTableName_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1"),
            "target", Map.of("connectionId", "c1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("target.tableName is required");
        verifyNoInteractions(importService);
    }

    @Test
    void fileSource_missingFileId_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file"),
            "target", Map.of("connectionId", "c1", "tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("source.fileId is required");
        verifyNoInteractions(importService);
    }

    @Test
    void querySource_missingConnectionId_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "query", "sql", "SELECT 1"),
            "target", Map.of("connectionId", "c1", "tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("source.connectionId is required");
        verifyNoInteractions(importService);
    }

    @Test
    void querySource_missingSql_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("type", "query", "connectionId", "src-conn"),
            "target", Map.of("connectionId", "c1", "tableName", "t1")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("source.sql is required");
        verifyNoInteractions(importService);
    }

    // ── sampleRows limiting ──────────────────────────────────────────

    @Test
    void sampleRows_limitedToThreeRows() {
        List<Map<String, Object>> manyRows = IntStream.range(0, 10)
            .mapToObj(i -> Map.<String, Object>of("id", i, "val", "row-" + i))
            .toList();

        DataImportService.ImportResult mockResult = new DataImportService.ImportResult(
            10, "tbl", List.of(), List.of(), manyRows, "id-limit"
        );

        when(importService.importFromFile(eq("f1"), eq("c1"), eq("tbl"),
            eq(true), isNull(), isNull())).thenReturn(mockResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1"),
            "target", Map.of("connectionId", "c1", "tableName", "tbl")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sampleRows = (List<Map<String, Object>>) result.get("sampleRows");
        assertThat(sampleRows).hasSize(3);
        assertThat(sampleRows.get(0)).containsEntry("id", 0);
        assertThat(sampleRows.get(2)).containsEntry("id", 2);
    }

    // ── createTable default ──────────────────────────────────────────

    @Test
    void createTable_defaultsToTrue() {
        DataImportService.ImportResult mockResult = new DataImportService.ImportResult(
            5, "tbl", List.of(), List.of(), List.of(), "id-ct"
        );

        when(importService.importFromFile(eq("f1"), eq("c1"), eq("tbl"),
            eq(true), isNull(), isNull())).thenReturn(mockResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("type", "file", "fileId", "f1"),
            "target", Map.of("connectionId", "c1", "tableName", "tbl")
        );

        Map result = handler.handle(ctx, input).toCompletableFuture().join();

        assertThat(result).doesNotContainKey("error");
        verify(importService).importFromFile("f1", "c1", "tbl", true, null, null);
    }
}
