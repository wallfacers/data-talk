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
            case ConnectionKind.H2 ->
                "jdbc:h2:" + (db != null ? db : "mem:test");
            case ConnectionKind.SQLITE ->
                "jdbc:sqlite:" + sqliteDatabaseName(db);
            case ConnectionKind.MARIADB ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "mariadb connection not yet implemented", false);
            case ConnectionKind.ORACLE ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "oracle connection not yet implemented", false);
            case ConnectionKind.SQLSERVER ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "sqlserver connection not yet implemented", false);
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
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "mariadb connection not yet implemented", false);
            case ORACLE ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "oracle connection not yet implemented", false);
            case SQLSERVER ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_KIND_UNSUPPORTED,
                    "sqlserver connection not yet implemented", false);
            default ->
                throw new DataTalkException(DataTalkErrorCodes.DATABASE_TYPE_UNSUPPORTED,
                    "unsupported database type: " + c.dbType(), false);
        };
    }

    private static String sqliteDatabaseName(String databaseName) {
        return databaseName == null || databaseName.isBlank() ? ":memory:" : databaseName;
    }
}
