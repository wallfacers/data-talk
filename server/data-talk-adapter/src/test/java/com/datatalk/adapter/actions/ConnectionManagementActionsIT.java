package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ConnectionManagementActionsIT {

    @TempDir Path tempDir;

    @Autowired ConnectionService connections;
    @Autowired ConnectionRepository connectionRepo;
    @Autowired SessionRepository sessionRepo;
    @Autowired SessionDataContextRepository contextRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired ActionRegistry registry;
    @Autowired ListConnectionsAction listConnectionsAction;
    @Autowired CreateConnectionAction createConnectionAction;
    @Autowired TestConnectionAction testConnectionAction;
    @Autowired SelectConnectionAction selectConnectionAction;
    @Autowired UpdateConnectionConfirmableAction updateConnectionConfirmableAction;

    @MockBean ConnectionTargetDiscoveryService discovery;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @BeforeEach
    void reset() throws Exception {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS session_data_contexts (
              session_id TEXT PRIMARY KEY,
              connection_id TEXT,
              connection_name_snapshot TEXT,
              database_name TEXT,
              schema_name TEXT,
              selected_level TEXT,
              updated_at INTEGER NOT NULL
            )
            """);
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        jdbc.update("DELETE FROM connections");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_create_test_and_select_connection_actions_work_without_delete_action() throws Exception {
        DriverManager.getConnection("jdbc:h2:mem:mgmt;DB_CLOSE_DELAY=-1", "sa", "").close();

        Map<String, Object> created = (Map<String, Object>) createConnectionAction.handle(
            new ActionContext("s-create", "call-create", null, "oc-create"),
            Map.of(
                "name", "AI 数据源",
                "kind", "h2",
                "host", "localhost",
                "port", 0,
                "databaseName", "mem:mgmt;DB_CLOSE_DELAY=-1",
                "username", "sa",
                "password", "",
                "connectTimeout", 3000
            )
        ).toCompletableFuture().get();

        assertThat(created.get("id")).isInstanceOf(String.class);
        String connectionId = String.valueOf(created.get("id"));

        Map<String, Object> listed = (Map<String, Object>) listConnectionsAction.handle(
            new ActionContext("s-create", "call-list", null, "oc-create"),
            Map.of()
        ).toCompletableFuture().get();
        List<?> connections = (List<?>) listed.get("connections");
        assertThat(connections).isNotEmpty();

        Map<String, Object> tested = (Map<String, Object>) testConnectionAction.handle(
            new ActionContext("s-create", "call-test", connectionId, "oc-create"),
            Map.of("connectionId", connectionId)
        ).toCompletableFuture().get();
        assertThat(tested).containsEntry("ok", true);
        assertThat(tested).containsKey("latencyMs");

        sessionRepo.upsert(new SessionRecord("s-select", null, "Select", false, null, 1L, 1L, false));
        Map<String, Object> selected = (Map<String, Object>) selectConnectionAction.handle(
            new ActionContext("s-select", "call-select", null, "oc-select"),
            Map.of("connectionId", connectionId)
        ).toCompletableFuture().get();

        assertThat(selected).containsEntry("connectionId", connectionId);
        assertThat(selected).containsEntry("connectionNameSnapshot", "AI 数据源");
        SessionDataContextRecord context = contextRepo.findBySessionId("s-select").orElseThrow();
        assertThat(context.connectionId()).isEqualTo(connectionId);
        assertThat(context.connectionNameSnapshot()).isEqualTo("AI 数据源");
        assertThat(context.databaseName()).isNull();
        assertThat(context.schemaName()).isNull();
        assertThatThrownBy(() -> registry.require("datatalk.delete_connection"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_and_test_sqlite_file_connection() throws Exception {
        Path dbFile = tempDir.resolve("datatalk-sqlite-connection.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var st = c.createStatement()) {
            st.execute("CREATE TABLE sample(id INTEGER PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO sample(name) VALUES ('alpha')");
        }

        Map<String, Object> created = (Map<String, Object>) createConnectionAction.handle(
            new ActionContext("s-sqlite", "call-create-sqlite", null, "oc-sqlite"),
            Map.of(
                "name", "SQLite 文件",
                "kind", "sqlite",
                "databaseName", dbFile.toString(),
                "connectTimeout", 3000
            )
        ).toCompletableFuture().get();

        String connectionId = String.valueOf(created.get("id"));
        assertThat(connectionRepo.findById(connectionId).orElseThrow())
            .extracting(ConnectionRecord::kind, ConnectionRecord::databaseName, ConnectionRecord::host, ConnectionRecord::port)
            .containsExactly("sqlite", dbFile.toString(), "", 0);

        Map<String, Object> tested = (Map<String, Object>) testConnectionAction.handle(
            new ActionContext("s-sqlite", "call-test-sqlite", connectionId, "oc-sqlite"),
            Map.of("connectionId", connectionId)
        ).toCompletableFuture().get();

        assertThat(tested).containsEntry("ok", true);
        assertThat(tested).containsKey("latencyMs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_connection_confirmable_requires_confirmation_and_validates_current_session() throws Exception {
        String connectionId = connections.create(
            "原名称",
            "h2",
            "localhost",
            0,
            "mem:update_confirmable;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            3000,
            null,
            null,
            null,
            null,
            null
        );
        sessionRepo.upsert(new SessionRecord("s-update", connectionId, "Update", false, null, 1L, 1L, false));
        contextRepo.upsert(new SessionDataContextRecord(
            "s-update",
            connectionId,
            "原名称",
            "legacy_db",
            "legacy_schema",
            "schema",
            1L
        ));

        when(discovery.discover(connectionId)).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            connectionId,
            "新名称",
            Set.of("fresh_db"),
            Set.of("fresh_schema")
        ));

        Map<String, Object> preview = (Map<String, Object>) updateConnectionConfirmableAction.handle(
            new ActionContext("s-update", "call-update-1", connectionId, "oc-update"),
            Map.of(
                "connectionId", connectionId,
                "name", "新名称",
                "kind", "h2",
                "host", "localhost",
                "port", 0,
                "databaseName", "mem:update_confirmable;DB_CLOSE_DELAY=-1",
                "username", "sa",
                "password", "",
                "connectTimeout", 3000
            )
        ).toCompletableFuture().get();

        assertThat(preview).containsEntry("confirm_required", true);
        assertThat(preview.get("confirmation_token")).isInstanceOf(String.class);
        assertThat(connectionRepo.findById(connectionId).orElseThrow().name()).isEqualTo("原名称");

        var confirmPayload = new java.util.LinkedHashMap<String, Object>();
        confirmPayload.put("connectionId", connectionId);
        confirmPayload.put("name", "新名称");
        confirmPayload.put("kind", "h2");
        confirmPayload.put("host", "localhost");
        confirmPayload.put("port", 0);
        confirmPayload.put("databaseName", "mem:update_confirmable;DB_CLOSE_DELAY=-1");
        confirmPayload.put("username", "sa");
        confirmPayload.put("password", "");
        confirmPayload.put("connectTimeout", 3000);
        confirmPayload.put("confirm", true);
        confirmPayload.put("confirmationToken", preview.get("confirmation_token"));

        Map<String, Object> result = (Map<String, Object>) updateConnectionConfirmableAction.handle(
            new ActionContext("s-update", "call-update-2", connectionId, "oc-update"),
            confirmPayload
        ).toCompletableFuture().get();

        assertThat(result).containsEntry("ok", true);
        assertThat(((Map<String, Object>) result.get("connection")).get("name")).isEqualTo("新名称");

        SessionDataContextRecord refreshed = contextRepo.findBySessionId("s-update").orElseThrow();
        assertThat(refreshed.connectionId()).isEqualTo(connectionId);
        assertThat(refreshed.connectionNameSnapshot()).isEqualTo("新名称");
        assertThat(refreshed.databaseName()).isNull();
        assertThat(refreshed.schemaName()).isNull();
    }

    @Test
    void update_connection_confirmable_requires_localized_confirmation_token() {
        String connectionId = connections.create(
            "原名称",
            "h2",
            "localhost",
            0,
            "mem:update_confirmable_missing_token;DB_CLOSE_DELAY=-1",
            "sa",
            "",
            3000,
            null,
            null,
            null,
            null,
            null
        );
        sessionRepo.upsert(new SessionRecord("s-update-token", connectionId, "Update", false, null, 1L, 1L, false));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        assertThatThrownBy(() -> updateConnectionConfirmableAction.handle(
            new ActionContext("s-update-token", "call-update-token", connectionId, "oc-update"),
            Map.of(
                "connectionId", connectionId,
                "name", "新名称",
                "kind", "h2",
                "host", "localhost",
                "port", 0,
                "databaseName", "mem:update_confirmable_missing_token;DB_CLOSE_DELAY=-1",
                "username", "sa",
                "password", "",
                "connectTimeout", 3000,
                "confirm", true
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("必须提供确认令牌");
    }
}
