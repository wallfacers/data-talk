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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DiagnosticsService {

    private final DiagnosticsProviderRegistry registry;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;
    private final DiagnosticsThresholdProperties thresholds;

    public DiagnosticsService(DiagnosticsProviderRegistry registry,
                               ConnectionRepository connRepo,
                               ConnectionService connSvc,
                               SessionDataContextService sessionContexts,
                               Translator translator,
                               DiagnosticsThresholdProperties thresholds) {
        this.registry = registry;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
        this.thresholds = thresholds;
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

    public DiagnosticResult<LockReport> lockInfo(String sessionId) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.LOCK_INFO)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "lock"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        var result = provider.lockInfo(ctx.connection(), pwd, ctx.database());
        return switch (result) {
            case DiagnosticResult.Ok<LockReport> ok -> {
                var report = ok.value();
                yield DiagnosticResult.ok(new LockReport(
                    report.blockingChain(),
                    lockRecommendations(report.blockingChain())
                ));
            }
            case DiagnosticResult.Unsupported<LockReport> unsupported -> DiagnosticResult.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<LockReport> err -> DiagnosticResult.error(err.errorType(), err.message());
        };
    }

    public DiagnosticResult<PoolReport> poolStatus(String sessionId) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.POOL_STATUS)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "pool"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        var result = provider.poolStatus(ctx.connection(), pwd);
        return switch (result) {
            case DiagnosticResult.Ok<PoolReport> ok -> {
                var report = ok.value();
                yield DiagnosticResult.ok(new PoolReport(
                    report.scope(),
                    report.activeConnections(),
                    report.idleConnections(),
                    report.maxConnections(),
                    report.threadsRunning(),
                    report.waitingConnections(),
                    report.identifier(),
                    poolRecommendations(report)
                ));
            }
            case DiagnosticResult.Unsupported<PoolReport> unsupported -> DiagnosticResult.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<PoolReport> err -> DiagnosticResult.error(err.errorType(), err.message());
        };
    }

    public DiagnosticResult<SpaceReport> tableSpaceInfo(String sessionId, List<String> tables) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.TABLE_SPACE)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "space"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        var result = provider.tableSpaceInfo(ctx.connection(), pwd, ctx.database(), tables);
        return switch (result) {
            case DiagnosticResult.Ok<SpaceReport> ok -> {
                var report = ok.value();
                yield DiagnosticResult.ok(new SpaceReport(
                    report.tables(),
                    spaceRecommendations(report.tables())
                ));
            }
            case DiagnosticResult.Unsupported<SpaceReport> unsupported -> DiagnosticResult.unsupported(unsupported.reason());
            case DiagnosticResult.DiagnosticError<SpaceReport> err -> DiagnosticResult.error(err.errorType(), err.message());
        };
    }

    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(String sessionId, String targetSessionId) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.TERMINATE_SESSION)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "terminate"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.terminateSessionPreview(ctx.connection(), pwd, targetSessionId, ctx.database());
    }

    public DiagnosticResult<TerminateSessionResult> terminateSession(String sessionId, String targetSessionId) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.TERMINATE_SESSION)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "terminate"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.terminateSession(ctx.connection(), pwd, targetSessionId, ctx.database());
    }

    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(String sessionId, String table, String schemaName) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.OPTIMIZE_TABLE)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "optimize"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.optimizeTablePreview(ctx.connection(), pwd, table, schemaOrContext(schemaName, ctx.schema()), ctx.database());
    }

    public DiagnosticResult<OptimizeTableResult> optimizeTable(String sessionId, String table, String schemaName) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.OPTIMIZE_TABLE)) {
            return DiagnosticResult.unsupported(unsupportedReason(ctx.connection().kind(), "optimize"));
        }
        String pwd = connSvc.decryptPassword(ctx.connection().id());
        return provider.optimizeTable(ctx.connection(), pwd, table, schemaOrContext(schemaName, ctx.schema()), ctx.database());
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

    private List<DiagnosticRecommendation> lockRecommendations(List<LockReport.LockEntry> chain) {
        List<DiagnosticRecommendation> recommendations = new ArrayList<>();
        if (chain == null) return recommendations;
        for (var entry : chain) {
            Long waitMillis = entry.waitMillis();
            if (waitMillis != null && waitMillis > thresholds.lock().longWaitMs()) {
                recommendations.add(new DiagnosticRecommendation(
                    "warning",
                    translator.get("diagnostics.lock.recommendation.terminate", entry.holderId(), String.valueOf(waitMillis / 1000)),
                    "datatalk.terminate_session",
                    "datatalk_terminate_session",
                    Map.of("sessionId", entry.holderId()),
                    null
                ));
            }
        }
        return recommendations;
    }

    private List<DiagnosticRecommendation> poolRecommendations(PoolReport report) {
        if (report.activeConnections() == null || report.maxConnections() == null || report.maxConnections() <= 0) {
            return List.of();
        }
        double ratio = (double) report.activeConnections() / report.maxConnections();
        if (ratio > thresholds.pool().criticalRatio()) {
            return List.of(new DiagnosticRecommendation(
                "critical",
                translator.get("diagnostics.pool.recommendation.high_usage_critical", percent(ratio)),
                null,
                null,
                Map.of(),
                null
            ));
        }
        if (ratio > thresholds.pool().warnRatio()) {
            return List.of(new DiagnosticRecommendation(
                "warning",
                translator.get("diagnostics.pool.recommendation.high_usage_warning", percent(ratio)),
                null,
                null,
                Map.of(),
                null
            ));
        }
        return List.of();
    }

    private List<DiagnosticRecommendation> spaceRecommendations(List<SpaceReport.TableSpaceEntry> tables) {
        List<DiagnosticRecommendation> recommendations = new ArrayList<>();
        if (tables == null) return recommendations;
        for (var table : tables) {
            Long free = table.freeSpaceBytes();
            long total = table.dataSizeBytes() + table.indexSizeBytes();
            if (free == null || total <= 0 || table.dataSizeBytes() <= thresholds.space().minDataSizeBytes()) {
                continue;
            }
            double ratio = (double) free / total;
            if (ratio > thresholds.space().reclaimRatio()) {
                var args = new LinkedHashMap<String, Object>();
                args.put("table", table.table());
                args.put("schemaName", table.schemaName());
                recommendations.add(new DiagnosticRecommendation(
                    "info",
                    translator.get(
                        "diagnostics.space.recommendation.optimize_table",
                        displayTable(table),
                        percent(ratio),
                        bytes(free)
                    ),
                    "datatalk.optimize_table",
                    "datatalk_optimize_table",
                    args,
                    null
                ));
            }
        }
        return recommendations;
    }

    private String unsupportedReason(String kind, String capability) {
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        return switch (capability) {
            case "lock" -> switch (normalized) {
                case "h2" -> translator.get("diagnostics.lock.unsupported.h2");
                case "oracle" -> translator.get("diagnostics.lock.unsupported.oracle");
                default -> translator.get("diagnostics.lock_not_supported", kind);
            };
            case "pool" -> switch (normalized) {
                case "h2" -> translator.get("diagnostics.pool.unsupported.h2_embedded");
                case "oracle" -> translator.get("diagnostics.pool.unsupported.oracle");
                default -> translator.get("diagnostics.pool_not_supported", kind);
            };
            case "space" -> switch (normalized) {
                case "oracle" -> translator.get("diagnostics.space.unsupported.oracle");
                default -> translator.get("diagnostics.tablespace_not_supported", kind);
            };
            case "terminate" -> switch (normalized) {
                case "h2" -> translator.get("diagnostics.terminate.unsupported.h2");
                case "oracle" -> translator.get("diagnostics.terminate.unsupported.oracle");
                default -> translator.get("diagnostics.terminate.unsupported.oracle");
            };
            case "optimize" -> switch (normalized) {
                case "h2" -> translator.get("diagnostics.optimize.unsupported.h2");
                case "oracle" -> translator.get("diagnostics.optimize.unsupported.oracle");
                default -> translator.get("diagnostics.optimize.unsupported.oracle");
            };
            default -> translator.get("diagnostics.no_provider", kind);
        };
    }

    private static String percent(double ratio) {
        return String.valueOf(Math.round(ratio * 100));
    }

    private static String bytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format(Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L) return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private static String displayTable(SpaceReport.TableSpaceEntry table) {
        if (table.schemaName() == null || table.schemaName().isBlank()) {
            return table.table();
        }
        return table.schemaName() + "." + table.table();
    }

    private static String schemaOrContext(String schemaName, String contextSchema) {
        if (schemaName != null && !schemaName.isBlank()) return schemaName;
        return contextSchema;
    }
}
