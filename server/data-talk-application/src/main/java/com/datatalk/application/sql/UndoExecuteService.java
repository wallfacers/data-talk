package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.domain.undo.UndoLogEntry;
import com.datatalk.domain.undo.UndoResult;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class UndoExecuteService {

    private final UndoLogRepository undoLogRepo;
    private final ConnectionService connSvc;

    public UndoExecuteService(UndoLogRepository undoLogRepo, ConnectionService connSvc) {
        this.undoLogRepo = undoLogRepo;
        this.connSvc = connSvc;
    }

    public UndoResult execute(String undoLogId, boolean confirmed) {
        UndoLogEntry entry = undoLogRepo.findById(undoLogId).orElse(null);
        if (entry == null) {
            return new UndoResult.NotFound();
        }
        if ("undone".equals(entry.status())) {
            return new UndoResult.AlreadyUndone();
        }
        if ("expired".equals(entry.status()) || entry.expiresAt() < System.currentTimeMillis()) {
            return new UndoResult.Expired();
        }
        if (!entry.undoable() || entry.inverseSql() == null) {
            return new UndoResult.NotFound();
        }
        if (!confirmed) {
            return new UndoResult.RequiresConfirmation(
                entry.inverseSql(), entry.affectedRows(), entry.tableName()
            );
        }

        try {
            int affectedRows = executeInverseSql(entry);
            undoLogRepo.markUndone(undoLogId, System.currentTimeMillis());
            return new UndoResult.Undone(affectedRows);
        } catch (SQLException e) {
            throw new RuntimeException("Undo execution failed: " + e.getMessage(), e);
        }
    }

    private int executeInverseSql(UndoLogEntry entry) throws SQLException {
        String url = JdbcUrlBuilder.build(entry.connectionId() != null
            ? buildConnectionRecord(entry) : null);
        try (Connection c = DriverManager.getConnection(
                 JdbcUrlBuilder.build(withDatabase(buildConnectionRecord(entry), entry.databaseName())),
                 "sa", "")) {
            c.setAutoCommit(false);
            try (Statement stmt = c.createStatement()) {
                stmt.setQueryTimeout(30);
                int total = 0;
                for (String sql : entry.inverseSql().split(";\\s*\n")) {
                    if (!sql.isBlank()) {
                        boolean hasRs = stmt.execute(sql);
                        if (!hasRs) {
                            int uc = stmt.getUpdateCount();
                            if (uc > 0) total += uc;
                        }
                    }
                }
                c.commit();
                return total > 0 ? total : entry.affectedRows();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private ConnectionRecord buildConnectionRecord(UndoLogEntry entry) {
        throw new UnsupportedOperationException(
            "Connection lookup by connectionId requires ConnectionRepository — use the overloaded method");
    }

    public UndoResult execute(String undoLogId, boolean confirmed, ConnectionRecord connection, String decryptedPassword) {
        UndoLogEntry entry = undoLogRepo.findById(undoLogId).orElse(null);
        if (entry == null) {
            return new UndoResult.NotFound();
        }
        if ("undone".equals(entry.status())) {
            return new UndoResult.AlreadyUndone();
        }
        if ("expired".equals(entry.status()) || entry.expiresAt() < System.currentTimeMillis()) {
            return new UndoResult.Expired();
        }
        if (!entry.undoable() || entry.inverseSql() == null) {
            return new UndoResult.NotFound();
        }
        if (!confirmed) {
            return new UndoResult.RequiresConfirmation(
                entry.inverseSql(), entry.affectedRows(), entry.tableName()
            );
        }

        try {
            int affectedRows = executeInverseOnConnection(entry, connection, decryptedPassword);
            undoLogRepo.markUndone(undoLogId, System.currentTimeMillis());
            return new UndoResult.Undone(affectedRows);
        } catch (SQLException e) {
            throw new RuntimeException("Undo execution failed: " + e.getMessage(), e);
        }
    }

    private int executeInverseOnConnection(UndoLogEntry entry, ConnectionRecord cr, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                 JdbcUrlBuilder.build(withDatabase(cr, entry.databaseName())),
                 cr.username(), password)) {
            c.setAutoCommit(false);
            try (Statement stmt = c.createStatement()) {
                stmt.setQueryTimeout(30);
                int total = 0;
                for (String sql : entry.inverseSql().split(";\\s*\n")) {
                    if (!sql.isBlank()) {
                        boolean hasRs = stmt.execute(sql);
                        if (!hasRs) {
                            int uc = stmt.getUpdateCount();
                            if (uc > 0) total += uc;
                        }
                    }
                }
                c.commit();
                return total > 0 ? total : entry.affectedRows();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private ConnectionRecord withDatabase(ConnectionRecord connection, String database) {
        return new ConnectionRecord(
            connection.id(), connection.name(), connection.kind(),
            connection.host(), connection.port(), database,
            connection.username(), connection.passwordEnc(),
            connection.schemaDigest(), connection.createdAt(),
            connection.connectTimeout(), connection.lastTestStatus(),
            connection.lastTestAt(), connection.oracleServiceType(),
            connection.sqlserverEncrypt(), connection.sqlserverTrustServerCertificate(),
            connection.sqlserverInstanceName(), connection.readOnly(),
            connection.compatibilityMode(), connection.oceanbaseTenant(),
            connection.oceanbaseCluster()
        );
    }
}
