package com.datatalk.adapter.rest;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.importexport.DataExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class DataExportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DataExportService exportService;
    @MockBean Translator translator;

    @TempDir Path tempDir;

    @Test
    void downloadExistingFile_returns200() throws Exception {
        Path exportDir = tempDir.resolve("test-export-id");
        Files.createDirectories(exportDir);
        Path file = exportDir.resolve("data.csv");
        Files.writeString(file, "id,name\n1,Alice\n2,Bob\n");

        when(exportService.resolveExportFile("test-export-id")).thenReturn(file);

        mvc.perform(get("/api/exports/test-export-id/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"data.csv\""));
    }

    @Test
    void downloadNonExistentFile_returns404() throws Exception {
        when(exportService.resolveExportFile("missing-id")).thenReturn(null);

        mvc.perform(get("/api/exports/missing-id/download"))
            .andExpect(status().isNotFound());
    }

    @Test
    void downloadExpiredFile_returns404AndDeletesFile() throws Exception {
        Path exportDir = tempDir.resolve("expired-export-id");
        Files.createDirectories(exportDir);
        Path file = exportDir.resolve("old.csv");
        Files.writeString(file, "id,name\n1,Old\n");

        // Set lastModified to 2 hours ago
        long twoHoursAgo = System.currentTimeMillis() - 7_200_000L;
        file.toFile().setLastModified(twoHoursAgo);

        when(exportService.resolveExportFile("expired-export-id")).thenReturn(file);

        mvc.perform(get("/api/exports/expired-export-id/download"))
            .andExpect(status().isNotFound());

        // File should be deleted
        assert !Files.exists(file);
    }

    @Test
    void downloadJsonFile_returnsCorrectContentType() throws Exception {
        Path exportDir = tempDir.resolve("json-export-id");
        Files.createDirectories(exportDir);
        Path file = exportDir.resolve("data.json");
        Files.writeString(file, "[{\"id\": 1}]");

        when(exportService.resolveExportFile("json-export-id")).thenReturn(file);

        mvc.perform(get("/api/exports/json-export-id/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    void downloadXlsxFile_returnsCorrectContentType() throws Exception {
        Path exportDir = tempDir.resolve("xlsx-export-id");
        Files.createDirectories(exportDir);
        Path file = exportDir.resolve("data.xlsx");
        Files.write(file, new byte[]{0x50, 0x4B}); // dummy xlsx bytes

        when(exportService.resolveExportFile("xlsx-export-id")).thenReturn(file);

        mvc.perform(get("/api/exports/xlsx-export-id/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    // ── POST /api/exports/data ──────────────────────────────────────────

    @Test
    void exportData_streamsXlsxWithCorrectHeaders() throws Exception {
        when(exportService.validateStreamExport(anyInt(), eq("xlsx"))).thenReturn(null);
        when(exportService.buildStreamExportFilename(anyString(), eq("xlsx")))
            .thenReturn("export-orders-20260518-120000.xlsx");
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(4);
            out.write(new byte[]{0x50, 0x4B, 0x03, 0x04}); // xlsx (zip) magic
            return null;
        }).when(exportService).exportToStream(any(), any(), eq("xlsx"), anyString(), any(OutputStream.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id", "name"),
            "rows", List.of(List.of("1", "Alice"), List.of("2", "Bob")),
            "format", "xlsx",
            "tableName", "orders"
        ));

        MvcResult mvcResult = mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();

        // ResponseEntity<StreamingResponseBody> is processed asynchronously when the body
        // is StreamingResponseBody. Trigger async dispatch when present.
        if (mvcResult.getRequest().isAsyncStarted()) {
            mvcResult = mvc.perform(asyncDispatch(mvcResult)).andReturn();
        }

        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(mvcResult.getResponse().getHeader("Content-Type"))
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(mvcResult.getResponse().getHeader("Content-Disposition"))
            .isEqualTo("attachment; filename=\"export-orders-20260518-120000.xlsx\"");
        assertThat(mvcResult.getResponse().getContentAsByteArray())
            .startsWith((byte) 0x50, (byte) 0x4B);
    }

    @Test
    void exportData_missingColumns_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "rows", List.of(List.of("1")),
            "format", "csv",
            "tableName", "t"
        ));

        mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verify(exportService, never()).exportToStream(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void exportData_missingFormat_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id"),
            "rows", List.of(List.of("1")),
            "tableName", "t"
        ));

        mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void exportData_xlsxRowLimitExceeded_returns413AndDoesNotStream() throws Exception {
        when(exportService.validateStreamExport(anyInt(), eq("xlsx")))
            .thenReturn(new DataExportService.StreamExportRejection(
                413, "XLSX_ROW_LIMIT_EXCEEDED", "Row count exceeds the Excel maximum"));

        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id"),
            "rows", List.of(List.of("1")),
            "format", "xlsx",
            "tableName", "t"
        ));

        mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.errorCode").value("XLSX_ROW_LIMIT_EXCEEDED"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(exportService, never()).exportToStream(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void exportData_inMemoryRowLimitExceeded_returns413() throws Exception {
        when(exportService.validateStreamExport(anyInt(), eq("csv")))
            .thenReturn(new DataExportService.StreamExportRejection(
                413, "ROW_LIMIT_EXCEEDED", "Row count exceeds the in-memory export limit"));

        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id"),
            "rows", List.of(List.of("1")),
            "format", "csv",
            "tableName", "t"
        ));

        mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.errorCode").value("ROW_LIMIT_EXCEEDED"));
    }

    @Test
    void exportData_unsupportedFormat_returns400FromValidation() throws Exception {
        when(exportService.validateStreamExport(anyInt(), eq("parquet")))
            .thenReturn(new DataExportService.StreamExportRejection(
                400, "UNSUPPORTED_FORMAT", "Unsupported format: parquet"));

        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id"),
            "rows", List.of(List.of("1")),
            "format", "parquet",
            "tableName", "t"
        ));

        mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    void exportData_streamsCsvWithCorrectHeaders() throws Exception {
        when(exportService.validateStreamExport(anyInt(), eq("csv"))).thenReturn(null);
        when(exportService.buildStreamExportFilename(anyString(), eq("csv")))
            .thenReturn("export-t-20260518-120000.csv");
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(4);
            out.write("id,name\n1,Alice\n".getBytes());
            return null;
        }).when(exportService).exportToStream(any(), any(), eq("csv"), anyString(), any(OutputStream.class));

        String body = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("id", "name"),
            "rows", List.of(List.of("1", "Alice")),
            "format", "csv",
            "tableName", "t"
        ));

        MvcResult mvcResult = mvc.perform(post("/api/exports/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();

        if (mvcResult.getRequest().isAsyncStarted()) {
            mvcResult = mvc.perform(asyncDispatch(mvcResult)).andReturn();
        }

        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(mvcResult.getResponse().getHeader("Content-Type")).isEqualTo("text/csv");
        assertThat(mvcResult.getResponse().getHeader("Content-Disposition"))
            .isEqualTo("attachment; filename=\"export-t-20260518-120000.csv\"");
        assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("id,name\n1,Alice\n");
    }
}
