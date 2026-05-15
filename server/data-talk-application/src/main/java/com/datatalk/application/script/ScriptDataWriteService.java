package com.datatalk.application.script;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScriptDataWriteService {

    private static final Logger log = LoggerFactory.getLogger(ScriptDataWriteService.class);

    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;

    public ScriptDataWriteService(ConnectionRepository connRepo, ConnectionService connSvc) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
    }

    public record WriteResult(int rowsInserted, String tableName, List<String> columnsCreated) {}

    public WriteResult write(String connectionId, String tableName, List<Map<String, Object>> rows,
                              boolean createTable) {
        if (rows == null || rows.isEmpty()) {
            return new WriteResult(0, tableName, List.of());
        }

        List<String> columns = inferColumns(rows);
        List<String> columnsCreated = List.of();

        try (Connection c = openConnection(connectionId)) {
            if (createTable && !tableExists(c, tableName)) {
                String ddl = buildCreateTableSql(tableName, columns, rows.get(0));
                c.createStatement().execute(ddl);
                columnsCreated = columns;
                log.info("Created table {} with columns {}", tableName, columns);
            }

            String insertSql = buildInsertSql(tableName, columns);
            try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < columns.size(); i++) {
                        ps.setObject(i + 1, row.get(columns.get(i)));
                    }
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                int total = Arrays.stream(counts).sum();
                log.info("Inserted {} rows into {}", total, tableName);
                return new WriteResult(total, tableName, columnsCreated);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write data to " + tableName + ": " + e.getMessage(), e);
        }
    }

    private Connection openConnection(String connectionId) throws Exception {
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));
        String password = connSvc.decryptPassword(connectionId);
        String url = JdbcUrlBuilder.build(cr);

        String effectiveUsername = ConnectionKind.OCEANBASE.equals(cr.kind())
            ? ConnectionService.composeOceanBaseUsername(cr)
            : cr.username();

        return DriverManager.getConnection(url, effectiveUsername, password);
    }

    private List<String> inferColumns(List<Map<String, Object>> rows) {
        Set<String> cols = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            cols.addAll(row.keySet());
        }
        return new ArrayList<>(cols);
    }

    private boolean tableExists(Connection c, String tableName) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String buildCreateTableSql(String tableName, List<String> columns, Map<String, Object> sampleRow) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = columns.get(i);
            Object val = sampleRow.get(col);
            String sqlType = inferSqlType(val);
            sb.append(quoteIdentifier(col)).append(" ").append(sqlType);
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quoteIdentifier(columns.get(i)));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String quoteIdentifier(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
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
