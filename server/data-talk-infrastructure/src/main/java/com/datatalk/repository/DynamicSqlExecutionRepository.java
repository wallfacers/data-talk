package com.datatalk.repository;

import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.exception.SqlExecutionException;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class DynamicSqlExecutionRepository implements SqlExecutionRepository {

    @Override
    public QueryResult execute(DbConnection connection, String sql) {
        long start = System.currentTimeMillis();

        HikariDataSource ds = createDataSource(connection);
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i).toLowerCase());
            }

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }

            return new QueryResult(columns, rows, System.currentTimeMillis() - start);
        } catch (SQLException e) {
            throw new SqlExecutionException("Failed to execute SQL: " + e.getMessage(), e);
        } finally {
            ds.close();
        }
    }

    private HikariDataSource createDataSource(DbConnection connection) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(5000);

        switch (connection.dbType()) {
            case MYSQL -> {
                config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                        connection.host(), connection.port(), connection.databaseName()));
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
            case POSTGRESQL -> {
                config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
                        connection.host(), connection.port(), connection.databaseName()));
                config.setDriverClassName("org.postgresql.Driver");
            }
            case SQLITE -> {
                config.setJdbcUrl("jdbc:sqlite:" + connection.databaseName());
                config.setDriverClassName("org.sqlite.JDBC");
            }
            case H2 -> {
                config.setJdbcUrl(String.format("jdbc:h2:%s", connection.databaseName()));
                config.setDriverClassName("org.h2.Driver");
            }
            default -> throw new IllegalArgumentException("Unsupported database type: " + connection.dbType());
        }

        if (connection.username() != null) {
            config.setUsername(connection.username());
        }
        config.setPassword("");

        return new HikariDataSource(config);
    }
}
