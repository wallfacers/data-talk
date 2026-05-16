package com.datatalk.application.upload;

import java.util.Map;

/**
 * Structured result of local file analysis (no AI).
 *
 * @param type        file category: "SQL", "CSV", "EXCEL", "JSON", "TEXT", "UNKNOWN"
 * @param fullContent true if the file was smaller than 4KB and its content was included
 * @param content     full file content for small files, null for large files
 * @param summary     type-specific analysis summary (statement types, headers, sheet info, etc.)
 */
public record FileAnalysisResult(
        String type,
        boolean fullContent,
        String content,
        Map<String, Object> summary
) {}
