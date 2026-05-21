package com.datatalk.domain.diagnostics;

import java.util.Map;

public record DiagnosticRecommendation(
    String severity,
    String summary,
    String suggestedActionId,
    String suggestedToolName,
    Map<String, Object> suggestedActionArgs,
    String suggestedSql
) {}
