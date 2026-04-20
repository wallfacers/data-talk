package com.datatalk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionDataContextDto(
    String sessionId,
    String connectionId,
    String connectionNameSnapshot,
    String database,
    String schema,
    String selectedLevel,
    long updatedAt
) {}
