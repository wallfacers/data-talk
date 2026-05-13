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
}
