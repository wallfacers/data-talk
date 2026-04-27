package com.datatalk.application.stage;

import com.datatalk.application.stage.StageFindQuery.Read;
import com.datatalk.application.stage.StageFindQuery.ReadRange;
import com.datatalk.domain.stage.StageTabContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageFindServiceReadTest {

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
    void readByTabIdsReturnsFullContent() {
        long now = System.currentTimeMillis();
        var content = new StageTabContent("tab-1", "{\"sql\":\"SELECT 1\"}", "SELECT 1", 1, now);
        when(repo.findContents(List.of("tab-1"))).thenReturn(List.of(content));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.READ,
            null, null,
            List.of(new Read("tab-1", true))
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.reads()).hasSize(1);
        assertThat(result.reads().get(0).tabId()).isEqualTo("tab-1");
        assertThat(result.reads().get(0).contentText()).isEqualTo("SELECT 1");
    }

    @Test
    void readByLineRangeSlicesContent() {
        long now = System.currentTimeMillis();
        String contentText = "line1\nline2\nline3\nline4\nline5";
        var content = new StageTabContent("tab-2", "{}", contentText, 1, now);
        when(repo.findContents(List.of("tab-2"))).thenReturn(List.of(content));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.READ,
            null, null,
            List.of(new Read("tab-2", true, new ReadRange.LineRange(2, 4)))
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.reads()).hasSize(1);
        assertThat(result.reads().get(0).contentText()).isEqualTo("line2\nline3\nline4");
    }

    @Test
    void contextLinesAroundMatchesAttachesBeforeAndAfter() {
        String content = "alpha\nbeta\ngamma\ndelta\nepsilon\nzeta\neta";
        // Match at line 4 ("delta")
        var match = Map.<String, Object>of(
            "lineNumber", 4,
            "line", "delta",
            "columnStart", 1,
            "columnEnd", 6
        );

        var result = StageFindService.withContextLines(List.of(match), content, 2, 1);

        assertThat(result).hasSize(1);
        var context = (List<Map<String, Object>>) result.get(0).get("context");
        assertThat(context).hasSize(4); // lines 2,3,4,5 (before=2, match=4, after=1)
        assertThat(context.get(0)).containsEntry("lineNumber", 2);
        assertThat(context.get(0)).containsEntry("isMatch", false);
        assertThat(context.get(1)).containsEntry("lineNumber", 3);
        assertThat(context.get(1)).containsEntry("isMatch", false);
        assertThat(context.get(2)).containsEntry("lineNumber", 4);
        assertThat(context.get(2)).containsEntry("isMatch", true);
        assertThat(context.get(3)).containsEntry("lineNumber", 5);
        assertThat(context.get(3)).containsEntry("isMatch", false);
    }
}
