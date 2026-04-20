package com.datatalk.dto;

public record SessionDataContextUpdateRequest(
    String connectionId,
    String database,
    String schema,
    String selectedLevel
) {}
