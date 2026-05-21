package com.datatalk.application.persistence;

/**
 * Persistence record for an artifact snapshot.
 * <p>{@code originMessageId}/{@code originPartId} link chart artifacts back to the
 * chat message-part that produced them.
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
    long createdAt,
    String originMessageId,
    String originPartId
) {}
