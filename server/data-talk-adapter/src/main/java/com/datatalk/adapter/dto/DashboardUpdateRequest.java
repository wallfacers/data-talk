package com.datatalk.adapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record DashboardUpdateRequest(JsonNode dashboard, int baseVersion) {}
