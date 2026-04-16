package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;

/** Centralized JDBC URL builder for various database types. */
public final class JdbcUrlBuilder {

    public static String build(ConnectionRecord c) {
        return switch (c.kind()) {
            case ConnectionKind.POSTGRESQL ->
                "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case ConnectionKind.MYSQL ->
                "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case ConnectionKind.H2 ->
                "jdbc:h2:" + c.databaseName();
            case ConnectionKind.SQLITE ->
                "jdbc:sqlite:" + c.databaseName();
            default ->
                throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                    "unsupported database kind: " + c.kind(), false);
        };
    }
}