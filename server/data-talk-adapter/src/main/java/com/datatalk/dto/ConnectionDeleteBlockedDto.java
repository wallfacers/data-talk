package com.datatalk.dto;

public record ConnectionDeleteBlockedDto(
    String error,
    String connectionId,
    Counts counts
) {
    public record Counts(int sessions, int candidates, int temporary, int archived) {}

    public static ConnectionDeleteBlockedDto of(String connectionId, int sessions, int candidates, int temporary, int archived) {
        return new ConnectionDeleteBlockedDto(
            "connection_has_resources",
            connectionId,
            new Counts(sessions, candidates, temporary, archived));
    }
}