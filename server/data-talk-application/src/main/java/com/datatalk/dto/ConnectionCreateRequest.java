package com.datatalk.dto;

public record ConnectionCreateRequest(
    String kind,
    String host,
    int port,
    String databaseName,  // optional - null for server-level connection
    String username,
    String password
) {}