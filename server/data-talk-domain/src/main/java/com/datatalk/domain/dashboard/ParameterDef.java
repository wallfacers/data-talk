package com.datatalk.domain.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ParameterDef(
    String id,
    String scope,
    String ownerWidgetId,
    String name,
    String type,
    @JsonProperty("default") Object default_
) {}
