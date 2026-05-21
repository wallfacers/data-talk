package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConnectionRepository {

    private final JdbcTemplate jdbc;
    public ConnectionRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<ConnectionRecord> MAPPER = (rs, i) -> new ConnectionRecord(
        rs.getString("id"), rs.getString("name"), rs.getString("kind"), rs.getString("host"),
        rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
        rs.getBytes("password_enc"), rs.getString("schema_digest"), rs.getLong("created_at"),
        rs.getInt("connect_timeout"),
        rs.getString("last_test_status"),
        rs.getObject("last_test_at") instanceof Number n ? n.longValue() : null,
        rs.getString("oracle_service_type"),
        rs.getInt("sqlserver_encrypt"),
        rs.getBoolean("sqlserver_trust_server_certificate"),
        rs.getString("sqlserver_instance_name"),
        rs.getBoolean("read_only"),
        rs.getString("compatibility_mode"),
        rs.getString("oceanbase_tenant"),
        rs.getString("oceanbase_cluster")
    );

    public void insert(ConnectionRecord c) {
        jdbc.update("""
            INSERT INTO connections(id, name, kind, host, port, database_name, username, password_enc, schema_digest, created_at, connect_timeout, oracle_service_type, sqlserver_encrypt, sqlserver_trust_server_certificate, sqlserver_instance_name, read_only, compatibility_mode, oceanbase_tenant, oceanbase_cluster)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.name(), c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
            c.passwordEnc(), c.schemaDigest(), c.createdAt(), c.connectTimeout(), c.oracleServiceType(),
            c.sqlserverEncrypt(), c.sqlserverTrustServerCertificate(), c.sqlserverInstanceName(), c.readOnly(),
            c.compatibilityMode(), c.oceanbaseTenant(), c.oceanbaseCluster());
    }

    public List<ConnectionRecord> findAll() {
        return jdbc.query("SELECT * FROM connections ORDER BY created_at DESC", MAPPER);
    }

    public Optional<ConnectionRecord> findById(String id) {
        var list = jdbc.query("SELECT * FROM connections WHERE id = ?", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void update(ConnectionRecord c) {
        int n = jdbc.update("""
            UPDATE connections
               SET name = ?, kind = ?, host = ?, port = ?, database_name = ?, username = ?,
                   password_enc = ?, schema_digest = ?, connect_timeout = ?, oracle_service_type = ?,
                   sqlserver_encrypt = ?, sqlserver_trust_server_certificate = ?, sqlserver_instance_name = ?,
                   read_only = ?, compatibility_mode = ?, oceanbase_tenant = ?, oceanbase_cluster = ?
             WHERE id = ?
            """, c.name(), c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
            c.passwordEnc(), c.schemaDigest(), c.connectTimeout(), c.oracleServiceType(),
            c.sqlserverEncrypt(), c.sqlserverTrustServerCertificate(), c.sqlserverInstanceName(),
            c.readOnly(), c.compatibilityMode(), c.oceanbaseTenant(), c.oceanbaseCluster(), c.id());
        if (n == 0) throw new java.util.NoSuchElementException("unknown connection: " + c.id());
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM connections WHERE id = ?", id) > 0;
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM connections");
    }

    public void updateTestStatus(String id, String status, long timestamp) {
        jdbc.update("""
            UPDATE connections SET last_test_status = ?, last_test_at = ? WHERE id = ?
            """, status, timestamp, id);
    }
}
