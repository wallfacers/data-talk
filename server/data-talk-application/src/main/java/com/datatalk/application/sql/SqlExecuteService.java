package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SqlExecuteService {

    public record Result(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        long executionMs,
        boolean truncated
    ) {}

    public record RiskBlocked(String riskLevel, String riskReason) {}

    public static class SqlRiskBlockedException extends RuntimeException {
        private final RiskBlocked risk;
        public SqlRiskBlockedException(RiskBlocked risk) {
            super("SQL risk blocked: " + risk.riskLevel());
            this.risk = risk;
        }
        public RiskBlocked risk() { return risk; }
    }

    private final SqlRiskAnalyzer riskAnalyzer;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final int maxRows;

    public SqlExecuteService(SqlRiskAnalyzer riskAnalyzer,
                             ConnectionRepository connRepo,
                             ConnectionService connSvc,
                             @Value("${datatalk.sql.max-rows:5000}") int maxRows) {
        this.riskAnalyzer = riskAnalyzer;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.maxRows = maxRows;
    }

    public Result execute(String connectionId, String sql, String source) {
        if (connectionId == null || connectionId.isBlank())
            throw new IllegalArgumentException("connectionId required");
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException("sql required");

        if ("user".equals(source)) {
            SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
            if (risk.riskLevel() == RiskLevel.L3) {
                throw new SqlRiskBlockedException(new RiskBlocked("HIGH", risk.reason()));
            }
        }

        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new NoSuchElementException("unknown connection: " + connectionId));

        long started = System.currentTimeMillis();
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;

        try (Connection c = DriverManager.getConnection(
                 JdbcUrlBuilder.build(cr), cr.username(), connSvc.decryptPassword(connectionId));
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                for (int i = 1; i <= colCount; i++) columns.add(md.getColumnLabel(i));
                while (rs.next()) {
                    if (rows.size() >= maxRows) { truncated = true; break; }
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }

        return new Result(columns, rows, rows.size(), System.currentTimeMillis() - started, truncated);
    }
}
