package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabScope;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for the ui_find action. Supports metadata listing and count modes.
 * Content search and read modes will throw UnsupportedOperationException
 * until full implementation (Task 14-18).
 */
@Service
public class StageFindService {

    private final StageTabRepository repo;

    public StageFindService(StageTabRepository repo) {
        this.repo = repo;
    }

    public StageFindResult execute(StageFindQuery query) {
        return switch (query.outputMode()) {
            case METADATA -> executeMetadata(query.filter());
            case COUNT -> executeCount(query.filter());
            case TABS_ONLY -> executeTabsOnly(query.filter());
            case CONTENT -> throw new UnsupportedOperationException(
                "Content search mode not yet implemented (scheduled for Task 14-18)");
            case READ -> throw new UnsupportedOperationException(
                "Read mode not yet implemented (scheduled for Task 14-18)");
        };
    }

    private StageFindResult executeMetadata(StageFindQuery.Filter filter) {
        StageTabRepository.ListFilter listFilter = toListFilter(filter);
        List<StageTab> tabs = repo.list(listFilter);
        List<Map<String, Object>> items = tabs.stream().map(this::tabToMap).toList();
        boolean truncated = tabs.size() >= listFilter.limit();
        return StageFindResult.metadata(items, tabs.size(), truncated);
    }

    private StageFindResult executeCount(StageFindQuery.Filter filter) {
        // List with limit=max to get all matching tabs for counting
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
