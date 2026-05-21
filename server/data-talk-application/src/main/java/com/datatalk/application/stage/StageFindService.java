package com.datatalk.application.stage;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final int MAX_TAB_IDS = 200;
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private static final long PER_TAB_TIMEOUT_SECONDS = 2;
    static final int DEFAULT_CONTEXT_LINES = 3;

    private final StageTabRepository repo;
    private final StageTabIndexerPort indexer;
    private final SessionRepository sessions;

    private record ReadComputation(List<StageTabContent> reads, List<String> warnings) {}

    private record RegexFanOutResult(
        Map<String, List<Map<String, Object>>> matchesByTab,
        List<String> warnings
    ) {}

    public StageFindService(StageTabRepository repo, StageTabIndexerPort indexer, SessionRepository sessions) {
        this.repo = repo;
        this.indexer = indexer;
        this.sessions = sessions;
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
                case MATCHES -> throw new UnsupportedOperationException(
                    "Matches mode requires a content query with a pattern");
                case CONTENT -> throw new UnsupportedOperationException(
                    "Content search mode requires a contentQuery with a pattern");
                case READ -> executeRead(query);
            };
        }

        // Apply read on top of content search results
        if (query.reads() != null && !query.reads().isEmpty()
            && base != null && base.outputMode() != StageFindQuery.OutputMode.READ) {
            var reads = computeReads(query, base);
            return base.withReadsAndWarnings(reads.reads(), reads.warnings());
        }

        return base;
    }

    // ---- Read mode (cat/sed) + contextLines ----

    private ReadComputation computeReads(StageFindQuery query, StageFindResult base) {
        var reads = query.reads();
        var result = new ArrayList<StageTabContent>();
        var warnings = new ArrayList<String>();
        if (reads == null || reads.isEmpty()) {
            return new ReadComputation(List.of(), List.of());
        }

        // Resolve tab IDs: explicit reads take priority, then fall back to base results.
        var tabIds = resolveReadTabIds(reads, base);
        if (tabIds.isEmpty()) {
            return new ReadComputation(List.of(), List.of());
        }

        boolean includeArchived = query.filter() != null && Boolean.TRUE.equals(query.filter().includeArchived());
        var allowedTabIds = new ArrayList<String>();
        for (var tabId : tabIds) {
            var tab = repo.findById(tabId);
            if (tab.isEmpty()) {
                warnings.add("tab_not_found: " + tabId);
                continue;
            }
            if (tab.get().archived() && !includeArchived) {
                warnings.add("archived_tab_skipped: " + tabId);
                continue;
            }
            allowedTabIds.add(tabId);
        }
        if (allowedTabIds.isEmpty()) {
            return new ReadComputation(List.of(), warnings);
        }

        // Look up content for each tab, applying range slicing
        var contents = repo.findContents(allowedTabIds);
        var contentById = contents.stream()
            .collect(Collectors.toMap(StageTabContent::tabId, Function.identity()));

        // Build a readById map for range lookup
        var readById = reads.stream()
            .filter(read -> read.tabId() != null)
            .collect(Collectors.toMap(
                StageFindQuery.Read::tabId,
                Function.identity(),
                (first, second) -> second,
                LinkedHashMap::new));
        var defaultRead = reads.stream()
            .filter(read -> read.tabId() == null)
            .findFirst();

        for (var tabId : allowedTabIds) {
            var content = contentById.get(tabId);
            if (content == null) continue;
            var read = readById.getOrDefault(tabId,
                defaultRead.orElseGet(() -> new StageFindQuery.Read(tabId, true)));

            var range = read.range();
            if (range instanceof StageFindQuery.ReadRange.Full) {
                result.add(limitPayload(content, warnings));
            } else if (range instanceof StageFindQuery.ReadRange.LineRange lr) {
                var sliced = sliceContent(content, lr.startLine(), lr.endLine());
                result.add(limitPayload(sliced, warnings));
            }
        }

        return new ReadComputation(List.copyOf(result), List.copyOf(warnings));
    }

    private List<String> resolveReadTabIds(List<StageFindQuery.Read> reads, StageFindResult base) {
        var explicitIds = reads.stream()
            .map(StageFindQuery.Read::tabId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!explicitIds.isEmpty()) {
            return explicitIds.stream().limit(MAX_TAB_IDS).toList();
        }
        if (base == null) {
            return List.of();
        }
        var ids = new LinkedHashSet<String>();
        ids.addAll(base.tabIds());
        for (var item : base.items()) {
            Object id = item.get("objectId");
            if (!(id instanceof String)) {
                id = item.get("id");
            }
            if (!(id instanceof String) && item.get("tab") instanceof Map<?, ?> tab) {
                id = tab.get("objectId");
                if (!(id instanceof String)) {
                    id = tab.get("id");
                }
            }
            if (id instanceof String s && !s.isBlank()) {
                ids.add(s);
            }
        }
        return ids.stream().limit(MAX_TAB_IDS).toList();
    }

    private StageTabContent limitPayload(StageTabContent content, List<String> warnings) {
        String payloadJson = truncateUtf8(content.payloadJson(), MAX_PAYLOAD_BYTES);
        String contentText = truncateUtf8(content.contentText(), MAX_PAYLOAD_BYTES);
        if (payloadJson.equals(content.payloadJson()) && contentText.equals(content.contentText())) {
            return content;
        }
        warnings.add("payload_too_large: " + content.tabId());
        return new StageTabContent(content.tabId(), payloadJson, contentText,
            content.contentVersion(), content.updatedAt());
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (value.substring(0, mid).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return value.substring(0, low);
    }

    private StageTabContent sliceContent(StageTabContent content, int startLine, int endLine) {
        String[] lines = content.contentText().split("\n", -1);
        int start = Math.max(1, startLine) - 1; // 0-indexed
        int end = Math.min(lines.length, endLine);
        if (start >= end) {
            return new StageTabContent(content.tabId(), content.payloadJson(), "",
                content.contentVersion(), content.updatedAt());
        }
        var sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append("\n");
            sb.append(lines[i]);
        }
        return new StageTabContent(content.tabId(), content.payloadJson(), sb.toString(),
            content.contentVersion(), content.updatedAt());
    }

    /**
     * Apply context lines (before/after) around matched lines in content.
     */
    static List<Map<String, Object>> withContextLines(
            List<Map<String, Object>> matches, String content, int before, int after) {
        if (matches.isEmpty() || (before <= 0 && after <= 0)) {
            return matches;
        }
        String[] lines = content.split("\n", -1);
        var result = new ArrayList<Map<String, Object>>();
        for (var match : matches) {
            int lineNum = (int) match.get("lineNumber");
            int start = Math.max(1, lineNum - before);
            int end = Math.min(lines.length, lineNum + after);
            var context = new ArrayList<Map<String, Object>>();
            for (int i = start; i <= end; i++) {
                Map<String, Object> ctxLine = new LinkedHashMap<>();
                ctxLine.put("lineNumber", i);
                ctxLine.put("line", lines[i - 1]);
                ctxLine.put("isMatch", i == lineNum);
                context.add(ctxLine);
            }
            Map<String, Object> enriched = new LinkedHashMap<>(match);
            enriched.put("before", context.stream()
                .filter(line -> !Boolean.TRUE.equals(line.get("isMatch")) && (int) line.get("lineNumber") < lineNum)
                .map(line -> line.get("line"))
                .toList());
            enriched.put("after", context.stream()
                .filter(line -> !Boolean.TRUE.equals(line.get("isMatch")) && (int) line.get("lineNumber") > lineNum)
                .map(line -> line.get("line"))
                .toList());
            enriched.put("context", context);
            result.add(enriched);
        }
        return result;
    }

    // ---- Content search (FTS + substring/regex post-filter) ----

    private StageFindResult executeContentSearch(StageFindQuery query) {
        var cq = query.contentQuery();
        String pattern = cq.pattern();
        boolean includeArchived = cq.includeArchived() != null ? cq.includeArchived() : false;
        int queryLimit = cq.limit() != null ? cq.limit() : 100;
        var mode = cq.mode() != null ? cq.mode() : StageFindQuery.ContentQuery.SearchMode.FTS;

        var matchesByTab = new LinkedHashMap<String, List<Map<String, Object>>>();
        var scoreByTab = new LinkedHashMap<String, Double>();
        var warnings = new ArrayList<String>();
        boolean truncated = false;
        int contextLines = maxContextLines(query.reads());

        switch (mode) {
            case FTS, SUBSTRING -> {
                int candidateLimit = Math.max(queryLimit * 4, 200);
                var rowids = indexer.ftsMatch(pattern, includeArchived, candidateLimit);
                var ids = indexer.rowidsToIds(rowids.stream().map(StageTabIndexerPort.RowidScore::rowid).toList());
                for (int i = 0; i < ids.size() && i < rowids.size(); i++) {
                    scoreByTab.put(ids.get(i), rowids.get(i).score());
                }
                var filteredIds = ids.stream()
                    .filter(id -> repo.findById(id).map(tab -> matchesFilter(tab, query.filter())).orElse(false))
                    .toList();
                truncated = filteredIds.size() > queryLimit || ids.size() >= candidateLimit;
                var idsToCheck = filteredIds.stream().limit(queryLimit).toList();

                var contents = repo.findContents(idsToCheck);
                var contentById = contents.stream()
                    .collect(Collectors.toMap(StageTabContent::tabId, Function.identity()));

                for (var id : idsToCheck) {
                    var c = contentById.get(id);
                    if (c == null) continue;
                    var lines = mode == StageFindQuery.ContentQuery.SearchMode.FTS
                        ? firstMatchPerLine(c.contentText(), pattern, cq.caseInsensitive() == null || cq.caseInsensitive())
                        : substringPostFilter(pattern, c.contentText(), cq.caseInsensitive() == null || cq.caseInsensitive());
                    if (!lines.isEmpty() || mode == StageFindQuery.ContentQuery.SearchMode.FTS) {
                        if (!lines.isEmpty() && contextLines > 0) {
                            lines = withContextLines(lines, c.contentText(), contextLines, contextLines);
                        }
                        matchesByTab.put(id, lines);
                    }
                }
            }
            case REGEX -> {
                validateRegexPattern(pattern);

                // For regex, list all tabs and fan-out with virtual threads
                StageTabRepository.ListFilter listFilter = new StageTabRepository.ListFilter(
                    query.filter() != null ? query.filter().type() : null,
                    query.filter() != null ? query.filter().connectionId() : null,
                    query.filter() != null ? query.filter().originSessionId() : null,
                    includeArchived, query.filter() != null ? query.filter().pinned() : null,
                    query.filter() != null ? query.filter().lastTouchedAfter() : null,
                    query.filter() != null ? query.filter().lastTouchedBefore() : null,
                    queryLimit * 4
                );
                var tabs = repo.list(listFilter);
                truncated = tabs.size() > queryLimit;
                var idsToCheck = tabs.stream().map(StageTab::id).limit(queryLimit).toList();

                var contents = repo.findContents(idsToCheck);
                var contentById = contents.stream()
                    .collect(Collectors.toMap(StageTabContent::tabId, Function.identity()));

                int flags = Boolean.TRUE.equals(cq.multiline()) ? Pattern.MULTILINE : 0;
                if (cq.caseInsensitive() == null || cq.caseInsensitive()) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                var regexResults = regexFanOut(pattern, flags, idsToCheck, contentById);
                warnings.addAll(regexResults.warnings());
                if (!regexResults.warnings().isEmpty()) {
                    truncated = true;
                }
                for (var entry : regexResults.matchesByTab().entrySet()) {
                    var lines = entry.getValue();
                    if (!lines.isEmpty()) {
                        var content = contentById.get(entry.getKey());
                        if (content != null && contextLines > 0) {
                            lines = withContextLines(lines, content.contentText(), contextLines, contextLines);
                        }
                        matchesByTab.put(entry.getKey(), lines);
                    }
                }
            }
        }

        int lineMatches = matchesByTab.values().stream().mapToInt(List::size).sum();
        int totalMatches = Math.max(lineMatches, matchesByTab.size());
        var sessionTitleCache = new HashMap<String, Optional<String>>();

        return switch (query.outputMode()) {
            case METADATA -> {
                var tabs = matchesByTab.keySet().stream()
                    .map(id -> repo.findById(id))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
                var items = tabs.stream()
                    .map(tab -> tabToMap(tab, sessionTitleCache))
                    .toList();
                yield StageFindResult.metadata(items, matchesByTab.size(), truncated).withWarnings(warnings);
            }
            case COUNT -> StageFindResult.count(totalMatches).withWarnings(warnings);
            case TABS_ONLY -> {
                var tabIds = List.copyOf(matchesByTab.keySet());
                yield StageFindResult.tabsOnly(tabIds, totalMatches, truncated).withWarnings(warnings);
            }
            case MATCHES, CONTENT -> {
                var items = matchesByTab.entrySet().stream()
                    .map(entry -> matchItem(
                        entry.getKey(),
                        entry.getValue(),
                        scoreByTab.get(entry.getKey()),
                        sessionTitleCache))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
                yield new StageFindResult(
                    StageFindQuery.OutputMode.MATCHES, items, List.of(),
                    totalMatches, matchesByTab.size(), truncated, List.of(), List.copyOf(warnings));
            }
            case READ -> StageFindResult.tabsOnly(List.copyOf(matchesByTab.keySet()), totalMatches, truncated)
                .withWarnings(warnings);
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
    private RegexFanOutResult regexFanOut(
            String pattern,
            int flags,
            List<String> tabIds,
            Map<String, StageTabContent> contentById) {

        var compiled = Pattern.compile(pattern, flags);
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        var warnings = new ArrayList<String>();

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
                    warnings.add("timeout: regex tab scan exceeded " + PER_TAB_TIMEOUT_SECONDS + "s");
                    future.cancel(true);
                } catch (Exception e) {
                    // Other error — skip
                }
            }
        }

        return new RegexFanOutResult(result, List.copyOf(warnings));
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

    private static List<Map<String, Object>> substringPostFilter(String pattern, String content, boolean caseInsensitive) {
        return firstMatchPerLine(content, pattern, caseInsensitive);
    }

    private static List<Map<String, Object>> firstMatchPerLine(String content, String pattern, boolean caseInsensitive) {
        var result = new ArrayList<Map<String, Object>>();
        String[] lines = content.split("\n");
        String needle = caseInsensitive ? pattern.toLowerCase() : pattern;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String haystack = caseInsensitive ? line.toLowerCase() : line;
            int idx = haystack.indexOf(needle);
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

    private Optional<Map<String, Object>> matchItem(
        String tabId,
        List<Map<String, Object>> matches,
        Double score,
        Map<String, Optional<String>> sessionTitleCache
    ) {
        return repo.findById(tabId).map(tab -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tab", tabToMap(tab, sessionTitleCache));
            item.put("matches", matches);
            if (score != null) {
                item.put("matchScore", score);
            }
            return item;
        });
    }

    private int maxContextLines(List<StageFindQuery.Read> reads) {
        if (reads == null || reads.isEmpty()) {
            return 0;
        }
        return reads.stream().mapToInt(StageFindQuery.Read::contextLines).max().orElse(0);
    }

    // ---- Metadata-only modes ----

    private StageFindResult executeMetadata(StageFindQuery.Filter filter) {
        if (filter != null && filter.objectId() != null) {
            List<StageTab> tabs = repo.findById(filter.objectId())
                .filter(tab -> matchesFilter(tab, filter))
                .map(List::of)
                .orElse(List.of());
            var sessionTitleCache = new HashMap<String, Optional<String>>();
            List<Map<String, Object>> items = tabs.stream()
                .map(tab -> tabToMap(tab, sessionTitleCache))
                .toList();
            return StageFindResult.metadata(items, items.size(), false);
        }
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        var sessionTitleCache = new HashMap<String, Optional<String>>();
        List<Map<String, Object>> items = tabs.stream()
            .map(tab -> tabToMap(tab, sessionTitleCache))
            .toList();
        boolean truncated = tabs.size() >= listFilter.limit();
        return StageFindResult.metadata(items, tabs.size(), truncated);
    }

    private StageFindResult executeCount(StageFindQuery.Filter filter) {
        if (filter != null && filter.objectId() != null) {
            int count = repo.findById(filter.objectId()).filter(tab -> matchesFilter(tab, filter)).isPresent() ? 1 : 0;
            return StageFindResult.count(count);
        }
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        return StageFindResult.count(tabs.size());
    }

    private StageFindResult executeTabsOnly(StageFindQuery.Filter filter) {
        if (filter != null && filter.objectId() != null) {
            List<String> tabIds = repo.findById(filter.objectId())
                .filter(tab -> matchesFilter(tab, filter))
                .map(tab -> List.of(tab.id()))
                .orElse(List.of());
            return StageFindResult.tabsOnly(tabIds, tabIds.size(), false);
        }
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
        var reads = computeReads(query, null);
        return new StageFindResult(
            StageFindQuery.OutputMode.READ, List.of(),
            reads.reads().stream().map(StageTabContent::tabId).toList(),
            reads.reads().size(), reads.reads().size(), false, reads.reads(), reads.warnings());
    }

    private StageTabRepository.ListFilter toListFilter(StageFindQuery.Filter filter) {
        if (filter == null) {
            return StageTabRepository.ListFilter.defaultFilter();
        }
        return new StageTabRepository.ListFilter(
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

    private Map<String, Object> tabToMap(StageTab tab, Map<String, Optional<String>> sessionTitleCache) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tab.id());
        m.put("objectId", tab.id());
        m.put("type", tab.type());
        m.put("title", tab.title());
        if (tab.connectionId() != null) m.put("connectionId", tab.connectionId());
        if (tab.databaseName() != null) m.put("databaseName", tab.databaseName());
        if (tab.schemaName() != null) m.put("schemaName", tab.schemaName());
        if (tab.originSessionId() != null) m.put("originSessionId", tab.originSessionId());
        m.put("originSessionTitle", resolveOriginSessionTitle(tab.originSessionId(), sessionTitleCache));
        m.put("pinned", tab.pinned());
        m.put("archived", tab.archived());
        m.put("createdAt", tab.createdAt());
        m.put("lastTouchedAt", tab.lastTouchedAt());
        m.put("payloadVersion", tab.payloadVersion());
        return m;
    }

    private boolean matchesFilter(StageTab tab, StageFindQuery.Filter filter) {
        if (filter == null) {
            return !tab.archived();
        }
        if (!Boolean.TRUE.equals(filter.includeArchived()) && tab.archived()) return false;
        if (filter.type() != null && !filter.type().equals(tab.type())) return false;
        if (filter.connectionId() != null && !filter.connectionId().equals(tab.connectionId())) return false;
        if (filter.objectId() != null && !filter.objectId().equals(tab.id())) return false;
        if (filter.originSessionId() != null && !filter.originSessionId().equals(tab.originSessionId())) return false;
        if (filter.pinned() != null && filter.pinned() != tab.pinned()) return false;
        if (filter.lastTouchedAfter() != null && tab.lastTouchedAt() <= filter.lastTouchedAfter()) return false;
        if (filter.lastTouchedBefore() != null && tab.lastTouchedAt() >= filter.lastTouchedBefore()) return false;
        return true;
    }

    private String resolveOriginSessionTitle(
        String originSessionId,
        Map<String, Optional<String>> sessionTitleCache
    ) {
        if (originSessionId == null || originSessionId.isBlank()) {
            return null;
        }
        return sessionTitleCache
            .computeIfAbsent(originSessionId, id -> sessions.findById(id).map(session -> session.title()))
            .orElse(null);
    }
}
