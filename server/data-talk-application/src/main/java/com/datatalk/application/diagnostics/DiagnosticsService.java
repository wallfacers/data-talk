package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
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

    public DiagnosticsService(DiagnosticsProviderRegistry registry,
                               ConnectionRepository connRepo,
                               ConnectionService connSvc,
                               SessionDataContextService sessionContexts) {
        this.registry = registry;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionContexts = sessionContexts;
    }

    public DiagnosticResult<ExplainPlan> explain(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.EXPLAIN)) {
            return DiagnosticResult.unsupported("EXPLAIN not supported for dialect: " + ctx.connection().kind());
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.explain(sql, ctx.connection(), pwd, ctx.database(), ctx.schema());
    }

    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.INDEX_HINTS)) {
            return DiagnosticResult.unsupported("Index hints not supported for dialect: " + ctx.connection().kind());
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        var explainResult = provider.explain(sql, ctx.connection(), pwd, ctx.database(), ctx.schema());
        if (!explainResult.isOk()) return DiagnosticResult.unsupported("EXPLAIN failed, cannot compute index hints");
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();
        return provider.indexHints(sql, plan, ctx.connection(), pwd);
    }

    private DiagnosticsProvider requireProvider(String driverType) {
        return registry.find(driverType)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "No diagnostics provider for dialect: " + driverType, false));
    }

    private ResolvedExecutionContext resolveContext(String sessionId) {
        var sessionCtx = sessionContexts.get(sessionId);
        String connectionId = sessionCtx.connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING, "No active connection in session", false);
        }
        ConnectionRecord conn = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "Connection not found: " + connectionId, false));
        return new ResolvedExecutionContext(conn, sessionCtx.databaseName(), sessionCtx.schemaName());
    }
}
