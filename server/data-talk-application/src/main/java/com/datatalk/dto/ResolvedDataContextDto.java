package com.datatalk.dto;

public record ResolvedDataContextDto(
    String connectionId,
    String connectionName,
    String database,
    String schema,
    String selectedLevel
) {}
