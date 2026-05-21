package com.datatalk.domain.er;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErDesignerColumn(
    String id,
    String name,
    String type,
    boolean nullable,
    boolean isPrimaryKey,
    boolean isAutoIncrement,
    @JsonProperty("default") String defaultValue,
    String comment
) {}
