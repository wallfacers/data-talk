package com.datatalk.service;

import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.entity.DbConnection;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.exception.SqlExecutionException;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;

/**
 * 查询应用服务 - 编排用例
 */
public class QueryApplicationService {

    private final DbConnectionRepository connectionRepository;
    private final SqlExecutionRepository sqlExecutionRepository;

    public QueryApplicationService(DbConnectionRepository connectionRepository,
                                   SqlExecutionRepository sqlExecutionRepository) {
        this.connectionRepository = connectionRepository;
        this.sqlExecutionRepository = sqlExecutionRepository;
    }

    /**
     * 执行 SQL 查询
     */
    public QueryResponseDto executeQuery(ExecuteSqlCommand command) {
        DbConnection connection = connectionRepository.findById(command.connectionId())
                .orElseThrow(() -> new ConnectionNotFoundException(command.connectionId()));

        QueryResult result;
        try {
            result = sqlExecutionRepository.execute(connection, command.sql());
        } catch (SqlExecutionException e) {
            throw e;
        }

        return new QueryResponseDto(
                result.columns(),
                result.rows(),
                result.durationMs(),
                result.rowCount()
        );
    }
}
