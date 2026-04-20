package com.datatalk.domain.action;

public record SqlExecutionRisk(
    RiskLevel riskLevel,
    String reason,
    boolean requiresStrongConfirmation,
    boolean fallbackUsed
) {}
