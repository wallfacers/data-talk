package com.datatalk.adapter.dto;

import java.util.List;

public record SqlConfirmationPayload(
    String level,
    String reason,
    List<String> affectedObjects,
    String sqlPreview
) {}
