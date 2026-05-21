package com.datatalk.dto;

public record ConnectionTestResultDto(
    boolean ok,
    long latencyMs,
    String reason
) {}