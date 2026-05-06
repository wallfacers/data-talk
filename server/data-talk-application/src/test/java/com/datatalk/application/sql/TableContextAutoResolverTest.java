package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.session.ResolvedExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableContextAutoResolverTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final TableContextAutoResolver resolver = new TableContextAutoResolver(connectionService, translator());
    @TempDir Path tempDir;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

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

    @Test
    void rejects_ambiguous_unqualified_table_with_en_localized_message() throws Exception {
        String dbName = "mem:auto_locate_ambiguous_en;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (var c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "");
             var st = c.createStatement()) {
            st.execute("CREATE SCHEMA public");
            st.execute("CREATE SCHEMA reporting");
            st.execute("CREATE TABLE public.users(id INT PRIMARY KEY)");
            st.execute("CREATE TABLE reporting.users(id INT PRIMARY KEY)");
        }

        ConnectionRecord connection = h2Connection("c-auto-en", dbName);
        when(connectionService.decryptPassword("c-auto-en")).thenReturn("");
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThatThrownBy(() -> resolver.resolve(
            new ResolvedExecutionContext(connection, dbName, null),
            "SELECT * FROM users"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Table users matches multiple candidates")
            .hasMessageContaining("Select a database/schema first");
    }

    @Test
    void leaves_sqlite_context_file_scoped_without_schema_auto_location() throws Exception {
        Path dbFile = tempDir.resolve("users.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var st = c.createStatement()) {
            st.execute("CREATE TABLE users(id INTEGER PRIMARY KEY)");
        }
        ConnectionRecord connection = new ConnectionRecord(
            "sqlite-auto",
            "SQLite File",
            "sqlite",
            "",
            0,
            dbFile.toString(),
            "",
            new byte[0],
            null,
            Instant.parse("2026-04-21T00:00:00Z").toEpochMilli(),
            3000,
            null,
            null,
            null);
        when(connectionService.decryptPassword("sqlite-auto")).thenReturn("");

        ResolvedExecutionContext resolved = resolver.resolve(
            new ResolvedExecutionContext(connection, dbFile.toString(), null),
            "SELECT * FROM users"
        );

        assertThat(resolved.database()).isEqualTo(dbFile.toString());
        assertThat(resolved.schema()).isNull();
        assertThat(resolved.contextNotice()).isNull();
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
            null,
            null);
    }

    private static Translator translator() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("error.table.not_found", Locale.ENGLISH, "Table {0} was not found. Select a database/schema first.");
        source.addMessage("error.table.not_found", Locale.SIMPLIFIED_CHINESE, "未找到表 {0}，请先选择 database/schema");
        source.addMessage("error.table.ambiguous", Locale.ENGLISH, "Table {0} matches multiple candidates: {1}. Select a database/schema first.");
        source.addMessage("error.table.ambiguous", Locale.SIMPLIFIED_CHINESE, "表 {0} 命中多个候选：{1}。请先明确选择 database/schema");
        source.addMessage("error.table.auto_locate_failed", Locale.ENGLISH, "Unable to locate table {0} automatically: {1}");
        source.addMessage("error.table.auto_locate_failed", Locale.SIMPLIFIED_CHINESE, "无法自动定位表 {0}：{1}");
        source.addMessage("sql.context.auto_use.database", Locale.ENGLISH, "Automatically used database {0}");
        source.addMessage("sql.context.auto_use.database", Locale.SIMPLIFIED_CHINESE, "已自动使用 database {0}");
        source.addMessage("sql.context.auto_use.schema", Locale.ENGLISH, "Automatically used schema {0}");
        source.addMessage("sql.context.auto_use.schema", Locale.SIMPLIFIED_CHINESE, "已自动使用 schema {0}");
        return new Translator(source);
    }
}
