package com.datatalk.service;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.application.sql.TableContextAutoResolver;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.dto.ResolvedDataContextDto;
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
    private final SessionDataContextService sessionDataContextService;
    private final SqlExecutionRepository sqlExecutionRepository;
    private final SqlStatementGuard statementGuard;
    private final TableContextAutoResolver tableContextAutoResolver;
    private final Translator translator;

    public QueryApplicationService(ConnectionRepository connectionRepository,
                                   ConnectionService connectionService,
                                   SessionDataContextService sessionDataContextService,
                                   SqlExecutionRepository sqlExecutionRepository,
                                   SqlStatementGuard statementGuard,
                                   TableContextAutoResolver tableContextAutoResolver,
                                   Translator translator) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.sessionDataContextService = sessionDataContextService;
        this.sqlExecutionRepository = sqlExecutionRepository;
        this.statementGuard = statementGuard;
        this.tableContextAutoResolver = tableContextAutoResolver;
        this.translator = translator;
    }

    /**
     * 执行 SQL 查询
     */
    public QueryResponseDto executeQuery(ExecuteSqlCommand command) {
        statementGuard.assertSelectOnly(command.sql());
        ResolvedExecutionContext context = tableContextAutoResolver.resolve(resolveExecutionContext(command), command.sql());
        DbConnection connection = toDbConnection(context.connection(), context.database());

        QueryResult result = sqlExecutionRepository.execute(connection, command.sql(), context.schema());

        return new QueryResponseDto(
                result.columns(),
                result.rows(),
                result.durationMs(),
                result.rowCount(),
                toResolvedContextDto(context),
                context.contextNotice()
        );
    }

    private ResolvedExecutionContext resolveExecutionContext(ExecuteSqlCommand command) {
        SessionDataContextRecord sessionContext = null;
        if (hasText(command.sessionId())) {
            sessionContext = sessionDataContextService.get(command.sessionId());
        }

        String connectionId = firstNonBlank(
            command.connectionId(),
            sessionContext == null ? null : sessionContext.connectionId()
        );
        if (!hasText(connectionId)) {
            throw new IllegalArgumentException(translator.get("error.connection.id_required"));
        }

        ConnectionRecord connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ConnectionNotFoundException(connectionId));
        boolean inheritsSessionScope = sessionContext != null && connectionId.equals(sessionContext.connectionId());
        String database = firstNonBlank(
            command.database(),
            inheritsSessionScope ? sessionContext.databaseName() : null,
            connection.databaseName()
        );
        String schema = firstNonBlank(
            command.schema(),
            inheritsSessionScope ? sessionContext.schemaName() : null
        );
        return new ResolvedExecutionContext(connection, database, schema);
    }

    private DbConnection toDbConnection(ConnectionRecord record, String databaseName) {
        return new DbConnection(
                record.id(),
                record.name(),
                toDbType(record.kind()),
                record.host(),
                record.port(),
                databaseName,
                record.username(),
                connectionService.decryptPassword(record.id()),
                Instant.ofEpochMilli(record.createdAt())
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private DbType toDbType(String kind) {
        return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> DbType.MYSQL;
            case "postgres", "postgresql" -> DbType.POSTGRESQL;
            case "sqlite" -> DbType.SQLITE;
            case "h2" -> DbType.H2;
            case "sqlserver" -> DbType.SQLSERVER;
            case "oracle" -> DbType.ORACLE;
            default -> throw new IllegalArgumentException(translator.get("error.database.kind.unsupported", kind));
        };
    }

    private static ResolvedDataContextDto toResolvedContextDto(ResolvedExecutionContext context) {
        return new ResolvedDataContextDto(
            context.connection().id(),
            context.connection().name(),
            context.database(),
            context.schema(),
            context.selectedLevel()
        );
    }
}
