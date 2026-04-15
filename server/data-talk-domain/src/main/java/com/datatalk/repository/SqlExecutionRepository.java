package com.datatalk.repository;

import com.datatalk.entity.DbConnection;
import com.datatalk.valueobject.QueryResult;

/**
 * SQL 执行仓储接口 - 负责动态连接目标数据库并执行 SQL
 */
public interface SqlExecutionRepository {

    QueryResult execute(DbConnection connection, String sql);
}
