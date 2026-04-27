package com.datatalk.adapter.actions;

import com.datatalk.application.stage.StageFindQuery;
import com.datatalk.application.stage.StageFindResult;
import com.datatalk.application.stage.StageFindService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.stage.StageTabScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.ui.find",
    executor = Executor.SERVER,
    description = "action.ui_find.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.UI },
    exposeToMcp = true
)
public class UiFindAction implements ActionHandler<Map, Map> {

    private final StageFindService findService;

    public UiFindAction(StageFindService findService) {
        this.findService = findService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return UiFindSchemas.INPUT_SCHEMA;
    }

    @Override
    public Map<String, Object> outputSchema() {
        return UiFindSchemas.OUTPUT_SCHEMA;
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
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            StageFindQuery query = parse(input);
            StageFindResult result = findService.execute(query);
            return toEnvelope(result);
        });
    }

    @SuppressWarnings("unchecked")
    public static StageFindQuery parse(Map<String, Object> input) {
        String modeStr = (String) input.getOrDefault("outputMode", "metadata");
        StageFindQuery.OutputMode outputMode = switch (modeStr) {
            case "count" -> StageFindQuery.OutputMode.COUNT;
            case "content" -> StageFindQuery.OutputMode.CONTENT;
            case "tabs_only" -> StageFindQuery.OutputMode.TABS_ONLY;
            case "read" -> StageFindQuery.OutputMode.READ;
            default -> StageFindQuery.OutputMode.METADATA;
        };

        StageFindQuery.Filter filter = null;
        if (input.get("filter") instanceof Map<?, ?> rawFilter) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filterMap = (Map<String, Object>) rawFilter;
            filter = new StageFindQuery.Filter(
                filterMap.get("scope") instanceof String s ? StageTabScope.fromWire(s) : null,
                (String) filterMap.get("type"),
                (String) filterMap.get("connectionId"),
                (String) filterMap.get("originSessionId"),
                filterMap.get("includeArchived") instanceof Boolean b ? b : null,
                filterMap.get("pinned") instanceof Boolean p ? p : null,
                filterMap.get("lastTouchedAfter") instanceof Number n ? n.longValue() : null,
                filterMap.get("lastTouchedBefore") instanceof Number n ? n.longValue() : null,
                filterMap.get("limit") instanceof Number n ? n.intValue() : null
            );
        }

        StageFindQuery.ContentQuery contentQuery = null;
        if (input.get("contentQuery") instanceof Map<?, ?> rawCq) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cqMap = (Map<String, Object>) rawCq;
            String cqModeStr = (String) cqMap.getOrDefault("mode", "fts");
            StageFindQuery.ContentQuery.SearchMode mode = switch (cqModeStr) {
                case "substring" -> StageFindQuery.ContentQuery.SearchMode.SUBSTRING;
                case "regex" -> StageFindQuery.ContentQuery.SearchMode.REGEX;
                default -> StageFindQuery.ContentQuery.SearchMode.FTS;
            };
            contentQuery = new StageFindQuery.ContentQuery(
                (String) cqMap.get("pattern"),
                cqMap.get("includeArchived") instanceof Boolean b ? b : null,
                cqMap.get("limit") instanceof Number n ? n.intValue() : null,
                mode
            );
        }

        return new StageFindQuery(outputMode, filter, contentQuery, List.of());
    }

    public static Map<String, Object> toEnvelope(StageFindResult result) {
        return Map.of(
            "outputMode", result.outputMode().name().toLowerCase(),
            "items", result.items(),
            "tabIds", result.tabIds(),
            "totalMatched", result.totalMatched(),
            "tabsMatched", result.tabsMatched(),
            "truncated", result.truncated(),
            "warnings", result.warnings()
        );
    }
}
