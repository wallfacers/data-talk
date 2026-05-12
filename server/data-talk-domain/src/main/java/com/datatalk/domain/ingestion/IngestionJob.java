package com.datatalk.domain.ingestion;

import java.util.Map;

public record IngestionJob(
    String id,
    String sourceUrl,
    String sourceMethod,
    Map<String, String> sourceHeaders,
    Map<String, String> sourceQueryParams,
    String sourceBody,
    String credentialId,
    PaginationSpec pagination,
    PayloadFormat payloadFormat,
    String payloadArtifactId,
    String status,
    String connectionId,
    String targetSchema,
    String targetTable,
    IngestionMapping mapping,
    Integer rowCount,
    Integer rowsInserted,
    Long bytesFetched,
    long createdAt,
    long updatedAt,
    Long completedAt,
    String errorMessage) {}
