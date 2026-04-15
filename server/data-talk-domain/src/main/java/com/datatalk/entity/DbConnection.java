package com.datatalk.entity;

import java.time.Instant;

/**
 * 数据库连接实体
 */
public record DbConnection(
        String id,
        String name,
        DbType dbType,
        String host,
        Integer port,
        String databaseName,
        String username,
        Instant createdAt
) {

    public DbConnection {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (dbType == null) throw new IllegalArgumentException("dbType must not be null");
    }

    public static DbConnection of(String id, String name, DbType dbType, String host, Integer port, String databaseName, String username) {
        return new DbConnection(id, name, dbType, host, port, databaseName, username, Instant.now());
    }
}
