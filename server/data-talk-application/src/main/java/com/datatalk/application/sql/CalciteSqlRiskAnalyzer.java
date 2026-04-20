package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.springframework.stereotype.Component;

@Component
public class CalciteSqlRiskAnalyzer implements SqlRiskAnalyzer {

    @Override
    public SqlRiskAnalysis analyze(String sql, Category category) {
        try {
            SqlNodeList statements = SqlParser.create(sql).parseStmtList();
            SqlRiskAnalysis aggregate = null;
            for (SqlNode statement : statements) {
                SqlRiskAnalysis current = classify(statement);
                aggregate = aggregate == null ? current : max(aggregate, current);
            }
            return aggregate == null ? fallbackFor(category, "empty") : aggregate;
        } catch (SqlParseException e) {
            return fallbackFor(category, e.getMessage());
        }
    }

    private SqlRiskAnalysis fallbackFor(Category category, String reason) {
        if (category == Category.MUTATION || category == Category.DDL) {
            return SqlRiskAnalysis.high("parse_failed:" + reason);
        }
        return SqlRiskAnalysis.fallback("parse_failed:" + reason);
    }

    private SqlRiskAnalysis classify(SqlNode node) {
        if (node instanceof SqlWith with) {
            return max(SqlRiskAnalysis.high("with_dml"), classify(with.body));
        }
        if (node instanceof SqlOrderBy orderBy) {
            return classify(orderBy.query);
        }
        if (node instanceof SqlSelect) {
            return SqlRiskAnalysis.low("select");
        }
        if (node instanceof SqlInsert) {
            return SqlRiskAnalysis.medium("insert");
        }
        if (node instanceof SqlUpdate update) {
            return update.getCondition() == null
                ? SqlRiskAnalysis.high("update_without_where")
                : SqlRiskAnalysis.medium("update_with_where");
        }
        if (node instanceof SqlDelete) {
            return SqlRiskAnalysis.high("delete");
        }

        String kind = node.getKind().name();
        if ("EXPLAIN".equals(kind) || "DESCRIBE_TABLE".equals(kind) || "DESCRIBE_SCHEMA".equals(kind)) {
            return SqlRiskAnalysis.low(kind.toLowerCase());
        }
        if ("CREATE_VIEW".equals(kind) || "CREATE_INDEX".equals(kind)) {
            return SqlRiskAnalysis.medium(kind.toLowerCase());
        }
        if (kind.startsWith("DROP")
            || kind.startsWith("ALTER")
            || kind.startsWith("TRUNCATE")
            || kind.startsWith("GRANT")
            || kind.startsWith("REVOKE")) {
            return SqlRiskAnalysis.high(kind.toLowerCase());
        }
        if (kind.contains("QUERY") || kind.contains("SELECT") || kind.contains("SHOW")) {
            return SqlRiskAnalysis.low(kind.toLowerCase());
        }
        return SqlRiskAnalysis.high("unclassified:" + kind.toLowerCase());
    }

    private SqlRiskAnalysis max(SqlRiskAnalysis left, SqlRiskAnalysis right) {
        if (left.riskLevel() == null) return right;
        if (right.riskLevel() == null) return left;
        return left.riskLevel().ordinal() >= right.riskLevel().ordinal() ? left : right;
    }
}
