package com.datatalk.dto;

public record SessionDto(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    long createdAt,
    long updatedAt,
    boolean titleLocked
) {}