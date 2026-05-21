package com.datatalk.domain.diagnostics;

import java.util.List;

public record ExplainPlan(
    String dialect,
    String rawText,
    List<ExplainNode> nodes,
    Double totalCostEstimate,
    List<String> warnings
) {}
