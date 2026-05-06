package com.datatalk.dto;

public record ConnectionCreateRequest(
    String name,
    String kind,
    String host,
    int port,
    String databaseName,  // optional - null for server-level connection
    String username,
    String password,
    Integer connectTimeout,  // optional, defaults to 3000ms
    String oracleServiceType,  // optional - null or 'service' = service name mode, 'sid' = SID mode (Oracle only)
    Boolean sqlserverEncrypt,  // optional - defaults to true
    Boolean sqlserverTrustServerCertificate,  // optional - defaults to true
    String sqlserverInstanceName  // optional - named instance
) {}