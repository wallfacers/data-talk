package com.datatalk.dto;

public record OrphanedFileDto(
        String id,
        String filename,
        String kind,
        long sizeBytes,
        String title,
        String summary,
        String orphanedFromConnection,
        String orphanedFromConnectionId,
        long orphanedAt,
        String archivedAt
) {}
