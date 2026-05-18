package com.datatalk.adapter.rest;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.importexport.DataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class DataExportControllerTest {

    @Autowired MockMvc mvc;

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
}
