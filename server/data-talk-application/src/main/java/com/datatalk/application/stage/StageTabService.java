package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for stage tab lifecycle management.
 * Provides 90-day lazy auto-archive and 1MB payload cap.
 */
@Service
public class StageTabService {

    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024; // 1MB
    public static final int MAX_TABS_HARD_CAP = 10_000;
    public static final int AUTO_ARCHIVE_DAYS = 90;

    private final StageTabRepository repo;
    private final Clock clock;

    public StageTabService(StageTabRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /**
     * Upsert tab metadata with optional optimistic concurrency check.
     * Sets lastTouchedAt from the system clock.
     *
     * @return the new payloadVersion
     */
    public int upsert(StageTab tab, Integer expectedPayloadVersion) {
        long now = clock.millis();
        StageTab withTimestamp = new StageTab(
            tab.id(), tab.type(), tab.title(),
            tab.connectionId(), tab.databaseName(), tab.schemaName(),
            tab.originSessionId(), tab.payloadVersion(),
            tab.pinned(), tab.archived(), tab.archivedAt(),
            tab.createdAt(), now
        );
        return repo.upsertMetadata(withTimestamp, expectedPayloadVersion);
    }

    /**
     * Save payload and content text for a tab. Validates size cap.
     */
    public int savePayload(String tabId, String payloadJson, String contentText, Integer expectedVersion) {
        long payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > MAX_PAYLOAD_BYTES) {
            throw new StageTabPayloadTooLargeException(payloadBytes, MAX_PAYLOAD_BYTES);
        }
        long now = clock.millis();
        return repo.upsertPayload(tabId, payloadJson, contentText, expectedVersion, now);
    }

    public Optional<StageTab> find(String id) {
        return repo.findById(id);
    }

    public Optional<StageTabContent> findContent(String tabId) {
        return repo.findContent(tabId);
    }

    public List<StageTab> list(StageTabRepository.ListFilter filter) {
        return repo.list(filter);
    }

    public boolean delete(String id) {
        return repo.delete(id);
    }

    public void setArchived(String id, boolean archived) {
        Long archivedAt = archived ? clock.millis() : null;
        repo.setArchived(id, archived, archivedAt);
    }

    /**
     * Run lazy auto-archive: any non-archived tab whose last_touched_at
     * is older than AUTO_ARCHIVE_DAYS will be archived.
     *
     * @return number of tabs archived
     */
    public int runLazyAutoArchive() {
        Instant cutoff = Instant.now(clock).minus(java.time.Duration.ofDays(AUTO_ARCHIVE_DAYS));
        return repo.archiveStaleSince(cutoff.toEpochMilli());
    }
}
