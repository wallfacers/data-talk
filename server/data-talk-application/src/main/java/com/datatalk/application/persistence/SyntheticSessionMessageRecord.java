package com.datatalk.application.persistence;

/**
 * Persistence record for a synthetic session message.
 */
public record SyntheticSessionMessageRecord(
    String id,
    String sessionId,
    String kind,
    String text,
    String metadataJson,
    long createdAt
) {}
