package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TableSpaceActionTest {

    private final DiagnosticsService service = mock(DiagnosticsService.class);
    private final TableSpaceAction action = new TableSpaceAction(service, translator());
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @Test
    void okSerializesTablesAndRecommendations() {
        when(service.tableSpaceInfo(eq("s1"), eq(List.of("users")))).thenReturn(DiagnosticResult.ok(new SpaceReport(
            List.of(new SpaceReport.TableSpaceEntry("users", "app", 10, 900, 100, 400L)),
            List.of(new DiagnosticRecommendation("info", "optimize", "datatalk.optimize_table", "datatalk_optimize_table", Map.of("table", "users"), null))
        )));

        Map out = action.handle(ctx, Map.of("tables", List.of("users"))).toCompletableFuture().join();

        assertThat((List<?>) out.get("tables")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) out.get("tables")).get(0)).get("schemaName")).isEqualTo("app");
        assertThat((List<?>) out.get("recommendations")).hasSize(1);
    }

    @Test
    void invalidTableNameReturnsInvalidInputError() {
        Map out = action.handle(ctx, Map.of("tables", List.of("users;DROP"))).toCompletableFuture().join();

        assertThat(((Map<?, ?>) out.get("error")).get("type")).isEqualTo("INVALID_INPUT");
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedAndErrorSerialize() {
        when(service.tableSpaceInfo("s1", null)).thenReturn(DiagnosticResult.unsupported("unsupported"));
        assertThat(action.handle(ctx, Map.of()).toCompletableFuture().join()).containsEntry("unsupported", true);

        when(service.tableSpaceInfo("s1", null)).thenReturn(DiagnosticResult.error("X", "msg"));
        Map out = action.handle(ctx, Map.of()).toCompletableFuture().join();
        Map<?, ?> error = (Map<?, ?>) out.get("error");
        assertThat(error.get("type")).isEqualTo("X");
        assertThat(error.get("message")).isEqualTo("msg");
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.error.invalid_table_name", Locale.ENGLISH, "Invalid table name format");
        return new Translator(source);
    }
}
