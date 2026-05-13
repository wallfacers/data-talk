package com.datatalk.application.ingestion.repository;

import com.datatalk.domain.ingestion.IngestionJob;

import java.util.List;
import java.util.Optional;

public interface IngestionJobRepository {
    void save(IngestionJob job);
    Optional<IngestionJob> findById(String id);
    List<IngestionJob> list(String connectionIdOrNull, String statusOrNull,
                            Long createdAfterOrNull, int limit, int offset);
    int count(String connectionIdOrNull, String statusOrNull, Long createdAfterOrNull);
    void updateStatus(String id, String newStatus, String errorMessageOrNull, long updatedAt);
    void updatePayloadArtifact(String id, String artifactId, int rowCount, long bytesFetched, long updatedAt);
    void updateMapping(String id, String mappingJson, long updatedAt);
    void updateMappingHash(String id, String mappingHash, long updatedAt);
    void updateTargetTable(String id, String connectionId, String schema, String table, long updatedAt);
    void updateProgress(String id, int rowsInserted, long updatedAt);
    void updateCompleted(String id, int finalRowCount, long completedAt, long updatedAt);
    void deleteById(String id);
    int deleteByIds(List<String> ids);

    /** Refreshes heartbeat_at; no-op if job missing. Called from active fetch / write loops. */
    void updateHeartbeat(String id, long ts);

    /**
     * Bulk-flips all rows whose status is in {@code statusList} to {@code 'failed'},
     * setting error_message to {@code reason} and updated_at to {@code updatedAt}.
     * Used by the startup sweeper to reconcile state after a crash/restart.
     *
     * @return number of rows updated
     */
    int batchFailByStatus(List<String> statusList, String reason, long updatedAt);

    /**
     * Bulk-flips rows whose status is in {@code statusList} AND heartbeat_at is older
     * than {@code heartbeatDeadline} (or null) to {@code 'failed'}. Rows with null
     * heartbeat are included to handle pre-V23 rows or jobs that never ticked.
     *
     * @return number of rows updated
     */
    int batchFailIfHeartbeatBefore(List<String> statusList, long heartbeatDeadline,
                                    String reason, long updatedAt);
}
