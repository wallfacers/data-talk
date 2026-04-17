package com.datatalk.dto;

public record ConnectionCreateRequest(
    String kind,
    String host,
    int port,
    String databaseName,
    String username,
    String password
) {}