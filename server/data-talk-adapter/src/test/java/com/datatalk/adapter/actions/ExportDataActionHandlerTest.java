package com.datatalk.adapter.actions;

import com.datatalk.application.importexport.DataExportService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportDataActionHandlerTest {

    private DataExportService exportService;
    private ExportDataActionHandler handler;

    @BeforeEach
    void setUp() {
        exportService = mock(DataExportService.class);
        handler = new ExportDataActionHandler(exportService);
    }

    private ActionContext testContext() {
        return new ActionContext("session-1", "call-1", "conn-1", "oc-1");
    }

    @Test
    void sqlBasedExport_delegatesCorrectly_andReturnsResultMap() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-123", "/api/exports/export-123/download", 42, 2048L,
            "csv", "completed", List.of()
        );
        when(exportService.export(eq("session-1"), eq("conn-1"), eq("SELECT * FROM users"),
            isNull(), eq("csv"), isNull(), isNull()))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT * FROM users"),
            "format", "csv"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("exportId", "export-123");
        assertThat(result).containsEntry("downloadUrl", "/api/exports/export-123/download");
        assertThat(result).containsEntry("rowCount", 42);
        assertThat(result).containsEntry("fileSize", 2048L);
        assertThat(result).containsEntry("format", "csv");
        assertThat(result).containsEntry("status", "completed");
        assertThat(result).containsEntry("warnings", List.of());
    }

    @Test
    void tableNameExport_delegatesCorrectly() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-456", "/api/exports/export-456/download", 100, 8192L,
            "json", "completed", List.of("Result set limited to 100 rows")
        );
        when(exportService.export(eq("session-1"), eq("conn-1"), isNull(),
            eq("orders"), eq("json"), isNull(), isNull()))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("tableName", "orders"),
            "format", "json"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("exportId", "export-456");
        assertThat(result).containsEntry("format", "json");
        assertThat(result).containsEntry("rowCount", 100);
        assertThat((List<?>) result.get("warnings")).hasSize(1);
    }

    @Test
    void missingFormat_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT 1")
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("format is required");
        verifyNoInteractions(exportService);
    }

    @Test
    void blankFormat_returnsError() {
        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT 1"),
            "format", "  "
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        verifyNoInteractions(exportService);
    }

    @Test
    void exportServiceException_returnsErrorResult() {
        when(exportService.export(anyString(), anyString(), anyString(), any(),
            eq("csv"), any(), any()))
            .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT 1"),
            "format", "csv"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsKey("error");
        assertThat((String) result.get("error")).contains("Export failed");
        assertThat((String) result.get("error")).contains("Connection refused");
    }

    @Test
    void optionsArePassedCorrectly() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-789", "/api/exports/export-789/download", 500, 4096L,
            "xlsx", "completed", List.of()
        );
        when(exportService.export(eq("session-1"), eq("conn-1"), eq("SELECT * FROM products"),
            isNull(), eq("xlsx"), eq("my-export"), eq(1000)))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT * FROM products"),
            "format", "xlsx",
            "options", Map.of("filename", "my-export", "maxRows", 1000)
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("exportId", "export-789");
        assertThat(result).containsEntry("format", "xlsx");
        verify(exportService).export("session-1", "conn-1", "SELECT * FROM products",
            null, "xlsx", "my-export", 1000);
    }

    @Test
    void connectionIdFromSource_takesPrecedenceOverContext() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-override", "/api/exports/export-override/download", 10, 512L,
            "csv", "completed", List.of()
        );
        when(exportService.export(eq("session-1"), eq("other-conn"), eq("SELECT 1"),
            isNull(), eq("csv"), isNull(), isNull()))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT 1", "connectionId", "other-conn"),
            "format", "csv"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("exportId", "export-override");
        verify(exportService).export("session-1", "other-conn", "SELECT 1",
            null, "csv", null, null);
    }

    @Test
    void fallbackConnectionId_fromContext_whenSourceOmitsIt() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-fallback", "/api/exports/export-fallback/download", 5, 256L,
            "csv", "completed", List.of()
        );
        when(exportService.export(eq("session-1"), eq("conn-1"), eq("SELECT 1"),
            isNull(), eq("csv"), isNull(), isNull()))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT 1"),
            "format", "csv"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("exportId", "export-fallback");
        verify(exportService).export("session-1", "conn-1", "SELECT 1",
            null, "csv", null, null);
    }

    @Test
    void processingStatus_returnsNullDownloadUrl() {
        DataExportService.ExportResult serviceResult = new DataExportService.ExportResult(
            "export-async", "/api/exports/export-async/download", 0, 0L,
            "csv", "processing", List.of("Result set has 50000 rows, limited to 1000000")
        );
        when(exportService.export(anyString(), anyString(), anyString(), any(),
            eq("csv"), any(), any()))
            .thenReturn(serviceResult);

        Map<String, Object> input = Map.of(
            "source", Map.of("sql", "SELECT * FROM big_table"),
            "format", "csv"
        );

        Map result = handler.handle(testContext(), input).toCompletableFuture().join();

        assertThat(result).containsEntry("status", "processing");
        assertThat(result).containsEntry("rowCount", 0);
        assertThat(result).containsEntry("fileSize", 0L);
    }
}
