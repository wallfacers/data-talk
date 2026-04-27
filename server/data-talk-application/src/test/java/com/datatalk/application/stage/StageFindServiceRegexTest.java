package com.datatalk.application.stage;

import com.datatalk.application.stage.StageFindQuery.ContentQuery;
import com.datatalk.application.stage.StageFindQuery.ContentQuery.SearchMode;
import com.datatalk.application.stage.StageFindQuery.Filter;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageFindServiceRegexTest {

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
    void regexFanOutMatchesAcrossTabs() {
        long now = System.currentTimeMillis();
        var tab1 = new StageTab("tab-1", "query_editor", StageTabScope.WORKSPACE,
            "Q1", null, null, null, null, 1, false, false, null, now, now);
        var tab2 = new StageTab("tab-2", "query_editor", StageTabScope.WORKSPACE,
            "Q2", null, null, null, null, 1, false, false, null, now, now);

        when(repo.list(any(StageTabRepository.ListFilter.class)))
            .thenReturn(List.of(tab1, tab2));

        var content1 = new StageTabContent("tab-1", "{}", "order #12345 confirmed", 1, now);
        var content2 = new StageTabContent("tab-2", "{}", "no orders here", 1, now);
        when(repo.findContents(List.of("tab-1", "tab-2")))
            .thenReturn(List.of(content1, content2));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery("#\\d{5}", false, 100, SearchMode.REGEX),
            List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.tabIds()).containsExactly("tab-1");
        assertThat(result.totalMatched()).isEqualTo(1);
    }

    @Test
    void regexInvalidPatternThrowsException() {
        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery("[unclosed", false, 100, SearchMode.REGEX),
            List.of()
        );

        assertThatThrownBy(() -> svc.execute(query))
            .isInstanceOf(StageFindInvalidPatternException.class)
            .hasMessageContaining("[unclosed");
    }

    @Test
    void regexLongerThan200CharsRejected() {
        String longPattern = "a".repeat(201);

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.TABS_ONLY,
            new Filter(null, null, null, null, false, null, null, null, 100),
            new ContentQuery(longPattern, false, 100, SearchMode.REGEX),
            List.of()
        );

        assertThatThrownBy(() -> svc.execute(query))
            .isInstanceOf(StageFindInvalidPatternException.class)
            .hasMessageContaining("maximum length");
    }
}
