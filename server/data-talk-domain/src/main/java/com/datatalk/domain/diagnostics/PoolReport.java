package com.datatalk.domain.diagnostics;

import java.util.List;

public record PoolReport(
    String scope,
    Integer activeConnections,
    Integer idleConnections,
    Integer maxConnections,
    Integer threadsRunning,
    Integer waitingConnections,
    String identifier,
    List<DiagnosticRecommendation> recommendations
) {}
