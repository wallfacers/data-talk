package com.datatalk.application.script;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.dialect.IdentifierQuoter;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScriptDataWriteService {

    private static final Logger log = LoggerFactory.getLogger(ScriptDataWriteService.class);
    private static final int BATCH_SIZE = 1000;

    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;

    public ScriptDataWriteService(ConnectionRepository connRepo, ConnectionService connSvc) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
    }

    public record WriteResult(int rowsInserted, String tableName, List<String> columnsCreated) {}

    public record ColumnInfo(String name, String ddlType) {}

    public record StreamWriteResult(int rowsInserted, String tableName, List<ColumnInfo> columns) {}

    // ── batch write (List<Map>) ──────────────────────────────────────

    public WriteResult write(String connectionId, String tableName, List<Map<String, Object>> rows,
                              boolean createTable) {
        return write(connectionId, tableName, rows, createTable, null);
    }

    public WriteResult write(String connectionId, String tableName, List<Map<String, Object>> rows,
                              boolean createTable, Map<String, String> columnTypes) {
        if (rows == null || rows.isEmpty()) {
            return new WriteResult(0, tableName, List.of());
        }

        List<String> columns = inferColumns(rows);
        List<String> columnsCreated = List.of();
        String kind = resolveKind(connectionId);

        try (Connection c = openConnection(connectionId)) {
            c.setAutoCommit(false);
            if (createTable && !tableExists(c, tableName)) {
                String ddl = buildCreateTableSql(tableName, columns, rows.get(0), columnTypes, kind);
                log.info("DDL: {}", ddl);
                c.createStatement().execute(ddl);
                columnsCreated = columns;
                log.info("Created table {} with columns {}", tableName, columns);
            }

            String insertSql = buildInsertSql(tableName, columns, kind);
            try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < columns.size(); i++) {
                        ps.setObject(i + 1, row.get(columns.get(i)));
                    }
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                int total = Arrays.stream(counts).sum();
                c.commit();
                log.info("Inserted {} rows into {}", total, tableName);
                return new WriteResult(total, tableName, columnsCreated);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write data to " + tableName + ": " + e.getMessage(), e);
        }
    }

    // ── stream write (ResultSet cursor) ──────────────────────────────

    public StreamWriteResult writeStream(String connectionId, String tableName, ResultSet rs,
                                          boolean createTable, Map<String, String> columnTypes) {
        return writeStream(connectionId, null, tableName, rs, createTable, columnTypes);
    }

    /**
     * Variant accepting an explicit {@code database} override, plumbed via a derived
     * {@link ConnectionRecord} so the JDBC URL embeds that database segment — BUG-0072.
     */
    public StreamWriteResult writeStream(String connectionId, String database, String tableName,
                                          ResultSet rs, boolean createTable,
                                          Map<String, String> columnTypes) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> colNames = new ArrayList<>();
            List<ColumnInfo> columns = new ArrayList<>();

            for (int i = 1; i <= colCount; i++) {
                String colName = meta.getColumnLabel(i);
                colNames.add(colName);
                String ddlType = ColumnTypeMapper.mapWithOverride(
                    meta.getColumnType(i), meta.getPrecision(i), meta.getScale(i),
                    colName, columnTypes);
                columns.add(new ColumnInfo(colName, ddlType));
            }

            String kind = resolveKind(connectionId);
            try (Connection c = openResolvedConnection(connectionId, database)) {
                c.setAutoCommit(false);

                if (createTable && !tableExists(c, tableName)) {
                    String ddl = buildCreateTableFromColumns(tableName, columns, kind);
                    c.createStatement().execute(ddl);
                    log.info("Created table {} with columns {}", tableName, columns);
                }

                String insertSql = buildInsertSql(tableName, colNames, kind);
                int totalRows = streamFromCursor(c, rs, colCount, insertSql);
                log.info("Streamed {} rows into {}", totalRows, tableName);
                return new StreamWriteResult(totalRows, tableName, columns);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream data to " + tableName + ": " + e.getMessage(), e);
        }
    }

    private int streamFromCursor(Connection c, ResultSet rs, int colCount,
                                  String insertSql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(insertSql)) {
            int batchCount = 0;
            int totalRows = 0;
            while (rs.next()) {
                for (int i = 0; i < colCount; i++) {
                    ps.setObject(i + 1, rs.getObject(i + 1));
                }
                ps.addBatch();
                batchCount++;
                if (batchCount >= BATCH_SIZE) {
                    int[] counts = ps.executeBatch();
                    totalRows += Arrays.stream(counts).sum();
                    c.commit();
                    ps.clearBatch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0) {
                int[] counts = ps.executeBatch();
                totalRows += Arrays.stream(counts).sum();
                c.commit();
            }
            return totalRows;
        }
    }

    // ── connection ────────────────────────────────────────────────────

    public Connection openConnection(String connectionId) throws Exception {
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));
        return openConnection(cr);
    }

    /**
     * Opens a JDBC connection for the supplied {@link ConnectionRecord} as-is, allowing callers
     * to pass a derived record (e.g. {@code withDatabase(...)} from session_data_context) so the
     * URL builder receives the resolved database — see BUG-0072.
     */
    public Connection openConnection(ConnectionRecord cr) throws Exception {
        String password = connSvc.decryptPassword(cr.id());
        String url = JdbcUrlBuilder.build(cr);

        String effectiveUsername = ConnectionKind.OCEANBASE.equals(cr.kind())
            ? ConnectionService.composeOceanBaseUsername(cr)
            : cr.username();

        return DriverManager.getConnection(url, effectiveUsername, password);
    }

    /**
     * Opens a JDBC connection against {@code connectionId}. When {@code database} is non-blank,
     * the underlying {@link ConnectionRecord} is derived via {@code withDatabase} so the URL
     * builder embeds that database segment — see BUG-0072. Used by import / cross-DB / stream
     * paths that resolve the active database from {@code session_data_context}.
     */
    public Connection openResolvedConnection(String connectionId, String database) throws Exception {
        if (database == null || database.isBlank()) {
            return openConnection(connectionId);
        }
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));
        return openConnection(withDatabase(cr, database));
    }

    private static ConnectionRecord withDatabase(ConnectionRecord cr, String database) {
        return new ConnectionRecord(
            cr.id(), cr.name(), cr.kind(), cr.host(), cr.port(),
            database,
            cr.username(), cr.passwordEnc(), cr.schemaDigest(), cr.createdAt(),
            cr.connectTimeout(), cr.lastTestStatus(), cr.lastTestAt(),
            cr.oracleServiceType(), cr.sqlserverEncrypt(),
            cr.sqlserverTrustServerCertificate(), cr.sqlserverInstanceName(),
            cr.readOnly(), cr.compatibilityMode(),
            cr.oceanbaseTenant(), cr.oceanbaseCluster()
        );
    }

    /**
     * Resolves the connection kind for the given connection ID. Used by data-movement
     * services (import, export) to dispatch dialect-aware identifier quoting via
     * {@link IdentifierQuoter}. Returns the raw kind string from {@link ConnectionRecord}.
     */
    public String resolveKind(String connectionId) {
        return connRepo.findById(connectionId)
            .map(ConnectionRecord::kind)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));
    }

    // ── DDL helpers ──────────────────────────────────────────────────

    private boolean tableExists(Connection c, String tableName) throws Exception {
        String schema = c.getSchema();
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String buildCreateTableSql(String tableName, List<String> columns,
                                        Map<String, Object> sampleRow, Map<String, String> columnTypes,
                                        String connectionKind) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ")
            .append(IdentifierQuoter.quote(tableName, connectionKind)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = columns.get(i);
            String sqlType = (columnTypes != null && columnTypes.containsKey(col))
                ? columnTypes.get(col)
                : inferSqlType(sampleRow.get(col));
            sb.append(IdentifierQuoter.quote(col, connectionKind)).append(" ").append(sqlType);
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildCreateTableFromColumns(String tableName, List<ColumnInfo> columns, String connectionKind) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ")
            .append(IdentifierQuoter.quote(tableName, connectionKind)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(IdentifierQuoter.quote(columns.get(i).name(), connectionKind))
              .append(" ").append(columns.get(i).ddlType());
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildInsertSql(String tableName, List<String> columns, String connectionKind) {
        StringBuilder sb = new StringBuilder("INSERT INTO ")
            .append(IdentifierQuoter.quote(tableName, connectionKind)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(IdentifierQuoter.quote(columns.get(i), connectionKind));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private List<String> inferColumns(List<Map<String, Object>> rows) {
        Set<String> cols = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            cols.addAll(row.keySet());
        }
        return new ArrayList<>(cols);
    }

    private static String inferSqlType(Object value) {
        if (value == null) return "TEXT";
        if (value instanceof Integer || value instanceof Long) return "BIGINT";
        if (value instanceof Double || value instanceof Float) return "DOUBLE";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.time.temporal.Temporal) return "TIMESTAMP";
        return "TEXT";
    }
}
