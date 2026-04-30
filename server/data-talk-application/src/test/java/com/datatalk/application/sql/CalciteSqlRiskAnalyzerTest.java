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
        // Calcite's default SQL parser does not understand WITH ... UPDATE,
        // so this lands in the parse-failure fallback path. For Category.MUTATION
        // that fallback escalates to L3 unconditionally — the safe choice when
        // the AST cannot be inspected.
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

    @Test
    void deleteWithWhereIsMedium() {
        var result = analyzer.analyze("DELETE FROM users WHERE id = 1", Category.QUERY);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.reason()).isEqualTo("delete_with_where");
        assertThat(result.affectedObjects()).containsExactly("users");
    }

    @Test
    void deleteWithoutWhereIsHigh() {
        var result = analyzer.analyze("DELETE FROM users", Category.QUERY);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).isEqualTo("delete_without_where");
        assertThat(result.affectedObjects()).containsExactly("users");
    }

    @Test
    void updateWithWhereExposesAffectedObjects() {
        var result = analyzer.analyze("UPDATE orders SET status = 'paid' WHERE id = 9", Category.MUTATION);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(result.affectedObjects()).containsExactly("orders");
    }

    @Test
    void dropTableExposesAffectedObjects() {
        var result = analyzer.analyze("DROP TABLE temp_log", Category.MUTATION);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.affectedObjects()).containsExactly("temp_log");
    }

    @Test
    void multiStatementBatchUsesHighestRiskAndUnionAffectedObjects() {
        var result = analyzer.analyze(
            "UPDATE orders SET note = 'x' WHERE id = 1; DELETE FROM logs;",
            Category.MUTATION
        );
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.affectedObjects()).containsExactlyInAnyOrder("orders", "logs");
    }

    @Test
    void createViewLandsInDdlFallback() {
        // Calcite's default SQL parser does not parse CREATE VIEW; the path is
        // covered by the DDL parse-failure fallback (L3). The dedicated
        // CREATE_VIEW classifier branch with affectedObjects extraction stays
        // wired so that future parser upgrades (or dialect-aware splitters)
        // get the L2 + extracted-objects treatment for free.
        var result = analyzer.analyze(
            "CREATE VIEW active_users AS SELECT id FROM users WHERE active = TRUE",
            Category.DDL);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
        assertThat(result.reason()).startsWith("parse_failed:");
    }

    @Test
    void sqliteExplainQueryPlanIsReadOnly() {
        var result = analyzer.analyze("EXPLAIN QUERY PLAN SELECT * FROM users", Category.QUERY);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("explain_query_plan");
    }

    @Test
    void sqlitePragmaTableInfoIsReadOnly() {
        var result = analyzer.analyze("PRAGMA table_info(users)", Category.QUERY);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(result.reason()).isEqualTo("pragma_table_info");
    }

    @Test
    void sqliteAttachDetachAndVacuumAreHighRiskEvenFromQueryCategory() {
        assertThat(analyzer.analyze("ATTACH DATABASE 'other.db' AS other", Category.QUERY).riskLevel())
            .isEqualTo(RiskLevel.L3);
        assertThat(analyzer.analyze("DETACH DATABASE other", Category.QUERY).riskLevel())
            .isEqualTo(RiskLevel.L3);
        assertThat(analyzer.analyze("VACUUM", Category.QUERY).riskLevel())
            .isEqualTo(RiskLevel.L3);
    }
}
