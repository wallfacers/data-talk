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
    String oracleServiceType,  // nullable - null or 'service' = service name mode, 'sid' = SID mode (Oracle only)
    boolean sqlserverEncrypt,
    boolean sqlserverTrustServerCertificate,
    String sqlserverInstanceName,  // nullable - named instance
    boolean readOnly,              // DuckDB read-only flag
    // Wave C step 3 additions
    String compatibilityMode,      // null | "mysql" | "oracle" | "pg"
    String oceanbaseTenant,        // null unless kind='oceanbase'
    String oceanbaseCluster        // optional even for oceanbase
) {}