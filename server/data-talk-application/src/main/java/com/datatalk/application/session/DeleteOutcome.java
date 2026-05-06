package com.datatalk.application.session;

import com.datatalk.application.fileartifact.FileArtifactRepository.ConnectionResourceCounts;
import com.datatalk.domain.fileartifact.FileArtifact;

import java.util.List;

/**
 * Two-phase delete outcome.
 *
 * <p>Spec §A.4. Phase 1 (no {@code force} flag) returns {@link Blocked*}
 * variants when there are resources the user must consciously decide on;
 * Phase 2 ({@code force=true}) always returns {@link Ok}.
 */
public sealed interface DeleteOutcome
        permits DeleteOutcome.Ok,
                DeleteOutcome.BlockedByCandidates,
                DeleteOutcome.BlockedByResources,
                DeleteOutcome.NotFound {

    record Ok() implements DeleteOutcome {}

    record BlockedByCandidates(String sessionId, List<FileArtifact> candidates) implements DeleteOutcome {}

    record BlockedByResources(String connectionId, ConnectionResourceCounts counts) implements DeleteOutcome {}

    record NotFound(String id) implements DeleteOutcome {}
}