package com.datatalk.adapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record DashboardPromoteRequest(JsonNode dashboard, String html) {
    public DashboardPromoteRequest {
        // html is optional — null means JSON-only promote (backwards-compatible)
    }
}
