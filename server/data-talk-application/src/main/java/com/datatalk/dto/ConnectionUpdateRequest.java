package com.datatalk.dto;

public record ConnectionUpdateRequest(
    String kind,
    String host,
    int port,
    String databaseName,  // optional - null for server-level connection
    String username,
    String password,
    Integer connectTimeout  // optional, defaults to 3000ms
) {}