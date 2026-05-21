package com.datatalk.adapter.dto;

public record SqlExecuteRequest(
    String connectionId,
    String sql,
    String source,
    String sessionId,
    String database,
    String schema,
    Boolean confirmed,
    String riskAck
) {
    public boolean confirmedFlag() {
        return Boolean.TRUE.equals(confirmed);
    }
}
