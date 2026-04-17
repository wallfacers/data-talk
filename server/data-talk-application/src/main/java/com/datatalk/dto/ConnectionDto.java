package com.datatalk.dto;

public record ConnectionDto(
    String id,
    String kind,
    String host,
    int port,
    String databaseName,  // nullable
    String username,
    long createdAt
) {}