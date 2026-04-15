package com.datatalk.repository;

import com.datatalk.entity.DbConnection;
import java.util.List;
import java.util.Optional;

/**
 * 数据库连接仓储接口
 */
public interface DbConnectionRepository {

    void save(DbConnection connection);

    Optional<DbConnection> findById(String id);

    List<DbConnection> findAll();

    void deleteById(String id);
}
