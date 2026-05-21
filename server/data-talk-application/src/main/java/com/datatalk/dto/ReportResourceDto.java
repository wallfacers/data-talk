package com.datatalk.dto;

import java.util.List;

public record ReportResourceDto(
    String id,
    String title,
    List<String> availableFormats,  // e.g. ["html", "pdf", "md"]
    long sizeBytes,
    String originSessionId,
    long createdAt,
    long updatedAt
) {}
