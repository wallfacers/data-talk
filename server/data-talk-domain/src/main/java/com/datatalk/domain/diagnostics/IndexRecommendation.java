package com.datatalk.domain.diagnostics;

import java.util.List;

public record IndexRecommendation(
    String table,
    List<String> columns,
    String indexType,
    Impact impact,
    String rationale
) {}
