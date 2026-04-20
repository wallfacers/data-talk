package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;

public interface SqlRiskAnalyzer {

    SqlRiskAnalysis analyze(String sql, Category category);
}
