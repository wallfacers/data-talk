package com.datatalk.application.stage;

import com.datatalk.application.stage.StageFindQuery.ContentQuery;
import com.datatalk.application.stage.StageFindQuery.ContentQuery.SearchMode;
import com.datatalk.application.stage.StageFindQuery.Filter;
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
    private StageFindService svc;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        indexer = mock(StageTabIndexerPort.class);
        svc = new StageFindService(repo, indexer);
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
        when(repo.findContents(List.of("tab-x", "tab-y")))
            .thenReturn(List.of(contentX, contentY));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery("email", false, 100, SearchMode.FTS),
            List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.tabIds()).containsExactly("tab-x");
        assertThat(result.totalMatched()).isEqualTo(1);
    }
}
