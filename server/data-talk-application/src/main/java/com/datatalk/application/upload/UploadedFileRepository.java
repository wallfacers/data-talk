package com.datatalk.application.upload;

import com.datatalk.domain.upload.UploadedFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadedFileRepository {

    void insert(UploadedFile file);

    Optional<UploadedFile> findById(String id);

    void deleteById(String id);

    List<UploadedFile> findOlderThan(Instant cutoff);

    /** Return IDs of all uploaded files associated with the given session. */
    List<String> findIdsBySessionId(String sessionId);
}
