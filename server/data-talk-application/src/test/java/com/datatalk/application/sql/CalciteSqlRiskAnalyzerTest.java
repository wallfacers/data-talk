package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalciteSqlRiskAnalyzerTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer();

    @Test
    void classifiesSelectAsL1() {
        SqlRiskAnalysis analysis = analyzer.analyze("SELECT * FROM orders", Category.QUERY);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesInsertAsL2() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "INSERT INTO orders(id, status) VALUES (1, 'new')", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesUpdateWithWhereAsL2() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "UPDATE orders SET status = 'done' WHERE id = 1", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void classifiesUpdateWithoutWhereAsL3() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "UPDATE orders SET status = 'done'", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void classifiesWithUpdateAsL3() {
        SqlRiskAnalysis analysis = analyzer.analyze(
            "WITH stale AS (SELECT id FROM orders) UPDATE orders SET status = 'x' WHERE id IN (SELECT id FROM stale)",
            Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void classifiesDropAsL3() {
        SqlRiskAnalysis analysis = analyzer.analyze("DROP TABLE orders", Category.DDL);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }

    @Test
    void queryParseFailureFallsBackWithoutRisk() {
        SqlRiskAnalysis analysis = analyzer.analyze("SELECT FROM", Category.QUERY);

        assertThat(analysis.riskLevel()).isNull();
        assertThat(analysis.fallbackUsed()).isTrue();
        assertThat(analysis.requiresStrongConfirmation()).isFalse();
    }

    @Test
    void mutationParseFailureEscalatesToL3() {
        SqlRiskAnalysis analysis = analyzer.analyze("UPDATE", Category.MUTATION);

        assertThat(analysis.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(analysis.fallbackUsed()).isFalse();
        assertThat(analysis.requiresStrongConfirmation()).isTrue();
    }
}
