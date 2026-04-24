package com.datatalk.dto;

import java.util.List;

public record ConnectionTargetsDto(
    String connectionId,
    String connectionName,
    List<String> databases,
    List<String> schemas
) {}
