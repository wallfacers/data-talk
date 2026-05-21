package com.datatalk.domain.event;

/** An event with its per-session monotonic id and wall-clock timestamp. */
public record NumberedEvent(long eventId, String sessionId, DtEvent event, long ts) {}
