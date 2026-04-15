package com.datatalk.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 执行 SQL 查询命令
 */
public record ExecuteSqlCommand(
        @NotBlank(message = "connectionId is required")
        String connectionId,

        @NotBlank(message = "sql is required")
        String sql
) {
}
