package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.JsonSchemaLoader;
import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class SessionDataContextActionsIT {

    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;
    @Autowired ConnectionService connections;
    @Autowired SessionRepository sessions;
    @Autowired ActionRegistry registry;
    @Autowired JsonSchemaLoader schemas;
    @Autowired GetDataContextAction getDataContextAction;
    @Autowired SetDataContextAction setDataContextAction;
    @Autowired ResolveUseTargetAction resolveUseTargetAction;
    @Autowired ListConnectionTargetsAction listConnectionTargetsAction;

    @MockBean ConnectionTargetDiscoveryService discovery;

    private String c1Id;
    private String c2Id;
    private String c1Name;
    private String c2Name;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        jdbc.update("DELETE FROM connections");
        String suffix = Long.toString(System.nanoTime());
        c1Name = "分析库-" + suffix;
        c2Name = "运营库-" + suffix;
        c1Id = connections.create(c1Name, "h2", "localhost", 0, "analytics", "sa", "", 3000, null, null, null, null);
        c2Id = connections.create(c2Name, "h2", "localhost", 0, "ops", "sa", "", 3000, null, null, null, null);
        sessions.upsert(new SessionRecord("s1", c1Id, "上下文动作测试", false, null, 1L, 1L, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_set_and_resolve_use_actions_round_trip_context() throws Exception {
        when(discovery.discover(c1Id)).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            c1Id,
            c1Name,
            Set.of("analytics", "warehouse"),
            Set.of("public", "reporting")
        ));

        Map<String, Object> before = (Map<String, Object>) getDataContextAction.handle(
            new ActionContext("s1", "call-get-1", c1Id, "oc-1"),
            Map.of()
        ).toCompletableFuture().get();
        assertThat(before.get("sessionId")).isEqualTo("s1");
        assertThat(before.get("connectionId")).isEqualTo(c1Id);
        assertThat(before.get("connectionNameSnapshot")).isEqualTo(c1Name);
        assertThat(before.get("selectedLevel")).isEqualTo("connection");

        Map<String, Object> resolved = (Map<String, Object>) resolveUseTargetAction.handle(
            new ActionContext("s1", "call-resolve", c1Id, "oc-1"),
            Map.of("target", c1Name)
        ).toCompletableFuture().get();
        assertThat(resolved.get("status")).isEqualTo("matched");
        Map<String, Object> matched = (Map<String, Object>) resolved.get("matched_target");
        assertThat(matched.get("level")).isEqualTo("connection");

        Map<String, Object> stored = (Map<String, Object>) setDataContextAction.handle(
            new ActionContext("s1", "call-set", c1Id, "oc-1"),
            Map.of(
                "connectionId", c1Id,
                "database", "analytics",
                "schema", "public",
                "selectedLevel", "schema"
            )
        ).toCompletableFuture().get();
        assertThat(stored.get("connectionId")).isEqualTo(c1Id);
        assertThat(stored.get("database")).isEqualTo("analytics");
        assertThat(stored.get("schema")).isEqualTo("public");
        assertThat(stored.get("selectedLevel")).isEqualTo("schema");

        Map<String, Object> fetched = (Map<String, Object>) getDataContextAction.handle(
            new ActionContext("s1", "call-get-2", c1Id, "oc-1"),
            Map.of()
        ).toCompletableFuture().get();
        assertThat(fetched).containsEntry("connectionNameSnapshot", c1Name);
        assertThat(fetched).containsEntry("schema", "public");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_connection_targets_uses_explicit_or_session_connection() throws Exception {
        when(discovery.discover(c1Id)).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            c1Id,
            c1Name,
            Set.of("warehouse", "analytics"),
            Set.of("reporting", "public")
        ));
        when(discovery.discover(c2Id)).thenReturn(new ConnectionTargetDiscoveryService.DiscoveryResult(
            c2Id,
            c2Name,
            Set.of("ops"),
            Set.of("public")
        ));

        Map<String, Object> current = (Map<String, Object>) listConnectionTargetsAction.handle(
            new ActionContext("s1", "call-targets-1", c1Id, "oc-1"),
            Map.of("connectionId", c1Id)
        ).toCompletableFuture().get();
        assertThat(current.get("connectionId")).isEqualTo(c1Id);
        assertThat(current.get("databases")).isEqualTo(java.util.List.of("analytics", "warehouse"));
        assertThat(current.get("schemas")).isEqualTo(java.util.List.of("public", "reporting"));

        setDataContextAction.handle(
            new ActionContext("s1", "call-set", c1Id, "oc-1"),
            Map.of("connectionId", c2Id, "selectedLevel", "connection")
        ).toCompletableFuture().get();

        Map<String, Object> inferred = (Map<String, Object>) listConnectionTargetsAction.handle(
            new ActionContext("s1", "call-targets-2", c2Id, "oc-1"),
            Map.of()
        ).toCompletableFuture().get();
        assertThat(inferred.get("connectionId")).isEqualTo(c2Id);
        assertThat(inferred.get("connectionName")).isEqualTo(c2Name);
    }

    @Test
    void list_connection_targets_requires_active_connection_when_not_provided() {
        sessions.upsert(new SessionRecord("s2", null, "无连接会话", false, null, 1L, 1L, false));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        assertThatThrownBy(() -> listConnectionTargetsAction.handle(
            new ActionContext("s2", "call-targets-3", null, "oc-1"),
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("当前会话没有激活的数据源");
    }

    @Test
    void actions_are_registered() {
        assertThat(registry.require("datatalk.get_data_context").id()).isEqualTo("datatalk.get_data_context");
        assertThat(registry.require("datatalk.set_data_context").id()).isEqualTo("datatalk.set_data_context");
        assertThat(registry.require("datatalk.resolve_use_target").id()).isEqualTo("datatalk.resolve_use_target");
        assertThat(registry.require("datatalk.list_connection_targets").id()).isEqualTo("datatalk.list_connection_targets");
        assertThat(registry.handler("datatalk.get_data_context")).isSameAs(getDataContextAction);
        assertThat(registry.handler("datatalk.set_data_context")).isSameAs(setDataContextAction);
        assertThat(registry.handler("datatalk.resolve_use_target")).isSameAs(resolveUseTargetAction);
        assertThat(registry.handler("datatalk.list_connection_targets")).isSameAs(listConnectionTargetsAction);
    }

    @Test
    void set_data_context_schema_requires_full_resolved_target_not_database_only() {
        Map<String, Object> schema = registry.require("datatalk.set_data_context").inputSchema();

        assertThat(schemas.validate(schema, Map.of("database", "ecommerce")).valid())
            .as("database-only set_data_context calls clear the context today and must be rejected")
            .isFalse();
        assertThat(schemas.validate(schema, Map.of("connectionId", c1Id)).valid())
            .as("selectedLevel makes the intended context level explicit")
            .isFalse();
        assertThat(schemas.validate(schema, Map.of(
            "connectionId", c1Id,
            "selectedLevel", "connection"
        )).valid()).isTrue();
        assertThat(schemas.validate(schema, Map.of(
            "connectionId", c1Id,
            "selectedLevel", "database"
        )).valid()).isFalse();
        assertThat(schemas.validate(schema, Map.of(
            "connectionId", c1Id,
            "database", "analytics",
            "selectedLevel", "database"
        )).valid()).isTrue();
        assertThat(schemas.validate(schema, Map.of(
            "connectionId", c1Id,
            "selectedLevel", "schema"
        )).valid()).isFalse();
        assertThat(schemas.validate(schema, Map.of(
            "connectionId", c1Id,
            "schema", "public",
            "selectedLevel", "schema"
        )).valid()).isTrue();
    }
}
