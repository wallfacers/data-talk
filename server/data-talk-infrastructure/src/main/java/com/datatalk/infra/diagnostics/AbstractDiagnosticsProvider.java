package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.DiagnosticResult;
import com.datatalk.domain.diagnostics.ExplainNode;
import com.datatalk.domain.diagnostics.ScanType;
import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    protected void executeStatement(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    protected void executeStatementAutoCommit(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword)) {
            c.setAutoCommit(true);
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
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
            conn.lastTestAt(),
            conn.oracleServiceType(),
            conn.sqlserverEncrypt(),
            conn.sqlserverTrustServerCertificate(),
            conn.sqlserverInstanceName(),
            conn.readOnly()
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

    // --- Day-2 helpers for EXPLAIN plan parsing (tabular, text, XML) ---
    private static record NodeBuilder(
        int depth, String operator, String table, long rows, String info, List<NodeBuilder> children
    ) {}

    protected ScanType parseScanType(String dialectToken, Map<String, ScanType> overrides) {
        if (dialectToken == null || dialectToken.isBlank()) return ScanType.OTHER;
        String key = dialectToken.toLowerCase(Locale.ROOT);
        return overrides.getOrDefault(key, ScanType.OTHER);
    }

    protected List<ExplainNode> mapTabularPlanToNodes(List<Map<String, Object>> rows, TabularLayout layout) {
        if (rows == null || rows.isEmpty()) return List.of();
        Pattern opPattern = layout.operatorPattern() != null ? Pattern.compile(layout.operatorPattern()) : null;
        List<NodeBuilder> all = new ArrayList<>();
        for (var row : rows) {
            Object idObj = row.get(layout.idCol());
            if (idObj == null) continue;
            String idStr = String.valueOf(idObj);
            int depth = computeAsciiTreeDepth(idStr);
            String op = extractOperator(idStr, opPattern);
            String table = layout.objectCol() == null ? null : valueOrNull(row.get(layout.objectCol()));
            long rowsEst = layout.rowsCol() == null ? 0L : parseLongSafe(row.get(layout.rowsCol()));
            String info = layout.infoCol() == null ? null : valueOrNull(row.get(layout.infoCol()));
            all.add(new NodeBuilder(depth, op, table, rowsEst, info, new ArrayList<>()));
        }
        Deque<NodeBuilder> stack = new ArrayDeque<>();
        List<NodeBuilder> roots = new ArrayList<>();
        for (var nb : all) {
            while (!stack.isEmpty() && stack.peek().depth() >= nb.depth()) stack.pop();
            if (stack.isEmpty()) roots.add(nb);
            else stack.peek().children().add(nb);
            stack.push(nb);
        }
        return roots.stream().map(this::toTabularExplainNode).toList();
    }

    protected List<ExplainNode> mapTextPlanToNodes(String rawText, TextPlanGrammar grammar) {
        if (rawText == null || rawText.isBlank()) return List.of();
        List<NodeBuilder> all = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            if (line.isBlank()) continue;
            int depth = grammar.indentFn().apply(line);
            if (depth < 0) continue;
            String op = grammar.operatorFn().apply(line);
            if (op == null || op.isBlank()) continue;
            String table = grammar.tableFn().apply(line).orElse(null);
            Long rows = grammar.rowsFn().apply(line).orElse(null);
            all.add(new NodeBuilder(depth, op, table, rows == null ? 0L : rows, null, new ArrayList<>()));
        }
        Deque<NodeBuilder> stack = new ArrayDeque<>();
        List<NodeBuilder> roots = new ArrayList<>();
        for (var nb : all) {
            while (!stack.isEmpty() && stack.peek().depth() >= nb.depth()) stack.pop();
            if (stack.isEmpty()) roots.add(nb);
            else stack.peek().children().add(nb);
            stack.push(nb);
        }
        return roots.stream().map(this::toTabularExplainNode).toList();
    }

    protected List<ExplainNode> mapXmlPlanToNodes(String rawXml) {
        if (rawXml == null || rawXml.isBlank()) return List.of();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);
            var doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(rawXml.getBytes(StandardCharsets.UTF_8)));
            Element root = findFirstRelOp(doc.getDocumentElement());
            if (root == null) return List.of();
            return List.of(parseRelOp(root));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse SHOWPLAN XML: " + e.getMessage(), e);
        }
    }

    protected <T> DiagnosticResult<T> mapPermissionOrDriverError(SQLException e, String capability, String kind) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        int code = e.getErrorCode();

        boolean permission = switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "sqlserver" -> "42000".equals(state) && code == 262;
            case "tidb" -> "28000".equals(state) || msg.contains("Access denied");
            case "clickhouse" -> code == 497;
            case "apache_doris", "starrocks" -> msg.contains("Access denied for user");
            case "presto", "trino" -> msg.contains("Access Denied");
            case "hive" -> msg.contains("Permission denied") || msg.contains("HiveAccessControlException");
            default -> false;
        };

        if (permission) {
            String key = "diagnostics.explain.unsupported." + permissionKey(kind);
            return DiagnosticResult.unsupported(translator.get(key));
        }
        return DiagnosticResult.error(kind.toUpperCase(Locale.ROOT) + "_" + capability + "_ERROR", msg);
    }

    // --- MySQL JSON plan parser (moved from MySqlDiagnosticsProvider) ---
    protected List<ExplainNode> parseMySqlJsonPlan(JsonNode queryBlock) {
        List<ExplainNode> nodes = new ArrayList<>();
        if (queryBlock.has("table")) {
            nodes.add(parseMySqlTableNode(queryBlock.path("table")));
        }
        JsonNode nl = queryBlock.path("nested_loop");
        if (nl.isArray()) {
            for (JsonNode item : nl) nodes.addAll(parseMySqlJsonPlan(item));
        }
        if (queryBlock.has("ordering_operation")) nodes.addAll(parseMySqlJsonPlan(queryBlock.path("ordering_operation")));
        if (queryBlock.has("grouping_operation")) nodes.addAll(parseMySqlJsonPlan(queryBlock.path("grouping_operation")));
        return nodes;
    }

    private ExplainNode parseMySqlTableNode(JsonNode table) {
        String tableName = table.path("table_name").asText("");
        String accessType = table.path("access_type").asText("");
        long rows = table.path("rows_examined_per_scan").asLong(table.path("rows").asLong(0));
        Double cost = table.has("filtered") ? table.path("filtered").asDouble() : null;
        String key = table.path("key").asText(null);
        String extra = key != null ? "key=" + key : null;
        var overrides = Map.of(
            "all", ScanType.FULL_SCAN,
            "range", ScanType.INDEX_RANGE,
            "ref", ScanType.REF, "eq_ref", ScanType.REF,
            "index", ScanType.INDEX_SCAN,
            "const", ScanType.CONST, "system", ScanType.CONST
        );
        return new ExplainNode(accessType, tableName, parseScanType(accessType, overrides), rows, cost, extra, List.of());
    }

    // --- Private helpers for tabular/text/xml plan parsing ---
    private ExplainNode toTabularExplainNode(NodeBuilder nb) {
        return new ExplainNode(
            nb.operator(), nb.table(), ScanType.OTHER, nb.rows(),
            null, nb.info(),
            nb.children().stream().map(this::toTabularExplainNode).toList()
        );
    }

    private static int computeAsciiTreeDepth(String idStr) {
        int depth = 0;
        int i = 0;
        while (i < idStr.length()) {
            char c = idStr.charAt(i);
            if (c == ' ' || c == '│') { i++; depth++; }
            else if (c == '└' || c == '├') { i += 1; depth++; if (i < idStr.length() && idStr.charAt(i) == '─') i++; }
            else break;
        }
        return depth;
    }

    private static String extractOperator(String idStr, Pattern opPattern) {
        String trimmed = idStr;
        int i = 0;
        while (i < trimmed.length() &&
            (trimmed.charAt(i) == ' ' || trimmed.charAt(i) == '│' ||
             trimmed.charAt(i) == '└' || trimmed.charAt(i) == '├' || trimmed.charAt(i) == '─')) i++;
        trimmed = trimmed.substring(i).trim();
        if (opPattern == null) return trimmed;
        Matcher m = opPattern.matcher(trimmed);
        return m.find() ? m.group(1) : trimmed;
    }

    private static String valueOrNull(Object o) { return o == null ? null : String.valueOf(o); }

    private static long parseLongSafe(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return (long) Double.parseDouble(String.valueOf(o)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static Element findFirstRelOp(Element root) {
        NodeList list = root.getElementsByTagName("RelOp");
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static ExplainNode parseRelOp(Element relOp) {
        String op = relOp.getAttribute("PhysicalOp");
        long rows = parseLongSafe(relOp.getAttribute("EstimateRows"));
        Double cost = parseDoubleOrNull(relOp.getAttribute("EstimatedTotalSubtreeCost"));
        String table = null;
        NodeList objects = relOp.getElementsByTagName("Object");
        for (int i = 0; i < objects.getLength(); i++) {
            Element o = (Element) objects.item(i);
            if (o.getParentNode() == relOp) {
                String t = o.getAttribute("Table");
                if (t != null && !t.isBlank()) {
                    table = t.replaceAll("[\\[\\]]", "");
                    break;
                }
            }
        }
        List<ExplainNode> children = new ArrayList<>();
        NodeList kids = relOp.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.ELEMENT_NODE && "RelOp".equals(k.getNodeName())) {
                children.add(parseRelOp((Element) k));
            }
        }
        return new ExplainNode(op, table, ScanType.OTHER, rows, cost, null, List.copyOf(children));
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static String permissionKey(String kind) {
        if (kind == null) return "unknown";
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "apache_doris" -> "doris_permission";
            default -> kind.toLowerCase(Locale.ROOT) + "_permission";
        };
    }
}
