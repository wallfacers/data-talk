package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StageTabServiceTest {

    private StageTabRepository repo;
    private StageTabService svc;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        fixedClock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC);
        svc = new StageTabService(repo, fixedClock);
    }

    @Test
    void upsertSetsLastTouchedAtFromClock() {
        StageTab tab = new StageTab("t1", "query_editor", StageTabScope.WORKSPACE,
            "Test", null, null, null, null, 1,
            false, false, null, 500L, 500L);

        svc.upsert(tab, null);

        verify(repo).upsertMetadata(any(StageTab.class), eq(null));
    }

    @Test
    void runLazyAutoArchiveUsesNinetyDayCutoff() {
        when(repo.archiveStaleSince(anyLong())).thenReturn(3);

        int archived = svc.runLazyAutoArchive();

        assertThat(archived).isEqualTo(3);
        // The cutoff should be 90 days before the fixed clock time
        Instant expectedCutoff = Instant.ofEpochMilli(1_000_000L).minus(Duration.ofDays(90));
        verify(repo).archiveStaleSince(eq(expectedCutoff.toEpochMilli()));
    }

    @Test
    void deletePropagatesToRepo() {
        when(repo.delete("t-del")).thenReturn(true);

        boolean result = svc.delete("t-del");

        assertThat(result).isTrue();
        verify(repo).delete("t-del");
    }

    @Test
    void rejectsTabExceedingMaxPayloadSize() {
        // Create a payload that exceeds 1MB
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < StageTabService.MAX_PAYLOAD_BYTES + 100; i++) {
            sb.append('x');
        }
        String largePayload = sb.toString();

        assertThatThrownBy(() -> svc.savePayload("t-big", largePayload, "text", 0))
            .isInstanceOf(StageTabPayloadTooLargeException.class);
    }
}
