package com.datatalk.application.persistence;

public record ConnectionRecord(
    String id, String name, String kind, String host, int port,
    String databaseName,  // nullable - null for server-level connection
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout,
    String lastTestStatus, Long lastTestAt,
    String oracleServiceType,  // nullable: null or 'service' = service name mode, 'sid' = SID mode
    int sqlserverEncrypt,              // 1 = encrypt (default), 0 = no encryption
    boolean sqlserverTrustServerCertificate,  // default true
    String sqlserverInstanceName,       // nullable: named instance
    boolean readOnly                    // DuckDB read-only flag
) {}
