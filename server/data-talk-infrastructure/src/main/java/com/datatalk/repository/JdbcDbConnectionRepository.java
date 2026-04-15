package com.datatalk.repository;

import com.datatalk.entity.DbConnection;
import com.datatalk.repository.DbConnectionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDbConnectionRepository implements DbConnectionRepository {

    private final JdbcTemplate sqliteJdbcTemplate;

    public JdbcDbConnectionRepository(
            @Qualifier("sqliteJdbcTemplate") JdbcTemplate sqliteJdbcTemplate) {
        this.sqliteJdbcTemplate = sqliteJdbcTemplate;
    }

    @Override
    public void save(DbConnection connection) {
        sqliteJdbcTemplate.update(
                "INSERT OR REPLACE INTO db_connections (id, name, db_type, host, port, database_name, username) VALUES (?, ?, ?, ?, ?, ?, ?)",
                connection.id(), connection.name(), connection.dbType().name(),
                connection.host(), connection.port(), connection.databaseName(), connection.username());
    }

    @Override
    public Optional<DbConnection> findById(String id) {
        try {
            DbConnection conn = sqliteJdbcTemplate.queryForObject(
                    "SELECT * FROM db_connections WHERE id = ?",
                    this::mapRow, id);
            return Optional.ofNullable(conn);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<DbConnection> findAll() {
        return sqliteJdbcTemplate.query("SELECT * FROM db_connections ORDER BY created_at DESC", this::mapRow);
    }

    @Override
    public void deleteById(String id) {
        sqliteJdbcTemplate.update("DELETE FROM db_connections WHERE id = ?", id);
    }

    private DbConnection mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DbConnection(
                rs.getString("id"),
                rs.getString("name"),
                com.datatalk.entity.DbType.valueOf(rs.getString("db_type")),
                rs.getString("host"),
                rs.getObject("port", Integer.class),
                rs.getString("database_name"),
                rs.getString("username"),
                rs.getTimestamp("created_at").toInstant());
    }
}
