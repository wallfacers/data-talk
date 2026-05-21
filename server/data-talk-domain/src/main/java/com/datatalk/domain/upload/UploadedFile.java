package com.datatalk.domain.upload;

import java.time.Instant;
import java.util.Map;

public record UploadedFile(
        String id,
        String sessionId,
        String filename,
        String mimeType,
        long sizeBytes,
        String physicalPath,
        Map<String, Object> analysis,
        Instant createdAt
) {}
