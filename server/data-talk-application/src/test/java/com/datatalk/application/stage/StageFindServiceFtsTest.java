package com.datatalk.application.stage;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.stage.StageFindQuery.ContentQuery;
import com.datatalk.application.stage.StageFindQuery.ContentQuery.SearchMode;
import com.datatalk.application.stage.StageFindQuery.Filter;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageFindServiceFtsTest {

    private StageTabRepository repo;
    private StageTabIndexerPort indexer;
    private SessionRepository sessions;
    private StageFindService svc;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        indexer = mock(StageTabIndexerPort.class);
        sessions = mock(SessionRepository.class);
        svc = new StageFindService(repo, indexer, sessions);
    }

    @Test
    void ftsModeRanksByBm25() {
        long now = System.currentTimeMillis();
        // FTS returns rowids ordered by bm25 score (best first)
        var rowid1 = new StageTabIndexerPort.RowidScore(1, -1.5);
        var rowid2 = new StageTabIndexerPort.RowidScore(2, -3.0);

        when(indexer.ftsMatch("email", false, 400))
            .thenReturn(List.of(rowid1, rowid2));
        when(indexer.rowidsToIds(List.of(1L, 2L)))
            .thenReturn(List.of("tab-a", "tab-b"));

        var contentA = new StageTabContent("tab-a", "{}", "contact email: alice@example.com", 1, now);
        var contentB = new StageTabContent("tab-b", "{}", "send email to bob@corp.io", 1, now);
        when(repo.findById("tab-a")).thenReturn(java.util.Optional.of(stageTab("tab-a", "A", now)));
        when(repo.findById("tab-b")).thenReturn(java.util.Optional.of(stageTab("tab-b", "B", now)));
        when(repo.findContents(List.of("tab-a", "tab-b")))
            .thenReturn(List.of(contentA, contentB));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery("email", false, 100, SearchMode.FTS),
            List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.outputMode()).isEqualTo(StageFindQuery.OutputMode.TABS_ONLY);
        assertThat(result.tabIds()).containsExactly("tab-a", "tab-b");
        assertThat(result.totalMatched()).isEqualTo(2);
    }

    @Test
    void substringModePostFiltersFtsCandidates() {
        long now = System.currentTimeMillis();
        // FTS trigram returns 2 candidates
        var rowid1 = new StageTabIndexerPort.RowidScore(10, -1.0);
        var rowid2 = new StageTabIndexerPort.RowidScore(20, -2.0);

        when(indexer.ftsMatch("email", false, 400))
            .thenReturn(List.of(rowid1, rowid2));
        when(indexer.rowidsToIds(List.of(10L, 20L)))
            .thenReturn(List.of("tab-x", "tab-y"));

        // tab-x contains "email", tab-y does not
        var contentX = new StageTabContent("tab-x", "{}", "my email is here", 1, now);
        var contentY = new StageTabContent("tab-y", "{}", "no match here", 1, now);
        when(repo.findById("tab-x")).thenReturn(java.util.Optional.of(stageTab("tab-x", "X", now)));
        when(repo.findById("tab-y")).thenReturn(java.util.Optional.of(stageTab("tab-y", "Y", now)));
        when(repo.findContents(List.of("tab-x", "tab-y")))
            .thenReturn(List.of(contentX, contentY));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery("email", false, 100, SearchMode.SUBSTRING),
            List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.tabIds()).containsExactly("tab-x");
        assertThat(result.totalMatched()).isEqualTo(1);
    }

    private static StageTab stageTab(String id, String title, long now) {
        try {
            for (var constructor : StageTab.class.getConstructors()) {
                if (constructor.getParameterCount() == 13) {
                    return (StageTab) constructor.newInstance(
                        id, "query_editor", title, null, null, null, null, 1,
                        false, false, null, now, now
                    );
                }
                if (constructor.getParameterCount() == 14) {
                    return (StageTab) constructor.newInstance(
                        id, "query_editor", workspaceScope(constructor.getParameterTypes()[2]), title,
                        null, null, null, null, 1, false, false, null, now, now
                    );
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to construct StageTab", e);
        }
        throw new AssertionError("Unsupported StageTab constructor shape");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object workspaceScope(Class<?> scopeType) {
        if (!scopeType.isEnum()) {
            throw new AssertionError("Expected enum scope type but got " + scopeType.getName());
        }
        return Enum.valueOf((Class<? extends Enum>) scopeType.asSubclass(Enum.class), "WORKSPACE");
    }
}
