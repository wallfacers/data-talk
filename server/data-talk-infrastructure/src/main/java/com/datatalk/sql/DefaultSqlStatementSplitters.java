package com.datatalk.sql;

import com.datatalk.application.sql.SqlStatementSplitters;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultSqlStatementSplitters implements SqlStatementSplitters {

    private final PostgresJdbcSqlStatementSplitter postgresSplitter;
    private final GenericSqlStatementSplitter genericSplitter;

    public DefaultSqlStatementSplitters(
        PostgresJdbcSqlStatementSplitter postgresSplitter,
        GenericSqlStatementSplitter genericSplitter
    ) {
        this.postgresSplitter = postgresSplitter;
        this.genericSplitter = genericSplitter;
    }

    @Override
    public List<String> split(String connectionKind, String sql) {
        if ("postgres".equalsIgnoreCase(connectionKind) || "postgresql".equalsIgnoreCase(connectionKind)) {
            return postgresSplitter.split(sql);
        }
        return genericSplitter.split(sql);
    }
}
