package com.datatalk.service;

import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.entity.DbConnection;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;

/**
 * 查询应用服务 - 编排用例
 */
public class QueryApplicationService {

    private final DbConnectionRepository connectionRepository;
    private final SqlExecutionRepository sqlExecutionRepository;
    private final SqlStatementGuard statementGuard;

    public QueryApplicationService(DbConnectionRepository connectionRepository,
                                   SqlExecutionRepository sqlExecutionRepository,
                                   SqlStatementGuard statementGuard) {
        this.connectionRepository = connectionRepository;
        this.sqlExecutionRepository = sqlExecutionRepository;
        this.statementGuard = statementGuard;
    }

    /**
     * 执行 SQL 查询
     */
    public QueryResponseDto executeQuery(ExecuteSqlCommand command) {
        statementGuard.assertSelectOnly(command.sql());
        DbConnection connection = connectionRepository.findById(command.connectionId())
                .orElseThrow(() -> new ConnectionNotFoundException(command.connectionId()));

        QueryResult result = sqlExecutionRepository.execute(connection, command.sql());

        return new QueryResponseDto(
                result.columns(),
                result.rows(),
                result.durationMs(),
                result.rowCount()
        );
    }
}
