package com.datatalk.adapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record DashboardPatchRequest(int baseVersion, List<PatchOpDto> ops) {
    public record PatchOpDto(String op, String path, JsonNode value) {}
}
