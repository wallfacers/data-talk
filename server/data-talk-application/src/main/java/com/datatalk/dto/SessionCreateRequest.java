package com.datatalk.dto;

public record SessionCreateRequest(
    String connectionId,
    String title
) {}