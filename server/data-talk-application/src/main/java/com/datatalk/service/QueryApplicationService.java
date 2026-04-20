package com.datatalk.service;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;

import java.time.Instant;
import java.util.Locale;

/**
 * 查询应用服务 - 编排用例
 */
public class QueryApplicationService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;
    private final SqlExecutionRepository sqlExecutionRepository;
    private final SqlStatementGuard statementGuard;

    public QueryApplicationService(ConnectionRepository connectionRepository,
                                   ConnectionService connectionService,
                                   SqlExecutionRepository sqlExecutionRepository,
                                   SqlStatementGuard statementGuard) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.sqlExecutionRepository = sqlExecutionRepository;
        this.statementGuard = statementGuard;
    }

    /**
     * 执行 SQL 查询
     */
    public QueryResponseDto executeQuery(ExecuteSqlCommand command) {
        statementGuard.assertSelectOnly(command.sql());
        DbConnection connection = connectionRepository.findById(command.connectionId())
                .map(this::toDbConnection)
                .orElseThrow(() -> new ConnectionNotFoundException(command.connectionId()));

        QueryResult result = sqlExecutionRepository.execute(connection, command.sql());

        return new QueryResponseDto(
                result.columns(),
                result.rows(),
                result.durationMs(),
                result.rowCount()
        );
    }

    private DbConnection toDbConnection(ConnectionRecord record) {
        return new DbConnection(
                record.id(),
                record.name(),
                toDbType(record.kind()),
                record.host(),
                record.port(),
                record.databaseName(),
                record.username(),
                connectionService.decryptPassword(record.id()),
                Instant.ofEpochMilli(record.createdAt())
        );
    }

    private static DbType toDbType(String kind) {
        return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> DbType.MYSQL;
            case "postgres", "postgresql" -> DbType.POSTGRESQL;
            case "sqlite" -> DbType.SQLITE;
            case "h2" -> DbType.H2;
            case "sqlserver" -> DbType.SQLSERVER;
            case "oracle" -> DbType.ORACLE;
            default -> throw new IllegalArgumentException("Unsupported database kind: " + kind);
        };
    }
}
