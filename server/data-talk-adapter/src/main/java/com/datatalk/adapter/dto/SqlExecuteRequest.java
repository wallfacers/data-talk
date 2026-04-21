package com.datatalk.adapter.dto;

public record SqlExecuteRequest(
    String connectionId,
    String sql,
    String source,
    String sessionId,
    String database,
    String schema
) {}
