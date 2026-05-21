package com.datatalk.dto;

public record ConnectionUpdateRequest(
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
    String sqlserverInstanceName,  // optional - named instance
    Boolean readOnly,               // optional - DuckDB read-only flag, preserves existing if null
    String compatibilityMode,       // optional - multi-mode compatibility mode
    String oceanbaseTenant,         // optional - OceanBase tenant
    String oceanbaseCluster         // optional - OceanBase cluster
) {}