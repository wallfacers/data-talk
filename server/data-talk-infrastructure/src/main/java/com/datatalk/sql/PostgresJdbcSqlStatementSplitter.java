package com.datatalk.sql;

import org.postgresql.core.Parser;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component
public class PostgresJdbcSqlStatementSplitter {

    public List<String> split(String sql) {
        try {
            return Parser.parseJdbcSql(sql, true, false, true, false, false).stream()
                .map(nativeQuery -> nativeQuery.nativeSql.trim())
                .filter(statement -> !statement.isEmpty())
                .toList();
        } catch (SQLException e) {
            throw new IllegalArgumentException("failed to split PostgreSQL SQL script: " + e.getMessage(), e);
        }
    }
}
