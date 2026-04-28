package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlDiagnosticsProvider implements DiagnosticsProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Translator translator;

    public PostgreSqlDiagnosticsProvider(Translator translator) {
        this.translator = translator;
    }

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
        ConnectionRecord effectiveConn = withDatabaseOverride(conn, database);
        try (var connection = DriverManager.getConnection(JdbcUrlBuilder.build(effectiveConn), effectiveConn.username(), decryptedPassword);
             var stmt = connection.createStatement();
             ) {
            applySchema(connection, schema);
            try (var rs = stmt.executeQuery("EXPLAIN (FORMAT JSON, ANALYZE false) " + sql)) {

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
            }
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
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "PostgreSQL"));
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "PostgreSQL"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "PostgreSQL"));
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

    void applySchema(Connection connection, String schema) throws Exception {
        if (schema == null || schema.isBlank()) {
            return;
        }
        try (var stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + schema);
        }
    }

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
                warnings.add(translator.get("diagnostics.warning.sequential_scan", node.table()));
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
                        translator.get("diagnostics.recommendation.sequential_scan", node.table(), String.valueOf(node.rows()))
                    ));
                }
            }
            recs.addAll(collectRecommendations(node.children(), sql));
        }
        return recs;
    }
}
