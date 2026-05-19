package com.datatalk.dto;

public record SemanticResourceDto(
    String domain,
    String connectionId,
    String connectionName,
    String status,  // "active" or "pending"
    long sizeBytes,
    long updatedAt
) {}
