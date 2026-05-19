package com.datatalk.dto;

public record UploadResourceDto(
    String id,
    String filename,
    String mimeType,
    long sizeBytes,
    String originSessionId,
    long createdAt,
    long expiresAt
) {}
