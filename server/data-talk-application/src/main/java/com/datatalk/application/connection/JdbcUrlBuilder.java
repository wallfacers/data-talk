package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;

/** Centralized JDBC URL builder for various database types. */
public final class JdbcUrlBuilder {

    public static String build(ConnectionRecord c) {
        String db = c.databaseName();
        return switch (c.kind()) {
            case ConnectionKind.POSTGRESQL ->
                "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + (db != null ? db : "postgres");
            case ConnectionKind.MYSQL ->
                db != null ? "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:mysql://" + c.host() + ":" + c.port() + "/";
            case ConnectionKind.TIDB -> {
                boolean hasDb = db != null && !db.isBlank();
                yield hasDb ? "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true"
                            : "jdbc:mysql://" + c.host() + ":" + c.port() + "/?useSSL=false&allowPublicKeyRetrieval=true";
            }
            case ConnectionKind.H2 ->
                "jdbc:h2:" + (db != null ? db : "mem:test");
            case ConnectionKind.SQLITE ->
                "jdbc:sqlite:" + sqliteDatabaseName(db);
            case ConnectionKind.MARIADB ->
                db != null ? "jdbc:mariadb://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:mariadb://" + c.host() + ":" + c.port() + "/";
            case ConnectionKind.ORACLE -> {
                String serviceType = c.oracleServiceType();
                boolean useSid = "sid".equalsIgnoreCase(serviceType);
                if (useSid) {
                    yield "jdbc:oracle:thin:@" + c.host() + ":" + c.port() + ":" + (db != null ? db : "ORCL");
                } else {
                    yield "jdbc:oracle:thin:@//" + c.host() + ":" + c.port() + "/" + (db != null ? db : "ORCL");
                }
            }
            case ConnectionKind.SQLSERVER -> {
                StringBuilder url = new StringBuilder("jdbc:sqlserver://");
                if (c.sqlserverInstanceName() != null && !c.sqlserverInstanceName().isBlank()) {
                    url.append(c.host()).append("\\").append(c.sqlserverInstanceName());
                } else {
                    url.append(c.host());
                }
                url.append(":").append(c.port());
                if (db != null && !db.isBlank()) url.append(";databaseName=").append(db);
                url.append(";encrypt=").append(c.sqlserverEncrypt() != 0);
                url.append(";trustServerCertificate=").append(c.sqlserverTrustServerCertificate());
                yield url.toString();
            }
            case ConnectionKind.DUCKDB -> {
                if (db != null && db.equals(":memory:")) {
                    String sessionId = c.id();
                    String url = "jdbc:duckdb::memory:dt_mem_" + sessionId;
                    if (c.readOnly()) {
                        url += "?readonly=true";
                    }
                    yield url;
                }
                yield "jdbc:duckdb:" + db + (c.readOnly() ? "?readonly=true" : "");
            }
            case ConnectionKind.CLICKHOUSE -> {
                String base = db != null
                    ? "jdbc:clickhouse://" + c.host() + ":" + c.port() + "/" + db
                    : "jdbc:clickhouse://" + c.host() + ":" + c.port() + "/";
                // Port 8443 is ClickHouse native HTTPS port — append ssl=true
                if (c.port() == 8443) {
                    yield base + "?ssl=true";
                }
                yield base;
            }
            case ConnectionKind.APACHE_DORIS ->
                db != null ? "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:mysql://" + c.host() + ":" + c.port() + "/";
            case ConnectionKind.STARROCKS -> {
                if (db == null || db.isBlank()) {
                    throw new DataTalkException(DataTalkErrorCodes.DATABASE_NAME_REQUIRED,
                        "StarRocks", false);
                }
                yield "jdbc:starrocks://" + c.host() + ":" + c.port() + "/default_catalog." + db;
            }
            case ConnectionKind.TRINO -> {
                StringBuilder url = new StringBuilder("jdbc:trino://").append(c.host()).append(":").append(c.port());
                if (db != null && !db.isBlank()) {
                    url.append("/").append(db);
                }
                yield url.toString();
            }
            case ConnectionKind.PRESTO -> {
                StringBuilder url = new StringBuilder("jdbc:presto://")
                    .append(c.host()).append(":").append(c.port());
                if (db != null && !db.isBlank()) {
                    url.append("/").append(db);
                }
                yield url.toString();
            }
            case ConnectionKind.HIVE ->
                db != null ? "jdbc:hive2://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:hive2://" + c.host() + ":" + c.port() + "/";
            case ConnectionKind.OCEANBASE -> {
                String obDb = c.databaseName();
                yield (obDb != null && !obDb.isBlank())
                    ? "jdbc:oceanbase://" + c.host() + ":" + c.port() + "/" + obDb
                    : "jdbc:oceanbase://" + c.host() + ":" + c.port();
            }
            default ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "unsupported database kind: " + c.kind(), false);
        };
    }

    public static String build(DbConnection c) {
        String db = c.databaseName();
        return switch (c.dbType()) {
            case POSTGRESQL ->
                "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + (db != null ? db : "postgres");
            case MYSQL ->
                db != null ? "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:mysql://" + c.host() + ":" + c.port() + "/";
            case H2 ->
                "jdbc:h2:" + (db != null ? db : "mem:test");
            case SQLITE ->
                "jdbc:sqlite:" + sqliteDatabaseName(db);
            case MARIADB ->
                db != null ? "jdbc:mariadb://" + c.host() + ":" + c.port() + "/" + db
                           : "jdbc:mariadb://" + c.host() + ":" + c.port() + "/";
            case ORACLE ->
                "jdbc:oracle:thin:@//" + c.host() + ":" + c.port() + "/" + (db != null ? db : "ORCL");
            case SQLSERVER -> {
                StringBuilder url = new StringBuilder("jdbc:sqlserver://");
                url.append(c.host()).append(":").append(c.port());
                if (db != null && !db.isBlank()) url.append(";databaseName=").append(db);
                url.append(";encrypt=true;trustServerCertificate=true");
                yield url.toString();
            }
            default ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_TYPE_UNSUPPORTED,
                    "unsupported database type: " + c.dbType(), false);
        };
    }

    private static String sqliteDatabaseName(String databaseName) {
        return databaseName == null || databaseName.isBlank() ? ":memory:" : databaseName;
    }
}
