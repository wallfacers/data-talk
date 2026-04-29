package com.datatalk.infra.er;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErErrors;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErTableMeta;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class JdbcErRelationDiscoveryService implements ErRelationDiscoveryService {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;

    public JdbcErRelationDiscoveryService(ConnectionRepository connRepo, ConnectionService conn) {
        this.connRepo = connRepo;
        this.conn = conn;
    }

    @Override
    public ErGraph discover(String connectionId, List<String> seeds, int neighborDepth) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("seed tables must be non-empty");
        }
        if (seeds.size() > MAX_TABLES) {
            throw new ErErrors.ErPayloadOversizedException(seeds.size(), MAX_TABLES);
        }

        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("connection not found: " + connectionId));
        Dialect.fromConnectionKind(cr.kind())
            .orElseThrow(() -> new ErErrors.DialectUnsupportedException(cr.kind()));

        String password = conn.decryptPassword(connectionId);
        String url = JdbcUrlBuilder.build(cr);
        try (Connection c = DriverManager.getConnection(url, cr.username(), password)) {
            DatabaseMetaData meta = c.getMetaData();
            Set<String> selected = new LinkedHashSet<>(seeds);
            verifySeedsExist(meta, seeds);
            expandNeighbors(meta, selected, seeds, Math.max(0, neighborDepth));

            List<ErTableMeta> nodes = readTables(url, cr.username(), password, selected);
            List<ErRelation> edges = nodes.stream()
                .flatMap(node -> node.fkOut().stream())
                .filter(edge -> selected.contains(edge.targetTable()))
                .toList();

            return new ErGraph(nodes, edges, buildSummary(seeds, nodes.size(), edges.size()), List.of());
        } catch (SQLException e) {
            throw new RuntimeException("ER discovery failed: " + e.getMessage(), e);
        }
    }

    private static void verifySeedsExist(DatabaseMetaData meta, List<String> seeds) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String table : seeds) {
            if (!tableExists(meta, table)) missing.add(table);
        }
        if (!missing.isEmpty()) throw new ErErrors.TablesNotFoundException(missing);
    }

    private static boolean tableExists(DatabaseMetaData meta, String table) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, table.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, table.toLowerCase(Locale.ROOT), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static void expandNeighbors(DatabaseMetaData meta, Set<String> selected, List<String> seeds, int depth)
        throws SQLException {
        Set<String> frontier = new LinkedHashSet<>(seeds);
        for (int i = 0; i < depth; i++) {
            Set<String> next = new LinkedHashSet<>();
            for (String table : frontier) {
                try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
                    while (rs.next()) next.add(rs.getString("PKTABLE_NAME"));
                }
                try (ResultSet rs = meta.getExportedKeys(null, null, table)) {
                    while (rs.next()) next.add(rs.getString("FKTABLE_NAME"));
                }
            }
            next.removeAll(selected);
            if (selected.size() + next.size() > MAX_TABLES) {
                throw new ErErrors.ErPayloadOversizedException(selected.size() + next.size(), MAX_TABLES);
            }
            selected.addAll(next);
            frontier = next;
            if (frontier.isEmpty()) return;
        }
    }

    private static List<ErTableMeta> readTables(String url, String username, String password, Set<String> selected) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ErTableMeta>> futures = new ArrayList<>();
            for (String table : selected) {
                futures.add(executor.submit(() -> {
                    try (Connection c = DriverManager.getConnection(url, username, password)) {
                        return readTable(c, table);
                    }
                }));
            }
            List<ErTableMeta> nodes = new ArrayList<>();
            for (Future<ErTableMeta> future : futures) {
                nodes.add(future.get());
            }
            return nodes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ER discovery interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("ER discovery failed: " + e.getMessage(), e);
        }
    }

    private static ErTableMeta readTable(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        Set<String> pks = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, null, table)) {
            while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
        }

        Set<String> fks = new HashSet<>();
        List<ErRelation> fkOut = new ArrayList<>();
        try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
            while (rs.next()) {
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                fks.add(fkColumn);
                fkOut.add(new ErRelation(
                    table,
                    fkColumn,
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME"),
                    "many_to_one",
                    "schema_fk"
                ));
            }
        }

        List<ErColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                columns.add(new ErColumnMeta(
                    name,
                    typeFragment(type, rs.getInt("COLUMN_SIZE")),
                    rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                    pks.contains(name),
                    fks.contains(name),
                    "YES".equalsIgnoreCase(safeString(rs, "IS_AUTOINCREMENT")),
                    rs.getString("COLUMN_DEF"),
                    rs.getString("REMARKS")
                ));
            }
        }

        String tableComment = null;
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
            if (rs.next()) tableComment = rs.getString("REMARKS");
        }
        return new ErTableMeta(table, tableComment, columns, fkOut);
    }

    private static String typeFragment(String type, int columnSize) {
        if (type == null) return "";
        String upper = type.toUpperCase(Locale.ROOT);
        if (upper.startsWith("VARCHAR") || upper.startsWith("CHAR") || upper.startsWith("VARBINARY")
            || upper.startsWith("VARCHAR2")) {
            return type + "(" + columnSize + ")";
        }
        return type;
    }

    private static String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }

    private static String buildSummary(List<String> seeds, int totalTables, int edges) {
        int neighbors = totalTables - seeds.size();
        String head = String.join(" + ", seeds);
        if (neighbors > 0) {
            head += " + " + neighbors + " neighbor" + (neighbors == 1 ? "" : "s");
        }
        return head + ", " + totalTables + " table" + (totalTables == 1 ? "" : "s")
            + " / " + edges + " edge" + (edges == 1 ? "" : "s");
    }
}
