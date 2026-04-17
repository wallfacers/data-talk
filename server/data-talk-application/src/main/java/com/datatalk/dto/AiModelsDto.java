package com.datatalk.dto;

import java.util.List;

public record AiModelsDto(
    List<AiProviderDto> providers
) {}