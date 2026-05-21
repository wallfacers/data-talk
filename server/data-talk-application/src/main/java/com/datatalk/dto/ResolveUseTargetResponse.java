package com.datatalk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResolveUseTargetResponse(
    String status,
    SessionDataContextDto context,
    TargetOptionDto matchedTarget,
    List<TargetOptionDto> candidates,
    List<TargetOptionDto> suggestions,
    String message
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetOptionDto(
        String level,
        String connectionId,
        String connectionName,
        String database,
        String schema,
        String label
    ) {}
}
