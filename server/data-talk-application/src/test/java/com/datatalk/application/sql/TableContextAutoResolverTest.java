package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableContextAutoResolverTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final TableContextAutoResolver resolver = new TableContextAutoResolver(connectionService);

    @Test
    void auto_locates_unique_schema_for_simple_select() throws Exception {
        String dbName = "mem:auto_locate_unique;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (var c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "");
             var st = c.createStatement()) {
            st.execute("CREATE SCHEMA reporting");
            st.execute("CREATE TABLE reporting.users(id INT PRIMARY KEY)");
        }

        ConnectionRecord connection = h2Connection("c-auto-1", dbName);
        when(connectionService.decryptPassword("c-auto-1")).thenReturn("");

        ResolvedExecutionContext resolved = resolver.resolve(
            new ResolvedExecutionContext(connection, dbName, null),
            "SELECT * FROM users"
        );

        assertThat(resolved.database()).isEqualTo(dbName);
        assertThat(resolved.schema()).isEqualTo("reporting");
        assertThat(resolved.contextNotice()).contains("reporting");
    }

    @Test
    void rejects_ambiguous_unqualified_table_when_multiple_schemas_match() throws Exception {
        String dbName = "mem:auto_locate_ambiguous;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (var c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "");
             var st = c.createStatement()) {
            st.execute("CREATE SCHEMA public");
            st.execute("CREATE SCHEMA reporting");
            st.execute("CREATE TABLE public.users(id INT PRIMARY KEY)");
            st.execute("CREATE TABLE reporting.users(id INT PRIMARY KEY)");
        }

        ConnectionRecord connection = h2Connection("c-auto-2", dbName);
        when(connectionService.decryptPassword("c-auto-2")).thenReturn("");

        assertThatThrownBy(() -> resolver.resolve(
            new ResolvedExecutionContext(connection, dbName, null),
            "SELECT * FROM users"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("users")
            .hasMessageContaining("public")
            .hasMessageContaining("reporting");
    }

    private static ConnectionRecord h2Connection(String id, String dbName) {
        return new ConnectionRecord(
            id,
            "Auto Locate",
            "h2",
            "localhost",
            0,
            dbName,
            "sa",
            new byte[0],
            null,
            Instant.parse("2026-04-21T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null
        );
    }
}
