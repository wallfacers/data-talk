package com.datatalk.dto;

public record ConnectionDto(
    String id,
    String name,
    String kind,
    String host,
    int port,
    String databaseName,  // nullable
    String username,
    long createdAt,
    int connectTimeout,
    String lastTestStatus,
    Long lastTestAt,
    String oracleServiceType  // nullable - null or 'service' = service name mode, 'sid' = SID mode (Oracle only)
) {}