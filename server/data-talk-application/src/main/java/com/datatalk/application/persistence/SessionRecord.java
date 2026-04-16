package com.datatalk.application.persistence;

/**
 * Persistence record for a session. Maps to the {@code sessions} table.
 */
public record SessionRecord(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    String openCodeSid,
    long createdAt,
    long updatedAt
) {}
