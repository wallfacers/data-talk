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
public class PostgreSqlDiagnosticsProvider implements DiagnosticsProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("postgresql", "postgres");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(conn), conn.username(), decryptedPassword);
             var stmt = connection.createStatement();
             var rs = stmt.executeQuery("EXPLAIN (FORMAT JSON, ANALYZE false) " + sql)) {

            if (!rs.next()) {
                return DiagnosticResult.ok(new ExplainPlan("postgresql", "", List.of(), null, List.of()));
            }
            String raw = rs.getString(1);
            JsonNode root = objectMapper.readTree(raw);
            // PostgreSQL returns an array; take first element
            JsonNode planRoot = root.isArray() ? root.get(0) : root;
            JsonNode planNode = planRoot.path("Plan");

            ExplainNode topNode = parsePlanNode(planNode);
            List<ExplainNode> nodes = topNode != null ? List.of(topNode) : List.of();
            List<String> warnings = collectWarnings(nodes);

            Double totalCost = planNode.has("Total Cost") ? planNode.get("Total Cost").asDouble() : null;

            return DiagnosticResult.ok(new ExplainPlan("postgresql", raw, nodes, totalCost, warnings));
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
        List<IndexRecommendation> recs = collectRecommendations(plan.nodes());
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported("PostgreSQL lock info is not yet supported");
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported("PostgreSQL connection pool info is not yet supported");
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported("PostgreSQL table space info is not yet supported");
    }

    // -- internal parsing --

    private ExplainNode parsePlanNode(JsonNode plan) {
        if (plan == null || plan.isMissingNode() || plan.isNull()) {
            return null;
        }

        String nodeType = plan.path("Node Type").asText("");
        String relationName = plan.path("Relation Name").asText(plan.path("Alias").asText(""));
        long rows = plan.path("Plan Rows").asLong(0);
        Double cost = plan.has("Total Cost") ? plan.get("Total Cost").asDouble() : null;
        String indexName = plan.path("Index Name").asText(null);
        String extra = indexName != null ? "index=" + indexName : null;

        List<ExplainNode> children = new ArrayList<>();
        JsonNode plans = plan.path("Plans");
        if (plans.isArray()) {
            for (JsonNode child : plans) {
                ExplainNode childNode = parsePlanNode(child);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
        }

        return new ExplainNode(
            nodeType,
            relationName,
            mapNodeType(nodeType),
            rows,
            cost,
            extra,
            children
        );
    }

    ScanType mapNodeType(String nodeType) {
        if (nodeType == null) return ScanType.OTHER;
        return switch (nodeType) {
            case "Seq Scan" -> ScanType.FULL_SCAN;
            case "Index Scan", "Index Only Scan" -> ScanType.INDEX_SCAN;
            case "Bitmap Index Scan", "Bitmap Heap Scan" -> ScanType.INDEX_RANGE;
            default -> ScanType.OTHER;
        };
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                warnings.add("Sequential scan on " + node.table());
            }
            warnings.addAll(collectWarnings(node.children()));
        }
        return warnings;
    }

    private List<IndexRecommendation> collectRecommendations(List<ExplainNode> nodes) {
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode node : nodes) {
            if (node.scanType() == ScanType.FULL_SCAN) {
                Impact impact = node.rows() > 1000 ? Impact.HIGH : Impact.MEDIUM;
                recs.add(new IndexRecommendation(
                    node.table(),
                    List.of(),
                    "BTREE",
                    impact,
                    "Sequential scan on " + node.table() + " (" + node.rows() + " rows)"
                ));
            }
            recs.addAll(collectRecommendations(node.children()));
        }
        return recs;
    }
}
