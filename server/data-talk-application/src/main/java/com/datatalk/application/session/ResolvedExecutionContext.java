package com.datatalk.application.session;

import com.datatalk.application.persistence.ConnectionRecord;

public record ResolvedExecutionContext(
    ConnectionRecord connection,
    String database,
    String schema,
    String contextNotice
) {
    public ResolvedExecutionContext(ConnectionRecord connection, String database, String schema) {
        this(connection, database, schema, null);
    }

    public String selectedLevel() {
        if (schema != null && !schema.isBlank()) return "schema";
        if (database != null && !database.isBlank()) return "database";
        return "connection";
    }
}
