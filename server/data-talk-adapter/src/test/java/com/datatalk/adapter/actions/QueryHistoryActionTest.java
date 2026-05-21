package com.datatalk.adapter.actions;

import com.datatalk.application.history.SqlExecutionHistoryService;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionExecutionMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryHistoryActionTest {

    private SqlExecutionHistoryService historyService;
    private QueryHistoryAction action;

    @BeforeEach
    void setUp() {
        historyService = mock(SqlExecutionHistoryService.class);
        action = new QueryHistoryAction(historyService);
    }

    private ActionContext ctx(String sessionId) {
        return new ActionContext(sessionId, "call-1", null, "oc-1", ActionExecutionMetadata.empty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ActionContext ctx, Map<String, Object> input)
            throws ExecutionException, InterruptedException {
        return (Map<String, Object>) action.handle(ctx, input).toCompletableFuture().get();
    }

    @Test
    void returnsRecentSuccessfulQueriesByDefault() throws Exception {
        when(historyService.list(any(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class)))
            .thenReturn(List.of(
                SqlExecutionRecord.success("ses_1", "c", null, null,
                    "SELECT 1", 3000L, 5L, 1),
                SqlExecutionRecord.success("ses_1", "c", null, null,
                    "SELECT 2", 2000L, 6L, 2)
            ));

        Map<String, Object> out = invoke(ctx("ses_1"), Map.of());

        assertThat(out.get("totalQueries")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queries = (List<Map<String, Object>>) out.get("queries");
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).get("sqlText")).isEqualTo("SELECT 1");
        assertThat(queries.get(0).get("status")).isEqualTo("success");

        ArgumentCaptor<SqlExecutionHistoryService.SqlExecutionHistoryQuery> cap =
            ArgumentCaptor.forClass(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class);
        verify(historyService).list(cap.capture());
        assertThat(cap.getValue().sessionId()).isEqualTo("ses_1");
        assertThat(cap.getValue().statusFilter())
            .isEqualTo(SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.SUCCESS);
    }

    @Test
    void statusFailureFilterIsPassed() throws Exception {
        when(historyService.list(any())).thenReturn(List.of(
            SqlExecutionRecord.failure("ses_1", "c", null, null,
                "SELECT x", "ERR", "boom", 1000L, 5L)
        ));

        Map<String, Object> out = invoke(ctx("ses_1"), Map.of("status", "failure"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queries = (List<Map<String, Object>>) out.get("queries");
        assertThat(queries).hasSize(1);
        assertThat(queries.get(0).get("status")).isEqualTo("failure");
        assertThat(queries.get(0).get("errorCode")).isEqualTo("ERR");

        ArgumentCaptor<SqlExecutionHistoryService.SqlExecutionHistoryQuery> cap =
            ArgumentCaptor.forClass(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class);
        verify(historyService).list(cap.capture());
        assertThat(cap.getValue().statusFilter())
            .isEqualTo(SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.FAILURE);
    }

    @Test
    void statusAllFilterIsPassed() throws Exception {
        when(historyService.list(any())).thenReturn(List.of());

        invoke(ctx("ses_1"), Map.of("status", "all"));

        ArgumentCaptor<SqlExecutionHistoryService.SqlExecutionHistoryQuery> cap =
            ArgumentCaptor.forClass(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class);
        verify(historyService).list(cap.capture());
        assertThat(cap.getValue().statusFilter())
            .isEqualTo(SqlExecutionHistoryService.SqlExecutionHistoryQuery.StatusFilter.ALL);
    }

    @Test
    void connectionIdAndDatabaseFiltersArePassed() throws Exception {
        when(historyService.list(any())).thenReturn(List.of());

        invoke(ctx("ses_1"), Map.of(
            "connectionId", "c1",
            "database", "shop"));

        ArgumentCaptor<SqlExecutionHistoryService.SqlExecutionHistoryQuery> cap =
            ArgumentCaptor.forClass(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class);
        verify(historyService).list(cap.capture());
        assertThat(cap.getValue().connectionId()).isEqualTo("c1");
        assertThat(cap.getValue().databaseName()).isEqualTo("shop");
    }

    @Test
    void limitIsClampedToFiftyMaximum() throws Exception {
        when(historyService.list(any())).thenReturn(List.of());

        invoke(ctx("ses_1"), Map.of("limit", 999));

        ArgumentCaptor<SqlExecutionHistoryService.SqlExecutionHistoryQuery> cap =
            ArgumentCaptor.forClass(SqlExecutionHistoryService.SqlExecutionHistoryQuery.class);
        verify(historyService).list(cap.capture());
        assertThat(cap.getValue().limit()).isLessThanOrEqualTo(50);
    }

    @Test
    void emptyHistoryReturnsEmptyArray() throws Exception {
        when(historyService.list(any())).thenReturn(List.of());

        Map<String, Object> out = invoke(ctx("ses_empty"), Map.of());

        assertThat(out.get("totalQueries")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<?> queries = (List<?>) out.get("queries");
        assertThat(queries).isEmpty();
    }

    @Test
    void actionDeclaresExpectedAnnotationAttributes() {
        com.datatalk.domain.action.DataTalkAction annotation =
            QueryHistoryAction.class.getAnnotation(com.datatalk.domain.action.DataTalkAction.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.id()).isEqualTo("datatalk.query_history");
        assertThat(annotation.executor()).isEqualTo(com.datatalk.domain.action.Executor.SERVER);
        assertThat(annotation.requiresConnection()).isFalse();
        assertThat(annotation.timeoutMs()).isEqualTo(3_000);
        assertThat(annotation.exposeToMcp()).isTrue();
        assertThat(annotation.category()).contains(com.datatalk.domain.action.Category.METADATA);
        assertThat(annotation.riskLevel()).contains(com.datatalk.domain.action.RiskLevel.L1);
    }

    private static <T> T any(Class<T> c) {
        return org.mockito.ArgumentMatchers.any(c);
    }

    @SuppressWarnings("unchecked")
    private static <T> T any() {
        return (T) org.mockito.ArgumentMatchers.any();
    }
}
