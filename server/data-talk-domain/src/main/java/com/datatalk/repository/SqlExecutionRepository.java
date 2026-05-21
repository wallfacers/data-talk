package com.datatalk.repository;

import com.datatalk.entity.DbConnection;
import com.datatalk.valueobject.QueryResult;

/**
 * SQL 执行仓储接口 - 负责动态连接目标数据库并执行 SQL
 */
public interface SqlExecutionRepository {

    default QueryResult execute(DbConnection connection, String sql) {
        return execute(connection, sql, null);
    }

    QueryResult execute(DbConnection connection, String sql, String schema);
}
