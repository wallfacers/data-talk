package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TableContextAutoResolver {

    private static final Pattern TABLE_REF = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][\\w$]*)");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "pg_catalog", "sys", "system_lobs");

    private final ConnectionService connectionService;
    private final Translator translator;

    public TableContextAutoResolver(ConnectionService connectionService, Translator translator) {
        this.connectionService = connectionService;
        this.translator = translator;
    }

    public ResolvedExecutionContext resolve(ResolvedExecutionContext context, String sql) {
        if (context == null || context.connection() == null || !requiresLocation(context) || !isEligibleSql(sql)) {
            return context;
        }
        String tableName = extractSingleUnqualifiedTable(sql);
        if (tableName == null) {
            return context;
        }

        List<Candidate> matches = locateCandidates(context, tableName);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(translator.get("error.table.not_found", tableName));
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(translator.get(
                "error.table.ambiguous",
                tableName,
                String.join(", ", matches.stream().map(Candidate::label).toList())
            ));
        }

        Candidate match = matches.get(0);
        if (equalsIgnoreCase(context.database(), match.database()) && equalsIgnoreCase(context.schema(), match.schema())) {
            return context;
        }
        return new ResolvedExecutionContext(
            context.connection(),
            match.database(),
            match.schema(),
            match.notice()
        );
    }

    private List<Candidate> locateCandidates(ResolvedExecutionContext context, String tableName) {
        ConnectionRecord connection = context.connection();
        List<Candidate> matches = new ArrayList<>();
        try (var jdbc = DriverManager.getConnection(
            JdbcUrlBuilder.build(withDatabase(connection, firstNonBlank(context.database(), connection.databaseName()))),
            connection.username(),
            connectionService.decryptPassword(connection.id())
        )) {
            var meta = jdbc.getMetaData();
            try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String catalog = tables.getString("TABLE_CAT");
                    String schema = tables.getString("TABLE_SCHEM");
                    String actual = tables.getString("TABLE_NAME");
                    if (!equalsIgnoreCase(actual, tableName)) {
                        continue;
                    }
                    if (isMysql(connection.kind())) {
                        if (!hasText(catalog)) continue;
                        matches.add(new Candidate(
                            catalog,
                            null,
                            translator.get("sql.context.auto_use.database", catalog)
                        ));
                    } else {
                        if (!hasText(schema) || isSystemSchema(schema)) continue;
                        matches.add(new Candidate(
                            firstNonBlank(context.database(), connection.databaseName()),
                            schema,
                            translator.get("sql.context.auto_use.schema", schema)
                        ));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                translator.get("error.table.auto_locate_failed", tableName, e.getMessage()),
                e
            );
        }
        return new ArrayList<>(new LinkedHashSet<>(matches));
    }

    private static boolean requiresLocation(ResolvedExecutionContext context) {
        if (isSqlite(context.connection().kind())) {
            return false;
        }
        return isMysql(context.connection().kind())
            ? !hasText(context.database())
            : !hasText(context.schema());
    }

    private static boolean isEligibleSql(String sql) {
        if (!hasText(sql)) return false;
        return sql.trim().toLowerCase(Locale.ROOT).startsWith("select");
    }

    private static String extractSingleUnqualifiedTable(String sql) {
        Matcher matcher = TABLE_REF.matcher(sql);
        Set<String> tables = new LinkedHashSet<>();
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!hasText(candidate) || candidate.contains(".")) {
                return null;
            }
            tables.add(candidate);
        }
        return tables.size() == 1 ? tables.iterator().next() : null;
    }

    private static boolean isSystemSchema(String schema) {
        return SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT));
    }

    private static ConnectionRecord withDatabase(ConnectionRecord connection, String database) {
        return new ConnectionRecord(
            connection.id(),
            connection.name(),
            connection.kind(),
            connection.host(),
            connection.port(),
            database,
            connection.username(),
            connection.passwordEnc(),
            connection.schemaDigest(),
            connection.createdAt(),
            connection.connectTimeout(),
            connection.lastTestStatus(),
            connection.lastTestAt(),
            connection.oracleServiceType(),
            connection.sqlserverEncrypt(),
            connection.sqlserverTrustServerCertificate(),
            connection.sqlserverInstanceName()
        );
    }

    private static boolean isMysql(String kind) {
        return "mysql".equalsIgnoreCase(kind);
    }

    private static boolean isSqlite(String kind) {
        return "sqlite".equalsIgnoreCase(kind);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) return left == null && right == null;
        return left.equalsIgnoreCase(right);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record Candidate(String database, String schema, String notice) {
        String label() {
            if (hasText(schema)) return schema;
            if (hasText(database)) return database;
            return "<unknown>";
        }
    }
}
