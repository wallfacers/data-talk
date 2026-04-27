package com.datatalk.application.sql;

import com.datatalk.domain.action.RiskLevel;
import java.util.List;

public record SqlRiskAnalysis(
    RiskLevel riskLevel,
    String reason,
    boolean requiresStrongConfirmation,
    boolean fallbackUsed,
    List<String> affectedObjects
) {

    public static SqlRiskAnalysis low(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L1, reason, false, false, List.of());
    }

    public static SqlRiskAnalysis medium(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L2, reason, false, false, List.of());
    }

    public static SqlRiskAnalysis high(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L3, reason, true, false, List.of());
    }

    public static SqlRiskAnalysis fallback(String reason) {
        return new SqlRiskAnalysis(null, reason, false, true, List.of());
    }

    public SqlRiskAnalysis withAffectedObjects(List<String> objects) {
        return new SqlRiskAnalysis(
            riskLevel, reason, requiresStrongConfirmation, fallbackUsed,
            objects == null ? List.of() : List.copyOf(objects)
        );
    }
}
