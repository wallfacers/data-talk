package com.datatalk.domain.action;

import java.util.List;
import java.util.Map;

public record ActionDescriptor(
    String id,
    Executor executor,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<String> produces,
    List<OntologyEffect> sideEffects,
    boolean requiresConnection,
    int timeoutMs,
    RiskLevel riskLevel,   // nullable — see product spec §3.4
    Category category      // nullable — see product spec §3.4
) {}
