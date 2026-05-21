package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

@Component
@DataTalkAction(
    id = "datatalk.table_space",
    executor = Executor.SERVER,
    description = "action.table_space.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class TableSpaceAction implements ActionHandler<Map, Map> {

    static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private final DiagnosticsService diagnosticsService;
    private final Translator translator;

    public TableSpaceAction(DiagnosticsService diagnosticsService, Translator translator) {
        this.diagnosticsService = diagnosticsService;
        this.translator = translator;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "tables", Map.of("type", "array", "items", Map.of("type", "string"))
            ));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "tables", Map.of("type", "array"),
                "recommendations", Map.of("type", "array"),
                "unsupported", Map.of("type", "boolean"),
                "reason", Map.of("type", "string"),
                "error", Map.of("type", "object")
            ));
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        List<String> tables = parseTables(input);
        if (tables == null && input.get("tables") != null) {
            return CompletableFuture.completedFuture(DiagnosticsActionSupport.invalidInput(
                translator.get("diagnostics.error.invalid_table_name")
            ));
        }
        return CompletableFuture.supplyAsync(() -> {
            DiagnosticResult<SpaceReport> result = diagnosticsService.tableSpaceInfo(ctx.sessionId(), tables);
            return switch (result) {
                case DiagnosticResult.Ok<SpaceReport> ok -> serialize(ok.value());
                case DiagnosticResult.Unsupported<SpaceReport> unsupported -> DiagnosticsActionSupport.unsupported(unsupported.reason());
                case DiagnosticResult.DiagnosticError<SpaceReport> err -> DiagnosticsActionSupport.error(err.errorType(), err.message());
            };
        });
    }

    static Map<String, Object> serialize(SpaceReport report) {
        var out = new LinkedHashMap<String, Object>();
        out.put("tables", report.tables().stream().map(TableSpaceAction::serializeEntry).toList());
        out.put("recommendations", DiagnosticsActionSupport.serializeRecommendations(report.recommendations()));
        return out;
    }

    private static Map<String, Object> serializeEntry(SpaceReport.TableSpaceEntry entry) {
        var out = new LinkedHashMap<String, Object>();
        out.put("table", entry.table());
        out.put("schemaName", entry.schemaName());
        out.put("rowCount", entry.rowCount());
        out.put("dataSizeBytes", entry.dataSizeBytes());
        out.put("indexSizeBytes", entry.indexSizeBytes());
        out.put("freeSpaceBytes", entry.freeSpaceBytes());
        return out;
    }

    private static List<String> parseTables(Map input) {
        Object raw = input.get("tables");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> tables = new ArrayList<>();
        for (Object value : list) {
            String table = value == null ? "" : String.valueOf(value);
            if (!IDENTIFIER.matcher(table).matches()) {
                return null;
            }
            tables.add(table);
        }
        return tables;
    }
}
