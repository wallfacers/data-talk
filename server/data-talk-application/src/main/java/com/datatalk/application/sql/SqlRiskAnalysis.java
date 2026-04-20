package com.datatalk.application.sql;

import com.datatalk.domain.action.RiskLevel;

public record SqlRiskAnalysis(
    RiskLevel riskLevel,
    String reason,
    boolean requiresStrongConfirmation,
    boolean fallbackUsed
) {

    public static SqlRiskAnalysis low(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L1, reason, false, false);
    }

    public static SqlRiskAnalysis medium(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L2, reason, false, false);
    }

    public static SqlRiskAnalysis high(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L3, reason, true, false);
    }

    public static SqlRiskAnalysis fallback(String reason) {
        return new SqlRiskAnalysis(null, reason, false, true);
    }
}
