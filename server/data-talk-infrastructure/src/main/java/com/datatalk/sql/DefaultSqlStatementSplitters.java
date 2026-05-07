package com.datatalk.sql;

import com.datatalk.application.sql.SqlStatementSplitters;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultSqlStatementSplitters implements SqlStatementSplitters {

    private final PostgresJdbcSqlStatementSplitter postgresSplitter;
    private final MySqlSqlStatementSplitter mysqlSplitter;
    private final GenericSqlStatementSplitter genericSplitter;

    public DefaultSqlStatementSplitters(
        PostgresJdbcSqlStatementSplitter postgresSplitter,
        MySqlSqlStatementSplitter mysqlSplitter,
        GenericSqlStatementSplitter genericSplitter
    ) {
        this.postgresSplitter = postgresSplitter;
        this.mysqlSplitter = mysqlSplitter;
        this.genericSplitter = genericSplitter;
    }

    @Override
    public List<String> split(String connectionKind, String sql) {
        if ("postgres".equalsIgnoreCase(connectionKind) || "postgresql".equalsIgnoreCase(connectionKind)) {
            return postgresSplitter.split(sql);
        }
        if ("mysql".equalsIgnoreCase(connectionKind) || "mariadb".equalsIgnoreCase(connectionKind) || "apache_doris".equalsIgnoreCase(connectionKind) || "starrocks".equalsIgnoreCase(connectionKind)) {
            return mysqlSplitter.split(sql);
        }
        if ("oracle".equalsIgnoreCase(connectionKind)) {
            // TODO: Oracle day-1 uses generic single-statement splitting.
            //  Future: implement PL/SQL block-aware splitting (/ terminator,
            //  BEGIN...END blocks, EXECUTE IMMEDIATE, etc.)
            return genericSplitter.split(sql);
        }
        if ("sqlserver".equalsIgnoreCase(connectionKind)) {
            // TODO: SQL Server day-1 uses generic single-statement splitting.
            //  Future: implement GO batch-aware splitting (GO separator,
            //  batch scripts, sqlcmd mode, etc.)
            return genericSplitter.split(sql);
        }
        if ("trino".equalsIgnoreCase(connectionKind)) {
            return genericSplitter.split(sql);
        }
        if ("presto".equalsIgnoreCase(connectionKind)) {
            return genericSplitter.split(sql);
        }
        if ("duckdb".equalsIgnoreCase(connectionKind)) {
            // DuckDB has no DELIMITER, no PL/SQL, no GO — generic splitter is sufficient.
            return genericSplitter.split(sql);
        }
        if ("clickhouse".equalsIgnoreCase(connectionKind)) {
            // ClickHouse has no DELIMITER, no PL/SQL, no GO — generic splitter handles
            // comments (-- and /* */), single-quoted strings, FORMAT, and SETTINGS clauses.
            return genericSplitter.split(sql);
        }
        return genericSplitter.split(sql);
    }
}
