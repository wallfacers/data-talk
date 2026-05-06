package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import org.springframework.beans.factory.annotation.Qualifier;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class ReadSchemaActionIT {
    private static final String DB_NAME = "mem:readschema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Autowired ConnectionService conn;
    @Autowired ReadSchemaAction action;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;
    @TempDir Path tempDir;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @BeforeEach
    void seedDb() throws Exception {
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + DB_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE SCHEMA IF NOT EXISTS analytics");
            st.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255), created_at TIMESTAMP)");
            st.execute("CREATE TABLE orders (id INT PRIMARY KEY, user_id INT)");
            st.execute("CREATE TABLE analytics.audit_log (id INT PRIMARY KEY, action VARCHAR(255))");
        }
        connectionId = conn.create("Read Schema Test", "h2", "", 0, DB_NAME, "sa", "", null, null, null, null, null);
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-1", connectionId, "Read Schema", "oc-1", now, now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaReturnsBothTables() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-1", connectionId, "oc-1"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaWithoutTablesReturnsTableSummaryOnly() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-summary", connectionId, "oc-summary"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
        assertThat(schema).allSatisfy(table -> assertThat(table).doesNotContainKey("columns"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaWithTablesReturnsOnlyRequestedColumnDetails() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-filtered", connectionId, "oc-filtered"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC", "tables", List.of("users"))
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name")).containsExactly("users");

        List<Map<String, Object>> columns = (List<Map<String, Object>>) schema.getFirst().get("columns");
        assertThat(columns).extracting(c -> c.get("name"))
            .containsExactlyInAnyOrder("id", "name", "created_at");
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverDefaultsToFiftyTablesAndReturnsPaginationMetadata() throws Exception {
        createUserTables(IntStream.range(0, 60)
            .mapToObj(i -> "bulk_" + String.format("%02d", i))
            .toList());

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-large", connectionId, "oc-large"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).hasSize(50);
        assertThat(schema).allSatisfy(table -> assertThat(table).doesNotContainKey("columns"));
        assertThat(out)
            .containsEntry("mode", "discover")
            .containsEntry("returnedCount", 50)
            .containsEntry("totalCount", 62)
            .containsEntry("truncated", true)
            .containsEntry("nextCursor", "50");
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverUsesCursorForNextPage() throws Exception {
        createUserTables(IntStream.range(0, 20)
            .mapToObj(i -> "page_" + String.format("%02d", i))
            .toList());

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-page", connectionId, "oc-page"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC", "limit", 10, "cursor", "10")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).hasSize(10);
        assertThat(out)
            .containsEntry("returnedCount", 10)
            .containsEntry("totalCount", 22)
            .containsEntry("truncated", true)
            .containsEntry("nextCursor", "20");
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverFiltersTablesByPattern() throws Exception {
        createUserTables(List.of("customer_events", "invoice_events"));

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-pattern", connectionId, "oc-pattern"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC", "pattern", "customer")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactly("customer_events");
        assertThat(out).containsEntry("truncated", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverCanSearchColumnNamesWhenRequested() throws Exception {
        createUserTables(List.of("invoices"));
        try (var c = DriverManager.getConnection("jdbc:h2:" + DB_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("ALTER TABLE invoices ADD COLUMN total_amount DECIMAL(12,2)");
        }

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-col-search", connectionId, "oc-col-search"),
            Map.of(
                "connectionId", connectionId,
                "schema", "PUBLIC",
                "pattern", "amount",
                "searchColumns", true
            )
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactly("invoices");
    }

    @Test
    void describeRejectsTooManyExplicitTables() {
        List<String> requested = IntStream.range(0, 21)
            .mapToObj(i -> "table_" + i)
            .toList();

        assertThatThrownBy(() -> action.handle(
            new ActionContext("s-1", "c-too-many", connectionId, "oc-too-many"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC", "tables", requested)
        ).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 20 tables");
    }

    @Test
    @SuppressWarnings("unchecked")
    void describeTruncatesVeryWideTableColumns() throws Exception {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            columns.add("c" + String.format("%03d", i) + " INT");
        }
        try (var c = DriverManager.getConnection("jdbc:h2:" + DB_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("CREATE TABLE wide_table(" + String.join(",", columns) + ")");
        }

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-wide", connectionId, "oc-wide"),
            Map.of("connectionId", connectionId, "schema", "PUBLIC", "tables", List.of("wide_table"))
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        List<Map<String, Object>> returnedColumns = (List<Map<String, Object>>) schema.getFirst().get("columns");
        assertThat(returnedColumns).hasSize(200);
        assertThat(schema.getFirst()).containsEntry("columnsTruncated", true);
        assertThat(out).containsEntry("mode", "describe");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaUsesSessionContextWhenInputOmitsConnection() throws Exception {
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-ctx", connectionId, "Read Schema", "oc-ctx", now, now);
        datatalkJdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, "s-ctx", connectionId, "Read Schema Test", DB_NAME, "PUBLIC", "schema", now);

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-ctx", "c-2", null, "oc-ctx"),
            Map.of()
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }

    @Test
    void readSchema_requires_active_connection_with_localized_message() {
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, NULL, ?, 1, ?, ?, ?, 0)
            """, "s-no-conn", "Read Schema", "oc-missing", now, now);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        assertThatThrownBy(() -> action.handle(
            new ActionContext("s-no-conn", "c-missing", null, "oc-missing"),
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("当前会话没有激活的数据源");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqliteDiscoverFiltersInternalTablesAndAppliesPagination() throws Exception {
        String sqliteConnectionId = createSqliteFixtureConnection();

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-sqlite-discover", sqliteConnectionId, "oc-sqlite-discover"),
            Map.of("connectionId", sqliteConnectionId, "mode", "discover", "limit", 2)
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).hasSize(2);
        assertThat(schema).extracting(t -> t.get("name"))
            .doesNotContain("sqlite_sequence");
        assertThat(out)
            .containsEntry("mode", "discover")
            .containsEntry("returnedCount", 2)
            .containsEntry("totalCount", 3)
            .containsEntry("truncated", true)
            .containsEntry("nextCursor", "2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqliteDescribeReturnsRequestedTableColumnsOnly() throws Exception {
        String sqliteConnectionId = createSqliteFixtureConnection();

        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-sqlite-describe", sqliteConnectionId, "oc-sqlite-describe"),
            Map.of("connectionId", sqliteConnectionId, "mode", "describe", "tables", List.of("accounts"))
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name")).containsExactly("accounts");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) schema.getFirst().get("columns");
        assertThat(columns).extracting(c -> c.get("name"))
            .containsExactlyInAnyOrder("id", "name");
    }

    private static void createUserTables(List<String> tableNames) throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:" + DB_NAME, "sa", "");
             var st = c.createStatement()) {
            for (String tableName : tableNames) {
                st.execute("CREATE TABLE " + tableName + "(id INT PRIMARY KEY)");
            }
        }
    }

    private String createSqliteFixtureConnection() throws Exception {
        Path dbFile = tempDir.resolve("schema-fixture.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var st = c.createStatement()) {
            st.execute("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
            st.execute("CREATE TABLE invoices(id INTEGER PRIMARY KEY, account_id INTEGER)");
            st.execute("CREATE TABLE report_cache(id INTEGER PRIMARY KEY)");
            st.execute("INSERT INTO accounts(name) VALUES ('Ada')");
        }
        return conn.create("SQLite Schema Test", "sqlite", "", 0, dbFile.toString(), "", "", null, null, null, null, null);
    }
}
