package com.datatalk.infra.metadata;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.metadata.SchemaSearchService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SchemaSearchJdbc implements SchemaSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int COMMENT_SNIPPET_MAX = 80;
    private static final String DEFAULT_HINT =
        "若结果为空,可尝试拼音 / 同义词 / 缩写,或缩窄 database/schema 范围";

    private final ConnectionRepository connRepo;
    private final ConnectionService connService;

    public SchemaSearchJdbc(ConnectionRepository connRepo, ConnectionService connService) {
        this.connRepo = connRepo;
        this.connService = connService;
    }

    @Override
    public SchemaSearchResult search(SchemaSearchRequest request) {
        String keyword = normalizeKeyword(request.keyword());
        int limit = boundedLimit(request.limit());

        ConnectionRecord connection = connRepo.findById(request.connectionId())
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unknown connection: " + request.connectionId(), false));

        requireSupportedKind(connection.kind());

        String escaped = escapeForLike(keyword);
        ConnectionRecord effective = withDatabase(connection, request.database());
        String username = ConnectionKind.OCEANBASE.equals(effective.kind())
            ? ConnectionService.composeOceanBaseUsername(effective)
            : effective.username();

        List<ScoredMatch> matches = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(
                JdbcUrlBuilder.build(effective), username, connService.decryptPassword(connection.id()))) {
            if (hasText(request.schema())) {
                trySetSchema(c, effective.kind(), request.schema());
            }
            DatabaseMetaData meta = c.getMetaData();
            String catalogScope = catalogScope(effective.kind(), request.database());
            String schemaScope = schemaScope(effective.kind(), request.schema(), request.database());
            collectMatches(meta, catalogScope, schemaScope, escaped, matches, limit);
        } catch (SQLException e) {
            throw new DataTalkException(DataTalkErrorCodes.UPSTREAM_UNAVAILABLE,
                "schema_search query failed: " + e.getMessage(), true);
        }

        matches.sort(Comparator.<ScoredMatch>comparingInt(m -> m.score).reversed());
        int total = matches.size();
        List<TableMatch> top = matches.stream()
            .limit(limit)
            .map(ScoredMatch::toMatch)
            .toList();
        boolean truncated = total > top.size();
        return new SchemaSearchResult(top, total, truncated, DEFAULT_HINT);
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            throw invalidArgument("keyword is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw invalidArgument("keyword is required");
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw invalidArgument("keyword exceeds 200 characters");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static int boundedLimit(int requested) {
        if (requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    private static String escapeForLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }

    private static String wildcard(String escaped) {
        return "%" + escaped + "%";
    }

    private static void requireSupportedKind(String kind) {
        try {
            ConnectionKind.normalize(kind);
        } catch (DataTalkException ex) {
            throw new DataTalkException(DataTalkErrorCodes.DIALECT_UNSUPPORTED,
                "unsupported dialect for schema_search: " + kind, false);
        }
    }

    private static DataTalkException invalidArgument(String message) {
        return new DataTalkException(DataTalkErrorCodes.SCHEMA_INPUT_INVALID, message, false);
    }

    private void collectMatches(DatabaseMetaData meta, String catalog, String schema,
                                String escapedKeyword, List<ScoredMatch> matches, int limit) throws SQLException {
        String like = wildcard(escapedKeyword);
        // Phase 1: tables whose name OR remarks match keyword.
        Map<String, ScoredMatch> byKey = new LinkedHashMap<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableSchema = rs.getString("TABLE_SCHEM");
                String tableCatalog = rs.getString("TABLE_CAT");
                String remarks = safeRemarks(rs);
                ScoredMatch match = byKey.computeIfAbsent(
                    matchKey(tableCatalog, tableSchema, tableName),
                    k -> new ScoredMatch(tableName, tableSchema, tableCatalog));
                if (containsIgnoreCase(tableName, escapedKeyword)) {
                    match.score += 3;
                    match.matchedOn.add("table.name");
                }
                if (containsIgnoreCase(remarks, escapedKeyword)) {
                    match.score += 1;
                    match.matchedOn.add("table.comment");
                    match.commentSnippet = snippet(remarks);
                }
            }
        }

        // Phase 2: columns whose name OR remarks match keyword.
        try (ResultSet rs = meta.getColumns(catalog, schema, "%", like)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String tableName = rs.getString("TABLE_NAME");
                String tableSchema = rs.getString("TABLE_SCHEM");
                String tableCatalog = rs.getString("TABLE_CAT");
                ScoredMatch match = byKey.computeIfAbsent(
                    matchKey(tableCatalog, tableSchema, tableName),
                    k -> new ScoredMatch(tableName, tableSchema, tableCatalog));
                match.score += 2;
                match.matchedOn.add("column." + columnName + ".name");
            }
        }
        // Phase 3: column comments that match keyword (driver returns REMARKS via getColumns w/o pattern).
        try (ResultSet rs = meta.getColumns(catalog, schema, "%", "%")) {
            while (rs.next()) {
                String remarks = safeRemarks(rs);
                if (!containsIgnoreCase(remarks, escapedKeyword)) continue;
                String columnName = rs.getString("COLUMN_NAME");
                String tableName = rs.getString("TABLE_NAME");
                String tableSchema = rs.getString("TABLE_SCHEM");
                String tableCatalog = rs.getString("TABLE_CAT");
                ScoredMatch match = byKey.computeIfAbsent(
                    matchKey(tableCatalog, tableSchema, tableName),
                    k -> new ScoredMatch(tableName, tableSchema, tableCatalog));
                match.score += 1;
                match.matchedOn.add("column." + columnName + ".comment");
                if (match.commentSnippet == null || match.commentSnippet.isEmpty()) {
                    match.commentSnippet = snippet(remarks);
                }
            }
        }
        for (ScoredMatch m : byKey.values()) {
            if (m.score > 0) matches.add(m);
        }
    }

    private static String safeRemarks(ResultSet rs) {
        try {
            String r = rs.getString("REMARKS");
            return r == null ? "" : r;
        } catch (SQLException e) {
            return "";
        }
    }

    private static boolean containsIgnoreCase(String haystack, String escapedNeedle) {
        if (haystack == null || haystack.isEmpty()) return false;
        // escapedNeedle uses backslash-escaped \% / \_ — convert back to literal for substring match.
        String literal = escapedNeedle
            .replace("\\%", "%")
            .replace("\\_", "_")
            .replace("\\\\", "\\");
        return haystack.toLowerCase(Locale.ROOT).contains(literal);
    }

    private static String snippet(String remarks) {
        if (remarks == null) return "";
        String trimmed = remarks.strip();
        if (trimmed.length() <= COMMENT_SNIPPET_MAX) return trimmed;
        return trimmed.substring(0, COMMENT_SNIPPET_MAX - 3) + "...";
    }

    private static String matchKey(String catalog, String schema, String table) {
        return (catalog == null ? "" : catalog) + "/"
            + (schema == null ? "" : schema) + "/"
            + (table == null ? "" : table);
    }

    private static String catalogScope(String kind, String database) {
        if (!hasText(database)) return null;
        if ("mysql".equalsIgnoreCase(kind) || "mariadb".equalsIgnoreCase(kind)
            || "tidb".equalsIgnoreCase(kind) || "oceanbase".equalsIgnoreCase(kind)
            || "apache_doris".equalsIgnoreCase(kind) || "starrocks".equalsIgnoreCase(kind)
            || "sqlserver".equalsIgnoreCase(kind) || "hive".equalsIgnoreCase(kind)
            || "trino".equalsIgnoreCase(kind) || "presto".equalsIgnoreCase(kind)) {
            return database;
        }
        return null;
    }

    private static String schemaScope(String kind, String schema, String database) {
        if (hasText(schema)) return schema;
        if ("dameng".equalsIgnoreCase(kind) && hasText(database)) {
            return database;
        }
        return null;
    }

    private static void trySetSchema(Connection c, String kind, String schema) {
        try {
            if (("postgres".equalsIgnoreCase(kind) || "postgresql".equalsIgnoreCase(kind)
                || "h2".equalsIgnoreCase(kind) || "sqlserver".equalsIgnoreCase(kind)
                || "kingbase".equalsIgnoreCase(kind) || "gaussdb".equalsIgnoreCase(kind)
                || "dameng".equalsIgnoreCase(kind) || "oracle".equalsIgnoreCase(kind))
                && hasText(schema)) {
                c.setSchema(schema);
            }
        } catch (SQLException ignored) {
            // best-effort; some drivers don't support setSchema
        }
    }

    private static ConnectionRecord withDatabase(ConnectionRecord base, String database) {
        String effective = hasText(database) ? database : base.databaseName();
        return new ConnectionRecord(
            base.id(), base.name(), base.kind(), base.host(), base.port(),
            effective, base.username(), base.passwordEnc(), base.schemaDigest(),
            base.createdAt(), base.connectTimeout(), base.lastTestStatus(), base.lastTestAt(),
            base.oracleServiceType(), base.sqlserverEncrypt(), base.sqlserverTrustServerCertificate(),
            base.sqlserverInstanceName(), base.readOnly(), base.compatibilityMode(),
            base.oceanbaseTenant(), base.oceanbaseCluster()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ScoredMatch {
        final String tableName;
        final String tableSchema;
        final String tableCatalog;
        final List<String> matchedOn = new ArrayList<>();
        int score = 0;
        String commentSnippet = "";

        ScoredMatch(String tableName, String tableSchema, String tableCatalog) {
            this.tableName = tableName;
            this.tableSchema = tableSchema;
            this.tableCatalog = tableCatalog;
        }

        TableMatch toMatch() {
            return new TableMatch(
                tableName,
                tableSchema,
                tableCatalog,
                score,
                List.copyOf(matchedOn),
                commentSnippet
            );
        }
    }
}
