package com.datatalk.dto;

public record ExportResourceDto(
    String exportId,
    String filename,
    String format,  // csv, json, xlsx, sql_insert
    long sizeBytes,
    long rowCount,
    String originSessionId,
    long createdAt,
    long expiresAt
) {}
