package com.datatalk.chatdb.model;

import jakarta.validation.constraints.NotBlank;

public class QueryRequest {

    @NotBlank(message = "connectionId is required")
    private String connectionId;

    private String sql;

    public QueryRequest() {}

    public QueryRequest(String connectionId, String sql) {
        this.connectionId = connectionId;
        this.sql = sql;
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
}
