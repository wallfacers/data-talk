package com.datatalk.application.stage;

import com.datatalk.application.stage.StageFindQuery.Filter;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageFindServiceMetadataTest {

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
    void metadataModeReturnsTabMetadata() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t1", "query_editor", StageTabScope.WORKSPACE,
            "My Query", "conn-1", null, null, null, 1,
            false, false, null, now, now);

        when(repo.list(any(StageTabRepository.ListFilter.class))).thenReturn(List.of(tab));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.METADATA,
            new Filter(StageTabScope.WORKSPACE, null, null, null, false, null, null, null, 100),
            null, List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.outputMode()).isEqualTo(StageFindQuery.OutputMode.METADATA);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0)).containsEntry("id", "t1");
        assertThat(result.items().get(0)).containsEntry("title", "My Query");
        assertThat(result.items().get(0)).containsEntry("scope", "workspace");
        assertThat(result.totalMatched()).isEqualTo(1);
    }

    @Test
    void countModeReturnsTotalCount() {
        long now = System.currentTimeMillis();
        StageTab tab1 = new StageTab("t1", "query_editor", StageTabScope.WORKSPACE,
            "Q1", null, null, null, null, 1, false, false, null, now, now);
        StageTab tab2 = new StageTab("t2", "chart", StageTabScope.WORKSPACE,
            "C1", null, null, null, null, 1, false, false, null, now, now);

        when(repo.list(any(StageTabRepository.ListFilter.class))).thenReturn(List.of(tab1, tab2));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.COUNT,
            new Filter(StageTabScope.WORKSPACE, null, null, null, false, null, null, null, 100),
            null, List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.outputMode()).isEqualTo(StageFindQuery.OutputMode.COUNT);
        assertThat(result.totalMatched()).isEqualTo(2);
        assertThat(result.items()).isEmpty();
    }
}
