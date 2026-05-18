package com.datatalk.adapter.rest;

import com.datatalk.application.importexport.DataExportService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/exports")
public class DataExportController {

    private static final Logger log = LoggerFactory.getLogger(DataExportController.class);
    private static final long TTL_MILLIS = 3_600_000L; // 1 hour

    private final DataExportService exportService;

    public DataExportController(DataExportService exportService) {
        this.exportService = exportService;
    }

    @PostConstruct
    void init() {
        exportService.cleanupOldExports();
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<Resource> download(@PathVariable String exportId) {
        Path file = exportService.resolveExportFile(exportId);
        if (file == null || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        // Check TTL — delete files older than 1 hour
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            if (System.currentTimeMillis() - lastModified > TTL_MILLIS) {
                Files.deleteIfExists(file);
                // Try to clean up the parent directory
                Files.deleteIfExists(file.getParent());
                log.info("Deleted expired export: {}", exportId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.warn("Failed to check TTL for export {}", exportId, e);
        }

        String filename = file.getFileName().toString();
        MediaType contentType = contentTypeForFile(filename);
        long contentLength;
        try {
            contentLength = Files.size(file);
        } catch (Exception e) {
            contentLength = -1;
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentLength(contentLength)
            .body(resource);
    }

    private MediaType contentTypeForFile(String filename) {
        if (filename.endsWith(".csv")) {
            return MediaType.parseMediaType("text/csv");
        } else if (filename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else if (filename.endsWith(".xlsx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else if (filename.endsWith(".sql")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
