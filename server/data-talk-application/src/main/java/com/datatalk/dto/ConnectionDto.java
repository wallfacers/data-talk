package com.datatalk.dto;

public record ConnectionDto(
    String id,
    String kind,
    String host,
    int port,
    String databaseName,
    String username,
    long createdAt
) {}