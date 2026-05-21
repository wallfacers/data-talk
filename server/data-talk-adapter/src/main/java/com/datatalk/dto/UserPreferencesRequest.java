package com.datatalk.dto;

public record UserPreferencesRequest(
    String timezone,
    String dateFormat
) {}
