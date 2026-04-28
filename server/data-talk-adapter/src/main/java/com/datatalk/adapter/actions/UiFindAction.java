package com.datatalk.adapter.actions;

import com.datatalk.application.stage.StageFindQuery;
import com.datatalk.application.stage.StageFindResult;
import com.datatalk.application.stage.StageFindService;
import com.datatalk.application.stage.StageFindInvalidPatternException;
import com.datatalk.application.stage.StageTabPayloadTooLargeException;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
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
            try {
                StageFindQuery query = parse(input);
                StageFindResult result = findService.execute(query);
                return toEnvelope(result);
            } catch (StageFindInvalidPatternException e) {
                return error("invalid_pattern", e.getMessage());
            } catch (StageTabPayloadTooLargeException e) {
                return error("payload_too_large", e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static StageFindQuery parse(Map<String, Object> input) {
        Map<String, Object> outputMap = input.get("output") instanceof Map<?, ?> rawOutput
            ? (Map<String, Object>) rawOutput
            : Map.of();
        String modeStr = (String) outputMap.getOrDefault("mode", input.getOrDefault("outputMode", "metadata"));
        StageFindQuery.OutputMode outputMode = switch (modeStr) {
            case "count" -> StageFindQuery.OutputMode.COUNT;
            case "matches" -> StageFindQuery.OutputMode.MATCHES;
            case "content" -> StageFindQuery.OutputMode.MATCHES;
            case "tabs_only" -> StageFindQuery.OutputMode.TABS_ONLY;
            case "read" -> StageFindQuery.OutputMode.READ;
            default -> StageFindQuery.OutputMode.METADATA;
        };
        int headLimit = outputMap.get("headLimit") instanceof Number n ? n.intValue() : 100;
        int maxTabs = outputMap.get("maxTabs") instanceof Number n ? n.intValue() : headLimit;

        Map<String, Object> filterMap = input.get("filter") instanceof Map<?, ?> rawFilter
            ? (Map<String, Object>) rawFilter
            : Map.of();
        StageFindQuery.Filter filter = new StageFindQuery.Filter(
            (String) filterMap.get("type"),
            (String) filterMap.get("connectionId"),
            (String) filterMap.get("objectId"),
            (String) filterMap.get("originSessionId"),
            filterMap.get("includeArchived") instanceof Boolean b ? b : false,
            filterMap.get("pinned") instanceof Boolean p ? p : null,
            filterMap.get("lastTouchedAfter") instanceof Number n ? n.longValue() : null,
            filterMap.get("lastTouchedBefore") instanceof Number n ? n.longValue() : null,
            filterMap.get("limit") instanceof Number n ? n.intValue() : maxTabs
        );

        StageFindQuery.ContentQuery contentQuery = null;
        Object rawQuery = input.containsKey("query") ? input.get("query") : input.get("contentQuery");
        if (rawQuery instanceof Map<?, ?> rawCq) {
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
                filter != null ? filter.includeArchived() : (cqMap.get("includeArchived") instanceof Boolean b ? b : null),
                cqMap.get("limit") instanceof Number n ? n.intValue() : headLimit,
                mode,
                cqMap.get("caseInsensitive") instanceof Boolean b ? b : true,
                cqMap.get("multiline") instanceof Boolean b ? b : false
            );
        }

        List<StageFindQuery.Read> reads = parseReads(input);

        return new StageFindQuery(outputMode, filter, contentQuery, reads);
    }

    @SuppressWarnings("unchecked")
    private static List<StageFindQuery.Read> parseReads(Map<String, Object> input) {
        if (input.get("read") instanceof Map<?, ?> rawRead) {
            Map<String, Object> readMap = (Map<String, Object>) rawRead;
            StageFindQuery.ReadRange range = parseRange(readMap.get("range"));
            int contextLines = readMap.get("contextLines") instanceof Number n ? Math.min(20, Math.max(0, n.intValue())) : 0;
            Object ids = readMap.get("tabIds");
            if (!(ids instanceof List<?> rawIds)) {
                boolean autoReadMatches = input.containsKey("query") || input.containsKey("contentQuery");
                return autoReadMatches
                    ? List.of(new StageFindQuery.Read(null, true, range, contextLines))
                    : List.of();
            }
            return rawIds.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .limit(200)
                .map(id -> new StageFindQuery.Read(id, true, range, contextLines))
                .toList();
        }
        if (input.get("reads") instanceof List<?> rawReads) {
            return rawReads.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> new StageFindQuery.Read((String) m.get("tabId"), true))
                .toList();
        }
        return List.of();
    }

    private static StageFindQuery.ReadRange parseRange(Object rawRange) {
        if (rawRange instanceof Map<?, ?> rawMap) {
            Object start = rawMap.get("lineStart");
            Object end = rawMap.get("lineEnd");
            if (start instanceof Number s && end instanceof Number e) {
                return new StageFindQuery.ReadRange.LineRange(s.intValue(), e.intValue());
            }
        }
        return StageFindQuery.ReadRange.FULL;
    }

    public static Map<String, Object> toEnvelope(StageFindResult result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        switch (result.outputMode()) {
            case COUNT -> {
                envelope.put("totalMatched", result.totalMatched());
                envelope.put("tabsMatched", result.tabsMatched());
            }
            case TABS_ONLY -> {
                envelope.put("tabIds", result.tabIds());
                envelope.put("totalMatched", result.totalMatched());
                envelope.put("truncated", result.truncated());
            }
            default -> {
                envelope.put("items", result.items());
                envelope.put("totalMatched", result.totalMatched());
                envelope.put("truncated", result.truncated());
            }
        }
        if (!result.reads().isEmpty()) {
            envelope.put("reads", result.reads().stream().map(UiFindAction::readToMap).toList());
        }
        if (!result.warnings().isEmpty()) {
            envelope.put("warnings", result.warnings());
        }
        return envelope;
    }

    private static Map<String, Object> readToMap(com.datatalk.domain.stage.StageTabContent content) {
        return Map.of(
            "tabId", content.tabId(),
            "payloadVersion", content.contentVersion(),
            "range", "full",
            "content", content.contentText(),
            "totalLines", content.contentText().split("\\R", -1).length
        );
    }

    private static Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }
}
