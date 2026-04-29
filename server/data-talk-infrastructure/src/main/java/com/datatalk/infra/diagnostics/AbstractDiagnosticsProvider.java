package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractDiagnosticsProvider implements DiagnosticsProvider {

    protected final Translator translator;

    protected AbstractDiagnosticsProvider(Translator translator) {
        this.translator = translator;
    }

    protected Connection openConnection(ConnectionRecord conn, String decryptedPassword) throws SQLException {
        ConnectionRecord effective = withDatabaseOverride(conn, conn.databaseName());
        return DriverManager.getConnection(JdbcUrlBuilder.build(effective), effective.username(), decryptedPassword);
    }

    protected List<Map<String, Object>> queryForList(
        ConnectionRecord conn,
        String decryptedPassword,
        String sql,
        Object... params
    ) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }

    protected int executeUpdate(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    protected void executeStatementAutoCommit(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             Statement s = c.createStatement()) {
            c.setAutoCommit(true);
            s.execute(sql);
        }
    }

    protected ConnectionRecord withDatabaseOverride(ConnectionRecord conn, String database) {
        if (database == null || database.isBlank()) {
            return conn;
        }
        return new ConnectionRecord(
            conn.id(),
            conn.name(),
            conn.kind(),
            conn.host(),
            conn.port(),
            database,
            conn.username(),
            conn.passwordEnc(),
            conn.schemaDigest(),
            conn.createdAt(),
            conn.connectTimeout(),
            conn.lastTestStatus(),
            conn.lastTestAt()
        );
    }

    private static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
