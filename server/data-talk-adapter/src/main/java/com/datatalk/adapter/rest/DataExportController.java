package com.datatalk.adapter.rest;

import com.datatalk.application.importexport.DataExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/exports")
public class DataExportController {

    private static final Logger log = LoggerFactory.getLogger(DataExportController.class);
    private static final long TTL_MILLIS = 3_600_000L; // 1 hour

    private final DataExportService exportService;

    public DataExportController(DataExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Housekeeping deferred to ApplicationReady so the main initialization
     * thread is not blocked on a disk scan.  Failures are logged but do not
     * propagate — they must never affect `/api/health` readiness signaling.
     */
    @EventListener(ApplicationReadyEvent.class)
    void scheduleCleanupAfterReady() {
        CompletableFuture.runAsync(() -> {
            try {
                exportService.cleanupOldExports();
            } catch (Throwable t) {
                log.warn("Background export cleanup failed (non-fatal)", t);
            }
        });
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

    /**
     * Frontend-triggered export: streams the exported file directly from request memory
     * to the response body without writing a temp file to disk. Used by the SQL result
     * table and chat markdown tables for Excel/SQL INSERT downloads of already-loaded data.
     *
     * The return type must be ResponseEntity<StreamingResponseBody> (not the wildcard) so
     * Spring's StreamingResponseBodyReturnValueHandler engages the async pipeline. Validation
     * errors throw {@link StreamExportException}, converted to JSON by the handler below.
     */
    @PostMapping("/data")
    public ResponseEntity<StreamingResponseBody> exportData(@RequestBody ExportDataRequest request) {
        if (request.columns() == null || request.rows() == null || request.format() == null) {
            throw new StreamExportException(400, "BAD_REQUEST",
                "columns, rows and format are required");
        }
        DataExportService.StreamExportRejection rejection =
            exportService.validateStreamExport(request.rows().size(), request.format());
        if (rejection != null) {
            throw new StreamExportException(rejection.httpStatus(), rejection.errorCode(), rejection.message());
        }

        String filename = exportService.buildStreamExportFilename(request.tableName(), request.format());
        MediaType contentType = contentTypeForFile(filename);
        StreamingResponseBody body = out -> exportService.exportToStream(
            request.columns(), request.rows(), request.format(), request.tableName(), out);

        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }

    @ExceptionHandler(StreamExportException.class)
    public ResponseEntity<Map<String, Object>> handleStreamExportException(StreamExportException ex) {
        return ResponseEntity.status(ex.status())
            .body(Map.of("errorCode", ex.errorCode(), "message", ex.getMessage()));
    }

    /** Signals a validation failure on the stream export path. */
    static class StreamExportException extends RuntimeException {
        private final int status;
        private final String errorCode;
        StreamExportException(int status, String errorCode, String message) {
            super(message);
            this.status = status;
            this.errorCode = errorCode;
        }
        int status() { return status; }
        String errorCode() { return errorCode; }
    }

    public record ExportDataRequest(
        List<String> columns,
        List<List<String>> rows,
        String format,
        String tableName
    ) {}

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
