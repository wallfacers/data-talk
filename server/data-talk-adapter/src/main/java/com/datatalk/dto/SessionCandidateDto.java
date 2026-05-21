package com.datatalk.dto;

public record SessionCandidateDto(
    String id,
    String filename,
    String kind,
    long sizeBytes,
    String title,
    String summary
) {}