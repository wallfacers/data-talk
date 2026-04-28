package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.diagnostics.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiagnosticsService {

    private final DiagnosticsProviderRegistry registry;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;

    public DiagnosticsService(DiagnosticsProviderRegistry registry,
                               ConnectionRepository connRepo,
                               ConnectionService connSvc,
                               SessionDataContextService sessionContexts,
                               Translator translator) {
        this.registry = registry;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
    }

    public DiagnosticResult<ExplainPlan> explain(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.EXPLAIN)) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.explain_unsupported", ctx.connection().kind()));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.explain(sql, ctx.connection(), pwd, ctx.database(), ctx.schema());
    }

    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.INDEX_HINTS)) {
            return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints_unsupported", ctx.connection().kind()));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        var explainResult = provider.explain(sql, ctx.connection(), pwd, ctx.database(), ctx.schema());
        return switch (explainResult) {
            case DiagnosticResult.Ok<ExplainPlan> ok ->
                provider.indexHints(sql, ok.value(), ctx.connection(), pwd);
            case DiagnosticResult.Unsupported<ExplainPlan> unsupported ->
                DiagnosticResult.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<ExplainPlan> err ->
                DiagnosticResult.error(err.errorType(), err.message());
        };
    }

    private DiagnosticsProvider requireProvider(String driverType) {
        return registry.find(driverType)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                translator.get("diagnostics.no_provider", driverType), false));
    }

    private ResolvedExecutionContext resolveContext(String sessionId) {
        var sessionCtx = sessionContexts.get(sessionId);
        String connectionId = sessionCtx.connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING, translator.get("diagnostics.no_active_session"), false);
        }
        ConnectionRecord conn = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                translator.get("diagnostics.connection_not_found", connectionId), false));
        return new ResolvedExecutionContext(conn, sessionCtx.databaseName(), sessionCtx.schemaName());
    }
}
