package com.datatalk.application.connection;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;

import java.util.HashSet;
import java.util.Set;

/** Constants for database connection kinds (matches ontology schema). */
public final class ConnectionKind {
    public static final String POSTGRESQL = "postgresql";
    public static final String MYSQL = "mysql";
    public static final String H2 = "h2";
    public static final String SQLITE = "sqlite";
    public static final String MARIADB = "mariadb";
    public static final String ORACLE = "oracle";
    public static final String SQLSERVER = "sqlserver";
    public static final String DUCKDB = "duckdb";
    public static final String CLICKHOUSE = "clickhouse";
    public static final String APACHE_DORIS = "apache_doris";
    public static final String STARROCKS = "starrocks";
    public static final String TRINO = "trino";
    public static final String PRESTO = "presto";
    public static final String HIVE = "hive";
    public static final String TIDB = "tidb";
    public static final String OCEANBASE = "oceanbase";
    public static final String DAMENG = "dameng";
    public static final String KINGBASE = "kingbase";

    /** Accepted aliases that normalize to a canonical kind (case-insensitive). */
    private static final Set<String> ACCEPTED_ALIASES = Set.of(
        "postgres",    // -> postgresql
        "mssql",       // -> sqlserver
        "ch",          // -> clickhouse
        "doris",       // -> apache_doris
        "kingbasees"   // -> kingbase (only Wave C alias permitted per umbrella §8 line 293)
    );

    /** Known but rejected inputs — these are NOT accepted as aliases. */
    private static final Set<String> REJECTED_KNOWN_DAMENG = Set.of(
        "dm", "dm8", "dm7",
        "dameng7", "dameng8"
    );

    private static final Set<String> REJECTED_KNOWN_KINGBASE = Set.of(
        "kb", "kbase",
        "kingbase7", "kingbase8", "kingbase9"
    );

    private static final Set<String> REJECTED_KNOWN =
        union(REJECTED_KNOWN_DAMENG, REJECTED_KNOWN_KINGBASE);

    /**
     * Normalizes a user-provided kind string to a canonical constant.
     * Accepts exact matches (case-insensitive) and a small set of aliases.
     * Rejects known near-misses with a structured error.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw unknownKindError(input);
        }
        String trimmed = input.trim();

        // Exact canonical match (case-insensitive)
        if (equalsIgnoreCase(trimmed, POSTGRESQL)) return POSTGRESQL;
        if (equalsIgnoreCase(trimmed, MYSQL)) return MYSQL;
        if (equalsIgnoreCase(trimmed, H2)) return H2;
        if (equalsIgnoreCase(trimmed, SQLITE)) return SQLITE;
        if (equalsIgnoreCase(trimmed, MARIADB)) return MARIADB;
        if (equalsIgnoreCase(trimmed, ORACLE)) return ORACLE;
        if (equalsIgnoreCase(trimmed, SQLSERVER)) return SQLSERVER;
        if (equalsIgnoreCase(trimmed, DUCKDB)) return DUCKDB;
        if (equalsIgnoreCase(trimmed, CLICKHOUSE)) return CLICKHOUSE;
        if (equalsIgnoreCase(trimmed, APACHE_DORIS)) return APACHE_DORIS;
        if (equalsIgnoreCase(trimmed, STARROCKS)) return STARROCKS;
        if (equalsIgnoreCase(trimmed, TRINO)) return TRINO;
        if (equalsIgnoreCase(trimmed, PRESTO)) return PRESTO;
        if (equalsIgnoreCase(trimmed, HIVE)) return HIVE;
        if (equalsIgnoreCase(trimmed, TIDB)) return TIDB;
        if (equalsIgnoreCase(trimmed, OCEANBASE)) return OCEANBASE;
        if (equalsIgnoreCase(trimmed, DAMENG)) return DAMENG;
        if (equalsIgnoreCase(trimmed, KINGBASE)) return KINGBASE;

        // Accepted aliases
        if (equalsIgnoreCase(trimmed, "postgres")) return POSTGRESQL;
        if (equalsIgnoreCase(trimmed, "mssql")) return SQLSERVER;
        if (equalsIgnoreCase(trimmed, "ch")) return CLICKHOUSE;
        if (equalsIgnoreCase(trimmed, "doris")) return APACHE_DORIS;
        if (equalsIgnoreCase(trimmed, "kingbasees")) return KINGBASE;

        // Known rejected inputs — give a helpful, kind-aware message
        if (isKnownRejected(trimmed)) {
            String lower = trimmed.toLowerCase();
            if (REJECTED_KNOWN_DAMENG.contains(lower)) {
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "unknown database kind '" + input + "'. Use 'dameng' for Dameng DM 8.", false);
            }
            throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                "unknown database kind '" + input + "'. Use 'kingbase' for KingbaseES.", false);
        }

        throw unknownKindError(input);
    }

    private static DataTalkException unknownKindError(String input) {
        return new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
            "unsupported database kind: " + input, false);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static boolean isKnownRejected(String input) {
        String lower = input.toLowerCase();
        for (String r : REJECTED_KNOWN) {
            if (r.equals(lower)) return true;
        }
        return false;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return Set.copyOf(result);
    }
}
