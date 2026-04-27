package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;

import java.util.List;
import java.util.Optional;

/**
 * Application-layer repository interface for stage tab persistence.
 * Implementation lives in the infrastructure layer.
 */
public interface StageTabRepository {

    /**
     * Insert or update tab metadata. If the row already exists and expectedPayloadVersion
     * is provided, the update is conditional on the current payload_version matching.
     *
     * @return the new payloadVersion (1 on insert, incremented on update)
     */
    int upsertMetadata(StageTab tab, Integer expectedPayloadVersion);

    /**
     * Insert or update tab payload + content. Uses optimistic concurrency:
     * if expectedVersion does not match current payload_version, throws
     * {@link StageTabConcurrencyException}.
     */
    int upsertPayload(String tabId, String payloadJson, String contentText, Integer expectedVersion, long updatedAt);

    Optional<StageTab> findById(String id);

    Optional<StageTabContent> findContent(String tabId);

    List<StageTab> list(ListFilter filter);

    List<StageTab> recentByLastTouched(int limit);

    boolean delete(String id);

    void setArchived(String id, boolean archived, Long archivedAt);

    int archiveStaleSince(long thresholdEpochMillis);

    int countActive();

    int countArchived();

    List<StageTabContent> findContents(List<String> tabIds);

    /**
     * Filter parameters for listing stage tabs.
     */
    record ListFilter(
        StageTabScope scope,
        String type,
        String connectionId,
        String originSessionId,
        boolean includeArchived,
        Boolean pinned,
        Long lastTouchedAfter,
        Long lastTouchedBefore,
        int limit
    ) {
        public static ListFilter defaultFilter() {
            return new ListFilter(null, null, null, null, false, null, null, null, 100);
        }
    }
}
