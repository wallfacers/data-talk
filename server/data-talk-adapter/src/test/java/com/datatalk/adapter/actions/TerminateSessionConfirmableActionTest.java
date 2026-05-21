package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TerminateSessionConfirmableActionTest {

    private final DiagnosticsService service = mock(DiagnosticsService.class);
    private final TerminateSessionConfirmableAction action = new TerminateSessionConfirmableAction(service, translator());
    private final ActionContext ctx = new ActionContext("s1", "call", null, null);

    @Test
    void phase1PreviewReturnsConfirmationTokenAndPreview() {
        when(service.terminateSessionPreview("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionPreview("mysql", "42", "KILL 42", "SELECT 1")));

        Map out = action.handle(ctx, Map.of("sessionId", "42", "confirm", false)).toCompletableFuture().join();

        assertThat(out).containsEntry("confirm_required", true);
        assertThat(out.get("confirmation_token")).isInstanceOf(String.class);
        assertThat(((Map<?, ?>) out.get("preview")).get("willRunSql")).isEqualTo("KILL 42");
    }

    @Test
    void phase1UnsupportedSerializesReason() {
        when(service.terminateSessionPreview("s1", "42"))
            .thenReturn(DiagnosticResult.unsupported("self_termination_blocked"));

        Map out = action.handle(ctx, Map.of("sessionId", "42")).toCompletableFuture().join();

        assertThat(out).containsEntry("unsupported", true).containsEntry("reason", "self_termination_blocked");
    }

    @Test
    void phase2ValidTokenExecutes() {
        when(service.terminateSessionPreview("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionPreview("mysql", "42", "KILL 42", "SELECT 1")));
        when(service.terminateSession("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionResult(true, "42", "Session terminated")));
        Map preview = action.handle(ctx, Map.of("sessionId", "42")).toCompletableFuture().join();

        Map out = action.handle(ctx, Map.of(
            "sessionId", "42",
            "confirm", true,
            "confirmationToken", preview.get("confirmation_token")
        )).toCompletableFuture().join();

        assertThat(out).containsEntry("ok", true).containsEntry("sessionId", "42");
        verify(service).terminateSession("s1", "42");
    }

    @Test
    void phase2MissingOrMismatchedTokenThrows() {
        when(service.terminateSessionPreview("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionPreview("mysql", "42", "KILL 42", "SELECT 1")));

        assertThatThrownBy(() -> action.handle(ctx, Map.of("sessionId", "42", "confirm", true)).toCompletableFuture().join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("confirmationToken is required when confirm=true");

        assertThatThrownBy(() -> action.handle(ctx, Map.of("sessionId", "42", "confirm", true, "confirmationToken", "wrong")).toCompletableFuture().join())
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("confirmationToken does not match preview");
    }

    @Test
    void phase2SessionNotFoundSerializesOkFalse() {
        when(service.terminateSessionPreview("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionPreview("mysql", "42", "KILL 42", null)));
        when(service.terminateSession("s1", "42"))
            .thenReturn(DiagnosticResult.ok(new TerminateSessionResult(false, "42", "Target session no longer exists")));
        Map preview = action.handle(ctx, Map.of("sessionId", "42")).toCompletableFuture().join();

        Map out = action.handle(ctx, Map.of(
            "sessionId", "42",
            "confirm", true,
            "confirmationToken", preview.get("confirmation_token")
        )).toCompletableFuture().join();

        assertThat(out).containsEntry("ok", false).containsEntry("sessionId", "42");
        assertThat(out.get("message")).asString().contains("no longer exists");
    }

    @Test
    void invalidSessionIdReturnsInvalidInputError() {
        Map out = action.handle(ctx, Map.of("sessionId", "42;DROP")).toCompletableFuture().join();

        assertThat(((Map<?, ?>) out.get("error")).get("type")).isEqualTo("INVALID_INPUT");
        verifyNoInteractions(service);
    }

    @Test
    void commaSeparatedSessionIdReturnsInvalidInputError() {
        Map out = action.handle(ctx, Map.of("sessionId", "42,43")).toCompletableFuture().join();

        assertThat(((Map<?, ?>) out.get("error")).get("type")).isEqualTo("INVALID_INPUT");
        verifyNoInteractions(service);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.error.invalid_session_id", Locale.ENGLISH, "Invalid session id format");
        source.addMessage("diagnostics.error.confirmation_token_required", Locale.ENGLISH, "confirmationToken is required when confirm=true");
        source.addMessage("diagnostics.error.confirmation_token_mismatch", Locale.ENGLISH, "confirmationToken does not match preview");
        return new Translator(source);
    }
}
