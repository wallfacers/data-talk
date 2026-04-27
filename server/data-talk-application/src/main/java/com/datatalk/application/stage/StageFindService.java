package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Service for the ui_find action. Supports metadata listing, count,
 * content search (FTS/substring/regex), and read modes.
 */
@Service
public class StageFindService {

    private static final int MAX_REGEX_LENGTH = 200;
    private static final long PER_TAB_TIMEOUT_SECONDS = 2;

    private final StageTabRepository repo;
    private final StageTabIndexerPort indexer;

    public StageFindService(StageTabRepository repo, StageTabIndexerPort indexer) {
        this.repo = repo;
        this.indexer = indexer;
    }

    public StageFindResult execute(StageFindQuery query) {
        StageFindResult base;
        if (query.contentQuery() != null && query.contentQuery().pattern() != null) {
            base = executeContentSearch(query);
        } else {
            base = switch (query.outputMode()) {
                case METADATA -> executeMetadata(query.filter());
                case COUNT -> executeCount(query.filter());
                case TABS_ONLY -> executeTabsOnly(query.filter());
                case CONTENT -> throw new UnsupportedOperationException(
                    "Content search mode requires a contentQuery with a pattern");
                case READ -> executeRead(query);
            };
        }

        return base;
    }

    // ---- Content search (FTS + substring/regex post-filter) ----

    private StageFindResult executeContentSearch(StageFindQuery query) {
        var cq = query.contentQuery();
        String pattern = cq.pattern();
        boolean includeArchived = cq.includeArchived() != null ? cq.includeArchived() : false;
        int queryLimit = cq.limit() != null ? cq.limit() : 100;
        var mode = cq.mode() != null ? cq.mode() : StageFindQuery.ContentQuery.SearchMode.FTS;

        var matchesByTab = new LinkedHashMap<String, List<Map<String, Object>>>();

        switch (mode) {
            case FTS, SUBSTRING -> {
                int candidateLimit = Math.max(queryLimit * 4, 200);
                var rowids = indexer.ftsMatch(pattern, includeArchived, candidateLimit);
                var ids = indexer.rowidsToIds(rowids.stream().map(StageTabIndexerPort.RowidScore::rowid).toList());
                var idsToCheck = ids.stream().limit(queryLimit).toList();

                var contents = repo.findContents(idsToCheck);
                var contentById = contents.stream()
                    .collect(Collectors.toMap(StageTabContent::tabId, Function.identity()));

                for (var id : idsToCheck) {
                    var c = contentById.get(id);
                    if (c == null) continue;
                    var lines = substringPostFilter(pattern, c.contentText());
                    if (!lines.isEmpty()) {
                        matchesByTab.put(id, lines);
                    }
                }
            }
            case REGEX -> {
                var warnings = new ArrayList<String>();
                validateRegexPattern(pattern);

                // For regex, list all tabs and fan-out with virtual threads
                StageTabRepository.ListFilter listFilter = new StageTabRepository.ListFilter(
                    null, null, null, null, includeArchived, null, null, null, queryLimit * 4
                );
                var tabs = repo.list(listFilter);
                var idsToCheck = tabs.stream().map(StageTab::id).limit(queryLimit).toList();

                var contents = repo.findContents(idsToCheck);
                var contentById = contents.stream()
                    .collect(Collectors.toMap(StageTabContent::tabId, Function.identity()));

                var results = regexFanOut(pattern, idsToCheck, contentById);
                for (var entry : results.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        matchesByTab.put(entry.getKey(), entry.getValue());
                    }
                }
                warnings.addAll(results.values().stream()
                    .flatMap(List::stream)
                    .filter(m -> m.containsKey("_warning"))
                    .map(m -> (String) m.get("_warning"))
                    .toList());
            }
        }

        int totalMatches = matchesByTab.values().stream().mapToInt(List::size).sum();
        boolean truncated = matchesByTab.size() > queryLimit;

        return switch (query.outputMode()) {
            case METADATA -> {
                var items = matchesByTab.keySet().stream()
                    .map(id -> repo.findById(id))
                    .filter(Optional::isPresent)
                    .map(opt -> tabToMap(opt.get()))
                    .toList();
                yield StageFindResult.metadata(items, matchesByTab.size(), truncated);
            }
            case COUNT -> StageFindResult.count(totalMatches);
            case CONTENT, TABS_ONLY -> {
                var tabIds = List.copyOf(matchesByTab.keySet());
                yield StageFindResult.tabsOnly(tabIds, totalMatches, truncated);
            }
            case READ -> {
                var tabIds = List.copyOf(matchesByTab.keySet());
                yield new StageFindResult(
                    StageFindQuery.OutputMode.READ, List.of(), tabIds,
                    totalMatches, matchesByTab.size(), truncated,
                    List.of(), List.of());
            }
        };
    }

    // ---- Regex support ----

    private void validateRegexPattern(String pattern) {
        if (pattern.length() > MAX_REGEX_LENGTH) {
            throw new StageFindInvalidPatternException(pattern,
                "pattern exceeds maximum length of " + MAX_REGEX_LENGTH + " characters (got " + pattern.length() + ")");
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new StageFindInvalidPatternException(pattern, e);
        }
    }

    /**
     * Fan-out regex matching across tabs using virtual threads.
     * Each tab gets its own virtual thread with a per-tab timeout.
     */
    private Map<String, List<Map<String, Object>>> regexFanOut(
            String pattern,
            List<String> tabIds,
            Map<String, StageTabContent> contentById) {

        var compiled = Pattern.compile(pattern);
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Map.Entry<String, List<Map<String, Object>>>>>();
            for (var tabId : tabIds) {
                var content = contentById.get(tabId);
                if (content == null) continue;
                futures.add(executor.submit(() -> Map.entry(tabId, regexMatchOne(compiled, content.contentText()))));
            }

            for (var future : futures) {
                try {
                    var entry = future.get(PER_TAB_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    result.put(entry.getKey(), entry.getValue());
                } catch (TimeoutException e) {
                    // Tab timed out — skip it
                } catch (Exception e) {
                    // Other error — skip
                }
            }
        }

        return result;
    }

    private static List<Map<String, Object>> regexMatchOne(Pattern pattern, String content) {
        var result = new ArrayList<Map<String, Object>>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lineNumber", i + 1);
                m.put("line", lines[i]);
                m.put("columnStart", matcher.start() + 1);
                m.put("columnEnd", matcher.end() + 1);
                result.add(m);
            }
        }
        return result;
    }

    // ---- Substring post-filter ----

    private static List<Map<String, Object>> substringPostFilter(String pattern, String content) {
        return firstMatchPerLine(content, pattern);
    }

    private static List<Map<String, Object>> firstMatchPerLine(String content, String pattern) {
        var result = new ArrayList<Map<String, Object>>();
        String[] lines = content.split("\n");
        String lowerPattern = pattern.toLowerCase();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int idx = line.toLowerCase().indexOf(lowerPattern);
            if (idx >= 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lineNumber", i + 1);
                m.put("line", line);
                m.put("columnStart", idx + 1);
                m.put("columnEnd", idx + pattern.length());
                result.add(m);
            }
        }
        return result;
    }

    // ---- Metadata-only modes ----

    private StageFindResult executeMetadata(StageFindQuery.Filter filter) {
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        List<Map<String, Object>> items = tabs.stream().map(this::tabToMap).toList();
        boolean truncated = tabs.size() >= listFilter.limit();
        return StageFindResult.metadata(items, tabs.size(), truncated);
    }

    private StageFindResult executeCount(StageFindQuery.Filter filter) {
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        return StageFindResult.count(tabs.size());
    }

    private StageFindResult executeTabsOnly(StageFindQuery.Filter filter) {
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        List<String> tabIds = tabs.stream().map(StageTab::id).toList();
        boolean truncated = tabs.size() >= listFilter.limit();
        return StageFindResult.tabsOnly(tabIds, tabs.size(), truncated);
    }

    private StageFindResult executeRead(StageFindQuery query) {
        if (query.reads() == null || query.reads().isEmpty()) {
            return new StageFindResult(
                StageFindQuery.OutputMode.READ, List.of(), List.of(),
                0, 0, false, List.of(), List.of());
        }
        var reads = query.reads().stream()
            .map(r -> repo.findContent(r.tabId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        return new StageFindResult(
            StageFindQuery.OutputMode.READ, List.of(),
            reads.stream().map(StageTabContent::tabId).toList(),
            reads.size(), reads.size(), false, reads, List.of());
    }

    private StageTabRepository.ListFilter toListFilter(StageFindQuery.Filter filter) {
        if (filter == null) {
            return StageTabRepository.ListFilter.defaultFilter();
        }
        return new StageTabRepository.ListFilter(
            filter.scope(),
            filter.type(),
            filter.connectionId(),
            filter.originSessionId(),
            filter.includeArchived() != null ? filter.includeArchived() : false,
            filter.pinned(),
            filter.lastTouchedAfter(),
            filter.lastTouchedBefore(),
            filter.limit() != null ? filter.limit() : 100
        );
    }

    private Map<String, Object> tabToMap(StageTab tab) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tab.id());
        m.put("type", tab.type());
        m.put("scope", tab.scope().wire());
        m.put("title", tab.title());
        if (tab.connectionId() != null) m.put("connectionId", tab.connectionId());
        if (tab.databaseName() != null) m.put("databaseName", tab.databaseName());
        if (tab.schemaName() != null) m.put("schemaName", tab.schemaName());
        if (tab.originSessionId() != null) m.put("originSessionId", tab.originSessionId());
        m.put("pinned", tab.pinned());
        m.put("archived", tab.archived());
        m.put("createdAt", tab.createdAt());
        m.put("lastTouchedAt", tab.lastTouchedAt());
        return m;
    }
}
