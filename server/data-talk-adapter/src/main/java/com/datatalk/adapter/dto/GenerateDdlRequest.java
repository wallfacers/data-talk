package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErDesignerPayload;

public record GenerateDdlRequest(
    ErDesignerPayload payload,
    String connectionId,
    boolean includeDrops
) {}
