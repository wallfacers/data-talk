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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OptimizeTableConfirmableActionTest {

    private final DiagnosticsService service = mock(DiagnosticsService.class);
    private final OptimizeTableConfirmableAction action = new OptimizeTableConfirmableAction(service, translator());
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @Test
    void phase1PreviewReturnsTokenPreviewAndTopLevelRecommendations() {
        when(service.optimizeTablePreview("s1", "users", null)).thenReturn(DiagnosticResult.ok(preview()));

        Map out = action.handle(ctx, Map.of("table", "users", "confirm", false)).toCompletableFuture().join();

        assertThat(out).containsEntry("confirm_required", true);
        assertThat(out.get("confirmation_token")).isInstanceOf(String.class);
        assertThat(((Map<?, ?>) out.get("preview")).get("willRunSql")).isEqualTo("OPTIMIZE TABLE `db`.`users`");
        assertThat((List<?>) out.get("recommendations")).hasSize(1);
    }

    @Test
    void phase2ValidTokenExecutes() {
        when(service.optimizeTablePreview("s1", "users", null)).thenReturn(DiagnosticResult.ok(preview()));
        when(service.optimizeTable("s1", "users", null))
            .thenReturn(DiagnosticResult.ok(new OptimizeTableResult(true, "users", "db", 10L, 8L, "ok")));
        Map phase1 = action.handle(ctx, Map.of("table", "users")).toCompletableFuture().join();

        Map out = action.handle(ctx, Map.of(
            "table", "users",
            "confirm", true,
            "confirmationToken", phase1.get("confirmation_token")
        )).toCompletableFuture().join();

        assertThat(out).containsEntry("ok", true)
            .containsEntry("table", "users")
            .containsEntry("schemaName", "db")
            .containsEntry("reclaimedBytes", 8L);
    }

    @Test
    void missingOrMismatchedTokenThrows() {
        when(service.optimizeTablePreview("s1", "users", null)).thenReturn(DiagnosticResult.ok(preview()));

        assertThatThrownBy(() -> action.handle(ctx, Map.of("table", "users", "confirm", true)).toCompletableFuture().join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("confirmationToken is required when confirm=true");

        assertThatThrownBy(() -> action.handle(ctx, Map.of("table", "users", "confirm", true, "confirmationToken", "wrong")).toCompletableFuture().join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("confirmationToken does not match preview");
    }

    @Test
    void invalidIdentifiersReturnInvalidInputErrors() {
        Map invalidTable = action.handle(ctx, Map.of("table", "bad-name")).toCompletableFuture().join();
        assertThat(((Map<?, ?>) invalidTable.get("error")).get("type")).isEqualTo("INVALID_INPUT");

        Map invalidSchema = action.handle(ctx, Map.of("table", "users", "schemaName", "bad-name")).toCompletableFuture().join();
        assertThat(((Map<?, ?>) invalidSchema.get("error")).get("type")).isEqualTo("INVALID_INPUT");
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedAndErrorSerialize() {
        when(service.optimizeTablePreview("s1", "users", null)).thenReturn(DiagnosticResult.unsupported("unsupported"));
        assertThat(action.handle(ctx, Map.of("table", "users")).toCompletableFuture().join()).containsEntry("unsupported", true);

        when(service.optimizeTablePreview("s1", "users", null)).thenReturn(DiagnosticResult.error("X", "msg"));
        Map out = action.handle(ctx, Map.of("table", "users")).toCompletableFuture().join();
        Map<?, ?> error = (Map<?, ?>) out.get("error");
        assertThat(error.get("type")).isEqualTo("X");
        assertThat(error.get("message")).isEqualTo("msg");
    }

    private OptimizeTablePreview preview() {
        return new OptimizeTablePreview(
            "mysql",
            "users",
            "db",
            "OPTIMIZE TABLE `db`.`users`",
            100L,
            null,
            List.of(new DiagnosticRecommendation("critical", "locks table", null, null, Map.of(), null))
        );
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.error.invalid_table_name", Locale.ENGLISH, "Invalid table name format");
        source.addMessage("diagnostics.error.invalid_schema_name", Locale.ENGLISH, "Invalid schema name format");
        source.addMessage("diagnostics.error.confirmation_token_required", Locale.ENGLISH, "confirmationToken is required when confirm=true");
        source.addMessage("diagnostics.error.confirmation_token_mismatch", Locale.ENGLISH, "confirmationToken does not match preview");
        return new Translator(source);
    }
}
