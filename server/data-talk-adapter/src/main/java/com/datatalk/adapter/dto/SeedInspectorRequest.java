package com.datatalk.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeedInspectorRequest(
    @NotBlank String connectionId,
    @NotEmpty @Size(max = 100) List<String> tables,
    int neighborDepth
) {}
