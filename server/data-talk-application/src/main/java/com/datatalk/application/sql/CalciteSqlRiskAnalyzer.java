package com.datatalk.application.sql;

import com.datatalk.domain.action.Category;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUpdate;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CalciteSqlRiskAnalyzer implements SqlRiskAnalyzer {

    @Override
    public SqlRiskAnalysis analyze(String sql, Category category) {
        SqlRiskAnalysis dialectSpecific = classifyDialectSpecific(sql);
        if (dialectSpecific != null) {
            return dialectSpecific;
        }
        try {
            SqlNodeList statements = SqlParser.create(sql).parseStmtList();
            SqlRiskAnalysis aggregate = null;
            var unionObjects = new LinkedHashSet<String>();
            for (SqlNode statement : statements) {
                SqlRiskAnalysis current = classify(statement);
                unionObjects.addAll(current.affectedObjects());
                aggregate = aggregate == null ? current : max(aggregate, current);
            }
            if (aggregate == null) {
                return fallbackFor(category, sql, "empty");
            }
            return unionObjects.isEmpty() ? aggregate : aggregate.withAffectedObjects(List.copyOf(unionObjects));
        } catch (SqlParseException e) {
            return fallbackFor(category, sql, e.getMessage());
        }
    }

    private SqlRiskAnalysis classifyDialectSpecific(String sql) {
        String normalized = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (normalized.startsWith("explain query plan")) {
            return SqlRiskAnalysis.low("explain_query_plan");
        }
        if (normalized.matches("(?s)^pragma\\s+table_info\\s*\\([^;]+\\)\\s*;?\\s*$")) {
            return SqlRiskAnalysis.low("pragma_table_info");
        }
        if (normalized.startsWith("attach ")
            || normalized.startsWith("detach ")
            || normalized.startsWith("vacuum")
            || normalized.startsWith("reindex")
            || normalized.startsWith("pragma ")) {
            return SqlRiskAnalysis.high("sqlite_file_or_maintenance_command");
        }
        return null;
    }

    private static final List<Pattern> DDL_OBJECT_PATTERNS = List.of(
        Pattern.compile("(?i)DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(\\w+)"),
        Pattern.compile("(?i)ALTER\\s+TABLE\\s+(\\w+)"),
        Pattern.compile("(?i)TRUNCATE\\s+(?:TABLE\\s+)?(\\w+)"),
        Pattern.compile("(?i)GRANT\\s+\\S+\\s+ON\\s+(\\w+)"),
        Pattern.compile("(?i)REVOKE\\s+\\S+\\s+ON\\s+(\\w+)")
    );

    private SqlRiskAnalysis fallbackFor(Category category, String sql, String reason) {
        var objects = extractDdlObjects(sql);
        if (category == Category.MUTATION || category == Category.DDL) {
            return SqlRiskAnalysis.high("parse_failed:" + reason).withAffectedObjects(objects);
        }
        return SqlRiskAnalysis.fallback("parse_failed:" + reason).withAffectedObjects(objects);
    }

    private List<String> extractDdlObjects(String sql) {
        var names = new ArrayList<String>();
        for (Pattern p : DDL_OBJECT_PATTERNS) {
            Matcher m = p.matcher(sql);
            while (m.find()) {
                names.add(m.group(1).toLowerCase());
            }
        }
        return names.isEmpty() ? List.of() : List.copyOf(names);
    }

    private SqlRiskAnalysis classify(SqlNode node) {
        if (node instanceof SqlWith with) {
            // The WITH wrapper inherits the body's risk so that
            // `WITH cte AS (...) UPDATE t SET ... WHERE id = ?` stays L2,
            // matching the spec's "L2 stays L2, L3 stays L3" rule.
            return classify(with.body);
        }
        if (node instanceof SqlOrderBy orderBy) {
            return classify(orderBy.query);
        }
        if (node instanceof SqlSelect select) {
            return SqlRiskAnalysis.low("select").withAffectedObjects(extractObjects(select));
        }
        if (node instanceof SqlInsert insert) {
            return SqlRiskAnalysis.medium("insert").withAffectedObjects(extractObjects(insert.getTargetTable()));
        }
        if (node instanceof SqlUpdate update) {
            var base = update.getCondition() == null
                ? SqlRiskAnalysis.high("update_without_where")
                : SqlRiskAnalysis.medium("update_with_where");
            return base.withAffectedObjects(extractObjects(update.getTargetTable()));
        }
        if (node instanceof SqlDelete delete) {
            var base = delete.getCondition() == null
                ? SqlRiskAnalysis.high("delete_without_where")
                : SqlRiskAnalysis.medium("delete_with_where");
            return base.withAffectedObjects(extractObjects(delete.getTargetTable()));
        }

        String kind = node.getKind().name();
        if ("EXPLAIN".equals(kind) || "DESCRIBE_TABLE".equals(kind) || "DESCRIBE_SCHEMA".equals(kind)) {
            return SqlRiskAnalysis.low(kind.toLowerCase());
        }
        if ("CREATE_VIEW".equals(kind) || "CREATE_INDEX".equals(kind)) {
            return SqlRiskAnalysis.medium(kind.toLowerCase()).withAffectedObjects(extractObjects(node));
        }
        if (kind.startsWith("DROP")
            || kind.startsWith("ALTER")
            || kind.startsWith("TRUNCATE")
            || kind.startsWith("GRANT")
            || kind.startsWith("REVOKE")) {
            return SqlRiskAnalysis.high(kind.toLowerCase()).withAffectedObjects(extractObjects(node));
        }
        if (kind.contains("QUERY") || kind.contains("SELECT") || kind.contains("SHOW")) {
            return SqlRiskAnalysis.low(kind.toLowerCase());
        }
        return SqlRiskAnalysis.high("unclassified:" + kind.toLowerCase());
    }

    private List<String> extractObjects(SqlNode node) {
        if (node == null) return List.of();
        var names = new LinkedHashSet<String>();
        try {
            node.accept(new SqlBasicVisitor<Void>() {
                @Override
                public Void visit(SqlIdentifier id) {
                    if (!id.names.isEmpty()) {
                        names.add(id.names.get(id.names.size() - 1).toLowerCase());
                    }
                    return null;
                }
            });
        } catch (Exception ignored) {
            // best-effort extraction
        }
        return List.copyOf(names);
    }

    private SqlRiskAnalysis max(SqlRiskAnalysis left, SqlRiskAnalysis right) {
        if (left.riskLevel() == null) return right;
        if (right.riskLevel() == null) return left;
        return left.riskLevel().ordinal() >= right.riskLevel().ordinal() ? left : right;
    }
}
