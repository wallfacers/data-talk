package com.datatalk.application.persistence;

/**
 * Persistence record for an artifact snapshot.
 */
public record ArtifactRecord(
    String id,
    int version,
    String sessionId,
    String kind,
    String producedBy,
    String payloadRef,
    int payloadSize,
    String supersedesId,
    Integer supersedesVersion,
    boolean pinned,
    long createdAt
) {}
