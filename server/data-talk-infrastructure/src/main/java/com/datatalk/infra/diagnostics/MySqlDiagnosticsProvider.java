package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MySqlDiagnosticsProvider implements DiagnosticsProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("mysql");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        ConnectionRecord effectiveConn = withDatabaseOverride(conn, database);
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(effectiveConn), effectiveConn.username(), decryptedPassword);
             var stmt = connection.createStatement();
             var rs = stmt.executeQuery("EXPLAIN FORMAT=JSON " + sql)) {

            if (!rs.next()) {
                return DiagnosticResult.ok(new ExplainPlan("mysql", "", List.of(), null, List.of()));
            }
            String raw = rs.getString(1);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode queryBlock = root.path("query_block");

            List<ExplainNode> nodes = parseQueryBlock(queryBlock);
            List<String> warnings = collectWarnings(nodes);

            return DiagnosticResult.ok(new ExplainPlan("mysql", raw, nodes, null, warnings));
        } catch (Exception e) {
            return DiagnosticResult.error("EXPLAIN_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                   ConnectionRecord conn, String decryptedPassword) {
        if (plan == null || plan.nodes() == null) {
            return DiagnosticResult.ok(List.of());
        }
        List<IndexRecommendation> recs = collectRecommendations(plan.nodes(), sql);
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported("MySQL lock info is not yet supported");
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported("MySQL connection pool info is not yet supported");
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported("MySQL table space info is not yet supported");
    }

    // -- internal parsing --

    ConnectionRecord withDatabaseOverride(ConnectionRecord conn, String database) {
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

    List<ExplainNode> parseQueryBlock(JsonNode queryBlock) {
        List<ExplainNode> nodes = new ArrayList<>();
        // Direct table block (no nested_loop)
        if (queryBlock.has("table")) {
            nodes.add(parseTableNode(queryBlock.path("table")));
        }
        // nested_loop contains a list of join operands
        JsonNode nl = queryBlock.path("nested_loop");
        if (nl.isArray()) {
            for (JsonNode item : nl) {
                nodes.addAll(parseQueryBlock(item));
            }
        }
        // order_by / group_by sub-blocks
        if (queryBlock.has("ordering_operation")) {
            nodes.addAll(parseQueryBlock(queryBlock.path("ordering_operation")));
        }
        if (queryBlock.has("grouping_operation")) {
            nodes.addAll(parseQueryBlock(queryBlock.path("grouping_operation")));
        }
        return nodes;
    }

    private ExplainNode parseTableNode(JsonNode table) {
        String tableName = table.path("table_name").asText("");
        String accessType = table.path("access_type").asText("");
        long rows = table.path("rows_examined_per_scan").asLong(table.path("rows").asLong(0));
        Double cost = table.has("filtered") ? table.path("filtered").asDouble() : null;
        String key = table.path("key").asText(null);
        String extra = key != null ? "key=" + key : null;

        return new ExplainNode(
            accessType,
            tableName,
            mapAccessType(accessType),
            rows,
            cost,
            extra,
            List.of()
        );
    }

    ScanType mapAccessType(String accessType) {
        if (accessType == null) return ScanType.OTHER;
        return switch (accessType.toLowerCase()) {
            case "all" -> ScanType.FULL_SCAN;
            case "range" -> ScanType.INDEX_RANGE;
            case "ref" -> ScanType.REF;
            case "eq_ref" -> ScanType.REF;
            case "index" -> ScanType.INDEX_SCAN;
            case "const", "system" -> ScanType.CONST;
            default -> ScanType.OTHER;
        };
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                warnings.add("Full table scan on " + node.table());
            }
            warnings.addAll(collectWarnings(node.children()));
        }
        return warnings;
    }

    private List<IndexRecommendation> collectRecommendations(List<ExplainNode> nodes, String sql) {
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                List<String> cols = SqlColumnExtractor.extract(sql, node.table());
                if (!cols.isEmpty()) {
                    Impact impact = node.rows() > 1000 ? Impact.HIGH : Impact.MEDIUM;
                    recs.add(new IndexRecommendation(
                        node.table(),
                        cols,
                        "BTREE",
                        impact,
                        "Full table scan on " + node.table() + " (" + node.rows() + " rows)"
                    ));
                }
            }
            recs.addAll(collectRecommendations(node.children(), sql));
        }
        return recs;
    }
}
