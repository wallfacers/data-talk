package com.datatalk.domain.preference;

import java.time.ZoneId;

public record UserPreferences(
    String id,
    ZoneId timezone,
    String dateFormat,
    long updatedAt
) {
    public static final UserPreferences DEFAULT = new UserPreferences(
        "default", ZoneId.of("UTC"), "yyyy-MM-dd HH:mm:ss", 0L
    );

    public UserPreferences {
        if (timezone == null) timezone = ZoneId.of("UTC");
        if (dateFormat == null || dateFormat.isBlank()) dateFormat = "yyyy-MM-dd HH:mm:ss";
    }
}
