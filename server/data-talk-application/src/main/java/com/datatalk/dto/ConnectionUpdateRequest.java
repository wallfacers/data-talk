package com.datatalk.dto;

public record ConnectionUpdateRequest(
    String kind,
    String host,
    int port,
    String databaseName,
    String username,
    String password
) {}