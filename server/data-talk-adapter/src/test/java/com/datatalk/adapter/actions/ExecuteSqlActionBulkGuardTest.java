package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.QueryResultRepository;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.BulkSqlGuard;
import com.datatalk.application.sql.BulkSqlVerdict;
import com.datatalk.application.sql.SqlPendingConfirmationStore;
import com.datatalk.application.sql.SqlRiskAnalyzer;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionExecutionMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecuteSqlActionBulkGuardTest {

    private BulkSqlGuard bulkSqlGuard;
    private SqlPendingConfirmationStore confirmationStore;
    private SessionDataContextService sessionContexts;
    private ConnectionRepository connRepo;
    private ExecuteSqlAction action;

    @BeforeEach
    void setUp() {
        bulkSqlGuard = mock(BulkSqlGuard.class);
        confirmationStore = mock(SqlPendingConfirmationStore.class);
        sessionContexts = mock(SessionDataContextService.class);
        connRepo = mock(ConnectionRepository.class);

        action = new ExecuteSqlAction(
            connRepo,
            mock(ConnectionService.class),
            mock(SqlRiskAnalyzer.class),
            mock(ArtifactRepository.class),
            mock(QueryResultRepository.class),
            mock(UserPreferencesService.class),
            new ObjectMapper(),
            Clock.systemUTC(),
            mock(IdGenerator.class),
            sessionContexts,
            mock(Translator.class),
            confirmationStore,
            mock(SqlExecutionHistoryService.class),
            bulkSqlGuard
        );

        when(sessionContexts.get(anyString())).thenReturn(new SessionDataContextRecord(
            "s-1", "conn-1", "Test", "db", "schema", "schema", 0L
        ));
        when(connRepo.findById("conn-1")).thenReturn(Optional.of(new ConnectionRecord(
            "conn-1", "Test", "mysql", "host", 3306, "db", "user", new byte[0],
            null, 0L, 0, null, null, null, 0, true, null, false, null, null, null
        )));
    }

    @Test
    void aiPath_guardReject_returnsStructuredRejection() {
        Map<String, Object> nextParams = new LinkedHashMap<>();
        nextParams.put("target", Map.of("connectionId", "conn-1", "tableName", "td_orders"));
        when(bulkSqlGuard.evaluate(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn(BulkSqlVerdict.reject(
                BulkSqlGuard.REASON_SIZE,
                "SQL size exceeds limit. Use datatalk_import_data instead.",
                nextParams
            ));

        ActionContext ctx = new ActionContext("s-1", "c-1", "conn-1", "oc-1",
            ActionExecutionMetadata.aiInitiated());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) action.handle(ctx,
            Map.of("sql", "INSERT INTO td_orders VALUES (1)")
        ).toCompletableFuture().join();

        assertThat(response).containsEntry("status", "rejected");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat(error)
            .containsEntry("code", "use_import_data")
            .containsEntry("reason", BulkSqlGuard.REASON_SIZE);
        assertThat(error.get("message")).asString().contains("import_data");

        @SuppressWarnings("unchecked")
        Map<String, Object> nextAction = (Map<String, Object>) response.get("nextAction");
        assertThat(nextAction).containsEntry("action", "datatalk_import_data");
        assertThat(nextAction.get("params")).isEqualTo(nextParams);
    }

    @Test
    void aiPath_guardReject_doesNotExecuteSql() {
        when(bulkSqlGuard.evaluate(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn(BulkSqlVerdict.reject(BulkSqlGuard.REASON_INSERT_COUNT,
                "21 inserts exceed limit 20", Map.of()));

        ActionContext ctx = new ActionContext("s-1", "c-1", "conn-1", "oc-1",
            ActionExecutionMetadata.aiInitiated());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) action.handle(ctx,
            Map.of("sql", "INSERT INTO t VALUES(1);".repeat(21))
        ).toCompletableFuture().join();

        // Rejection short-circuits before any DB activity — assert response is rejection-shaped,
        // not the executed-shape (which has artifactId / rowCount / preview).
        assertThat(response).containsEntry("status", "rejected");
        assertThat(response).doesNotContainKey("artifactId");
        assertThat(response).doesNotContainKey("rowCount");
    }

    @Test
    void confirmationFlow_doesNotInvokeGuard() {
        // confirmationId present → executeConfirmation branch → guard MUST NOT be called.
        // We expect the call to fail downstream (no real DB) but the assertion is purely
        // that bulkSqlGuard.evaluate is never invoked.
        when(confirmationStore.get("conf-1")).thenReturn(Optional.empty());

        ActionContext ctx = new ActionContext("s-1", "c-1", "conn-1", "oc-1",
            ActionExecutionMetadata.aiInitiated());

        try {
            action.handle(ctx, Map.of(
                "sql", "INSERT INTO t VALUES (1)",
                "confirmationId", "conf-1"
            )).toCompletableFuture().join();
        } catch (RuntimeException ignored) {
            // confirmationStore.get returning empty leads to a "confirmation_invalid" branch,
            // not an exception — but any downstream failure is acceptable; we only verify
            // the guard was not consulted.
        }

        verify(bulkSqlGuard, never()).evaluate(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void aiPath_guardCalledWithCallerKindAi_andPropagatesSourceFileId() {
        when(bulkSqlGuard.evaluate(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn(BulkSqlVerdict.reject(BulkSqlGuard.REASON_FILE_ORIGIN,
                "from file", Map.of()));

        ActionContext ctx = new ActionContext("s-1", "c-1", "conn-1", "oc-1",
            ActionExecutionMetadata.aiInitiated());

        action.handle(ctx, Map.of(
            "sql", "INSERT INTO t VALUES (1)",
            "sourceFileId", "f-xyz"
        )).toCompletableFuture().join();

        ArgumentCaptor<String> sourceFileCaptor = ArgumentCaptor.forClass(String.class);
        verify(bulkSqlGuard).evaluate(
            anyString(),
            eq(com.datatalk.domain.action.CallerKind.AI),
            sourceFileCaptor.capture(),
            anyString(),
            anyString()
        );
        assertThat(sourceFileCaptor.getValue()).isEqualTo("f-xyz");
    }

    @Test
    void userPath_guardCalledWithCallerKindUser() {
        // Even on USER path, guard is called — but its impl is supposed to no-op.
        // Here we just verify the kind passed in is USER.
        when(bulkSqlGuard.evaluate(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn(BulkSqlVerdict.pass());

        ActionContext ctx = new ActionContext("s-1", "c-1", "conn-1", "oc-1",
            ActionExecutionMetadata.userInitiated());

        try {
            action.handle(ctx, Map.of("sql", "SELECT 1")).toCompletableFuture().join();
        } catch (RuntimeException ignored) {
            // downstream execution will fail (mock JDBC), that's fine — assertion is on guard call.
        }

        verify(bulkSqlGuard).evaluate(
            anyString(),
            eq(com.datatalk.domain.action.CallerKind.USER),
            any(),
            anyString(),
            anyString()
        );
    }
}
