package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
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
        StageTab tab = stageTab("t1", "query_editor", "Test",
            null, null, null, null, 1, false, false, null, 500L, 500L);

        svc.upsert(tab, null);

        verify(repo).upsertMetadata(argThat(saved ->
            saved.id().equals("t1")
                && saved.title().equals("Test")
                && saved.lastTouchedAt() == 1_000_000L
                && saved.createdAt() == 500L
        ), eq(null));
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

    private static StageTab stageTab(
        String id,
        String type,
        String title,
        String connectionId,
        String databaseName,
        String schemaName,
        String originSessionId,
        int payloadVersion,
        boolean pinned,
        boolean archived,
        Long archivedAt,
        long createdAt,
        long lastTouchedAt
    ) {
        try {
            for (var constructor : StageTab.class.getConstructors()) {
                if (constructor.getParameterCount() == 13) {
                    return (StageTab) constructor.newInstance(
                        id, type, title, connectionId, databaseName, schemaName,
                        originSessionId, payloadVersion, pinned, archived, archivedAt, createdAt, lastTouchedAt
                    );
                }
                if (constructor.getParameterCount() == 14) {
                    return (StageTab) constructor.newInstance(
                        id, type, workspaceScope(constructor.getParameterTypes()[2]), title,
                        connectionId, databaseName, schemaName, originSessionId,
                        payloadVersion, pinned, archived, archivedAt, createdAt, lastTouchedAt
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
