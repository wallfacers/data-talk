package com.datatalk.dto;

public record DashboardResourceDto(
    String id,
    String title,
    String filename,
    long sizeBytes,
    int widgetCount,
    String originSessionId,
    long createdAt,
    long updatedAt
) {}
