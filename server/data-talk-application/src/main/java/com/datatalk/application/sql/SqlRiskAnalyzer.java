package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;

public interface SqlRiskAnalyzer {

    default SqlRiskAnalysis analyze(String sql, Category category) {
        return analyze(sql, category, null);
    }

    SqlRiskAnalysis analyze(String sql, Category category, String connectionKind);
}
