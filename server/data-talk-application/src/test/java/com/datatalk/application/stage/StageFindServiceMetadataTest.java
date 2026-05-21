package com.datatalk.application.stage;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.stage.StageFindQuery.Filter;
import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageFindServiceMetadataTest {

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
    void metadataModeReturnsTabMetadataWithOriginSessionTitle() {
        long now = System.currentTimeMillis();
        StageTab tab = stageTab("t1", "query_editor",
            "My Query", "conn-1", null, null, "sess-1", 1,
            false, false, null, now, now);

        when(repo.list(StageTabRepository.ListFilter.defaultFilter())).thenReturn(List.of(tab));
        when(sessions.findById("sess-1")).thenReturn(Optional.of(session("sess-1", "April Weekly", now)));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.METADATA,
            new Filter(null, null, null, null, false, null, null, null, 100),
            null, List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.outputMode()).isEqualTo(StageFindQuery.OutputMode.METADATA);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0)).containsEntry("id", "t1");
        assertThat(result.items().get(0)).containsEntry("title", "My Query");
        assertThat(result.items().get(0)).containsEntry("originSessionId", "sess-1");
        assertThat(result.items().get(0)).containsEntry("originSessionTitle", "April Weekly");
        assertThat(result.items().get(0)).doesNotContainKey("scope");
        assertThat(result.totalMatched()).isEqualTo(1);
    }

    @Test
    void countModeReturnsTotalCount() {
        long now = System.currentTimeMillis();
        StageTab tab1 = stageTab("t1", "query_editor",
            "Q1", null, null, null, null, 1, false, false, null, now, now);
        StageTab tab2 = stageTab("t2", "chart",
            "C1", null, null, null, null, 1, false, false, null, now, now);

        when(repo.list(StageTabRepository.ListFilter.defaultFilter())).thenReturn(List.of(tab1, tab2));

        StageFindQuery query = new StageFindQuery(
            StageFindQuery.OutputMode.COUNT,
            new Filter(null, null, null, null, false, null, null, null, 100),
            null, List.of()
        );

        StageFindResult result = svc.execute(query);

        assertThat(result.outputMode()).isEqualTo(StageFindQuery.OutputMode.COUNT);
        assertThat(result.totalMatched()).isEqualTo(2);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void metadataModeSetsOriginSessionTitleNullWhenSessionMissing() {
        long now = System.currentTimeMillis();
        StageTab tab = stageTab("t2", "query_editor",
            "Orphan Query", null, null, null, "sess-missing", 1,
            false, false, null, now, now);

        when(repo.list(StageTabRepository.ListFilter.defaultFilter())).thenReturn(List.of(tab));
        when(sessions.findById("sess-missing")).thenReturn(Optional.empty());

        StageFindResult result = svc.execute(new StageFindQuery(
            StageFindQuery.OutputMode.METADATA,
            new Filter(null, null, null, null, false, null, null, null, 100),
            null,
            List.of()
        ));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("originSessionId", "sess-missing");
            assertThat(item).containsEntry("originSessionTitle", null);
        });
    }

    private static SessionRecord session(String id, String title, long now) {
        return new SessionRecord(id, null, title, false, null, now, now, false);
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
