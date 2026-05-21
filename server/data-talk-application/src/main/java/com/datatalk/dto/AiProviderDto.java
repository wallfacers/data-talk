package com.datatalk.dto;

import java.util.List;

public record AiProviderDto(
    String id,
    String name,
    boolean connected,
    List<AiModelDto> models
) {}