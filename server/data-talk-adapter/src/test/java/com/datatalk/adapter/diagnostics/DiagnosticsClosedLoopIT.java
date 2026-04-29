package com.datatalk.adapter.diagnostics;

import com.datatalk.adapter.actions.LockInfoAction;
import com.datatalk.adapter.actions.OptimizeTableConfirmableAction;
import com.datatalk.adapter.actions.TableSpaceAction;
import com.datatalk.adapter.actions.TerminateSessionConfirmableAction;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.diagnostics.*;
import com.datatalk.infra.diagnostics.MySqlDiagnosticsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class DiagnosticsClosedLoopIT {

    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;
    @Autowired ConnectionService connections;
    @Autowired SessionRepository sessions;
    @Autowired SessionDataContextRepository contexts;
    @Autowired LockInfoAction lockInfoAction;
    @Autowired TableSpaceAction tableSpaceAction;
    @Autowired TerminateSessionConfirmableAction terminateAction;
    @Autowired OptimizeTableConfirmableAction optimizeAction;

    @MockBean MySqlDiagnosticsProvider mysqlProvider;

    private final ActionContext actionContext = new ActionContext("s-loop", "call", null, "oc");

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        jdbc.update("DELETE FROM connections");
        String connectionId = connections.create("MySQL Loop", "mysql", "localhost", 3306, "appdb", "user", "pw", 3000);
        sessions.upsert(new SessionRecord("s-loop", connectionId, "Diagnostics Loop", false, null, 1L, 1L, false));
        contexts.upsert(new SessionDataContextRecord("s-loop", connectionId, "MySQL Loop", "appdb", null, "database", 1L));

        when(mysqlProvider.supportedDriverTypes()).thenReturn(Set.of("mysql"));
        when(mysqlProvider.supportedCapabilities()).thenReturn(Set.of(
            DiagnosticCapability.EXPLAIN,
            DiagnosticCapability.INDEX_HINTS,
            DiagnosticCapability.LOCK_INFO,
            DiagnosticCapability.POOL_STATUS,
            DiagnosticCapability.TABLE_SPACE,
            DiagnosticCapability.TERMINATE_SESSION,
            DiagnosticCapability.OPTIMIZE_TABLE
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void lockRecommendationCanDriveTerminatePreviewAndConfirm() throws Exception {
        when(mysqlProvider.lockInfo(any(), anyString(), eq("appdb"))).thenReturn(DiagnosticResult.ok(new LockReport(
            List.of(new LockReport.LockEntry("users", "EXCLUSIVE", "42", "43", 8_000L, "UPDATE users", "SELECT users")),
            List.of()
        )));
        when(mysqlProvider.terminateSessionPreview(any(), anyString(), eq("42"), eq("appdb")))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionPreview("mysql", "42", "KILL 42", "UPDATE users")));
        when(mysqlProvider.terminateSession(any(), anyString(), eq("42"), eq("appdb")))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionResult(true, "42", "Session terminated")));

        Map<String, Object> lock = (Map<String, Object>) lockInfoAction.handle(actionContext, Map.of()).toCompletableFuture().get();
        Map<String, Object> recommendation = (Map<String, Object>) ((List<?>) lock.get("recommendations")).get(0);

        assertThat(recommendation).containsEntry("suggestedToolName", "datatalk_terminate_session");
        Map<String, Object> args = (Map<String, Object>) recommendation.get("suggestedActionArgs");
        assertThat(args).containsEntry("sessionId", "42");

        var previewInput = new LinkedHashMap<String, Object>(args);
        previewInput.put("confirm", false);
        Map<String, Object> preview = (Map<String, Object>) terminateAction.handle(actionContext, previewInput).toCompletableFuture().get();
        assertThat(preview).containsEntry("confirm_required", true);

        var confirmInput = new LinkedHashMap<String, Object>(args);
        confirmInput.put("confirm", true);
        confirmInput.put("confirmationToken", preview.get("confirmation_token"));
        Map<String, Object> executed = (Map<String, Object>) terminateAction.handle(actionContext, confirmInput).toCompletableFuture().get();
        assertThat(executed).containsEntry("ok", true).containsEntry("sessionId", "42");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tableSpaceRecommendationCanDriveOptimizePreviewAndConfirm() throws Exception {
        when(mysqlProvider.tableSpaceInfo(any(), anyString(), eq("appdb"), any())).thenReturn(DiagnosticResult.ok(new SpaceReport(
            List.of(new SpaceReport.TableSpaceEntry("users", "appdb", 100, 900_000_000L, 0L, 400_000_000L)),
            List.of()
        )));
        when(mysqlProvider.optimizeTablePreview(any(), anyString(), eq("users"), eq("appdb"), eq("appdb")))
            .thenReturn(DiagnosticResult.ok(new OptimizeTablePreview(
                "mysql",
                "users",
                "appdb",
                "OPTIMIZE TABLE `appdb`.`users`",
                400_000_000L,
                null,
                List.of(new DiagnosticRecommendation("critical", "locks table", null, null, Map.of(), null))
            )));
        when(mysqlProvider.optimizeTable(any(), anyString(), eq("users"), eq("appdb"), eq("appdb")))
            .thenReturn(DiagnosticResult.ok(new OptimizeTableResult(true, "users", "appdb", 10L, 200_000_000L, "ok")));

        Map<String, Object> space = (Map<String, Object>) tableSpaceAction.handle(actionContext, Map.of()).toCompletableFuture().get();
        Map<String, Object> recommendation = (Map<String, Object>) ((List<?>) space.get("recommendations")).get(0);

        assertThat(recommendation).containsEntry("suggestedToolName", "datatalk_optimize_table");
        Map<String, Object> args = (Map<String, Object>) recommendation.get("suggestedActionArgs");
        assertThat(args).containsEntry("table", "users");

        var previewInput = new LinkedHashMap<String, Object>(args);
        previewInput.put("confirm", false);
        Map<String, Object> preview = (Map<String, Object>) optimizeAction.handle(actionContext, previewInput).toCompletableFuture().get();
        assertThat(preview).containsEntry("confirm_required", true);
        assertThat((List<?>) preview.get("recommendations")).hasSize(1);

        var confirmInput = new LinkedHashMap<String, Object>(args);
        confirmInput.put("confirm", true);
        confirmInput.put("confirmationToken", preview.get("confirmation_token"));
        Map<String, Object> executed = (Map<String, Object>) optimizeAction.handle(actionContext, confirmInput).toCompletableFuture().get();
        assertThat(executed).containsEntry("ok", true).containsEntry("reclaimedBytes", 200_000_000L);
    }
}
