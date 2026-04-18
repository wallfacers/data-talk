package com.datatalk.application.persistence;

public record ConnectionRecord(
    String id, String name, String kind, String host, int port,
    String databaseName,  // nullable - null for server-level connection
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout,
    String lastTestStatus, Long lastTestAt
) {}
