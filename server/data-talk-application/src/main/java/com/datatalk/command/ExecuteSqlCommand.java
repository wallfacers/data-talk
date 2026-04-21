package com.datatalk.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 执行 SQL 查询命令
 */
public record ExecuteSqlCommand(
        String connectionId,

        @NotBlank(message = "sql is required")
        String sql,

        String sessionId,

        String database,

        String schema
) {
    public ExecuteSqlCommand(String connectionId, String sql) {
        this(connectionId, sql, null, null, null);
    }
}
