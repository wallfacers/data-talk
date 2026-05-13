package com.datatalk.dto;

public record UserPreferencesResponse(
    String timezone,
    String dateFormat
) {}
