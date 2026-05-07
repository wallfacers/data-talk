package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionKind;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CalciteSqlRiskAnalyzer implements SqlRiskAnalyzer {

    private static final Set<String> SQLITE_READ_ONLY_PRAGMAS = Set.of(
        "application_id",
        "collation_list",
        "compile_options",
        "database_list",
        "foreign_key_list",
        "freelist_count",
        "index_info",
        "index_list",
        "index_xinfo",
        "integrity_check",
        "page_count",
        "pragma_list",
        "quick_check",
        "schema_version",
        "table_info",
        "table_list",
        "table_xinfo",
        "user_version"
    );

    private static final Pattern SQLITE_PRAGMA_NAME_PATTERN =
        Pattern.compile("(?is)^pragma\\s+([\\w.]+)");

    private static final Set<String> DUCKDB_READ_ONLY_PRAGMAS = Set.of(
        "database_list",
        "database_size",
        "show_databases",
        "table_info",
        "storage_info",
        "version"
    );

    private static final Set<String> DUCKDB_DANGEROUS_FUNCTIONS = Set.of(
        "read_csv",
        "read_csv_auto",
        "read_parquet",
        "read_json",
        "read_json_auto",
        "glob",
        "parquet_metadata",
        "parquet_scan",
        "curl",
        "http_get",
        "http_post"
    );

    private static final Set<String> CLICKHOUSE_DANGEROUS_TABLE_FUNCTIONS = Set.of(
        "remote",
        "remotesecure",
        "url",
        "s3",
        "s3cluster",
        "file",
        "hdfs",
        "postgresql",
        "mysql",
        "mongodb",
        "odbc",
        "jdbc",
        "cluster",
        "clusterallreplicas"
    );

    private static final Pattern DUCKDB_PRAGMA_NAME_PATTERN =
        Pattern.compile("(?is)^pragma\\s+([\\w.]+)");

    private final SqlStatementSplitters statementSplitters;

    public CalciteSqlRiskAnalyzer(SqlStatementSplitters statementSplitters) {
        this.statementSplitters = statementSplitters;
    }

    @Override
    public SqlRiskAnalysis analyze(String sql, Category category, String connectionKind) {
        if (connectionKind == null || connectionKind.isBlank()) {
            return analyzeWithoutKind(sql, category);
        }
        List<String> statements = statementSplitters.split(connectionKind, sql);
        if (statements.isEmpty()) {
            return fallbackFor(category, sql, "empty");
        }
        return aggregateAnalyses(statements, category, connectionKind);
    }

    private SqlRiskAnalysis analyzeWithoutKind(String sql, Category category) {
        try {
            SqlNodeList statements = SqlParser.create(sql).parseStmtList();
            return aggregateParsedStatements(statements, category, sql);
        } catch (SqlParseException e) {
            return fallbackFor(category, sql, e.getMessage());
        }
    }

    private SqlRiskAnalysis aggregateAnalyses(List<String> statements, Category category, String connectionKind) {
        SqlRiskAnalysis aggregate = null;
        var unionObjects = new LinkedHashSet<String>();
        for (String statement : statements) {
            SqlRiskAnalysis current = analyzeStatement(statement, category, connectionKind);
            unionObjects.addAll(current.affectedObjects());
            aggregate = aggregate == null ? current : max(aggregate, current);
        }
        if (aggregate == null) {
            return fallbackFor(category, String.join(";\n", statements), "empty");
        }
        return unionObjects.isEmpty() ? aggregate : aggregate.withAffectedObjects(List.copyOf(unionObjects));
    }

    private SqlRiskAnalysis analyzeStatement(String sql, Category category, String connectionKind) {
        SqlRiskAnalysis dialectSpecific = classifyDialectSpecific(sql, connectionKind);
        if (dialectSpecific != null) {
            return dialectSpecific;
        }
        try {
            SqlNodeList statements = SqlParser.create(sql).parseStmtList();
            return aggregateParsedStatements(statements, category, sql);
        } catch (SqlParseException e) {
            return fallbackFor(category, sql, e.getMessage());
        }
    }

    private SqlRiskAnalysis aggregateParsedStatements(SqlNodeList statements, Category category, String rawSql) {
        SqlRiskAnalysis aggregate = null;
        var unionObjects = new LinkedHashSet<String>();
        for (SqlNode statement : statements) {
            SqlRiskAnalysis current = classify(statement);
            unionObjects.addAll(current.affectedObjects());
            aggregate = aggregate == null ? current : max(aggregate, current);
        }
        if (aggregate == null) {
            return fallbackFor(category, rawSql, "empty");
        }
        return unionObjects.isEmpty() ? aggregate : aggregate.withAffectedObjects(List.copyOf(unionObjects));
    }

    private SqlRiskAnalysis classifyDialectSpecific(String sql, String connectionKind) {
        if (ConnectionKind.SQLITE.equalsIgnoreCase(connectionKind)) {
            return classifySqliteSpecific(sql);
        }
        if (ConnectionKind.ORACLE.equalsIgnoreCase(connectionKind)) {
            return classifyOracleSpecific(sql);
        }
        if (ConnectionKind.SQLSERVER.equalsIgnoreCase(connectionKind)) {
            return classifySqlServerSpecific(sql);
        }
        if (ConnectionKind.DUCKDB.equalsIgnoreCase(connectionKind)) {
            return classifyDuckDbSpecific(sql);
        }
        if (ConnectionKind.CLICKHOUSE.equalsIgnoreCase(connectionKind)) {
            return classifyClickhouseSpecific(sql);
        }
        return null;
    }

    private SqlRiskAnalysis classifySqliteSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (normalized.startsWith("explain query plan")) {
            return SqlRiskAnalysis.low("explain_query_plan");
        }
        Matcher pragma = SQLITE_PRAGMA_NAME_PATTERN.matcher(normalized);
        if (pragma.find()) {
            String pragmaName = normalizeSqlitePragmaName(pragma.group(1));
            if (!normalized.contains("=") && SQLITE_READ_ONLY_PRAGMAS.contains(pragmaName)) {
                return SqlRiskAnalysis.low("pragma_" + pragmaName);
            }
            return SqlRiskAnalysis.high("sqlite_file_or_maintenance_command");
        }
        if (normalized.startsWith("attach ")
            || normalized.startsWith("detach ")
            || normalized.startsWith("vacuum")
            || normalized.startsWith("reindex")) {
            return SqlRiskAnalysis.high("sqlite_file_or_maintenance_command");
        }
        return null;
    }

    private SqlRiskAnalysis classifyOracleSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        // EXPLAIN PLAN FOR is read-only (L1)
        if (normalized.startsWith("explain plan")) {
            return SqlRiskAnalysis.low("explain_plan");
        }
        // Oracle high-risk commands
        if (normalized.startsWith("merge ")
            || normalized.startsWith("merge\t")) {
            return SqlRiskAnalysis.high("oracle_merge");
        }
        if (normalized.startsWith("call ")) {
            return SqlRiskAnalysis.high("oracle_call");
        }
        if (normalized.startsWith("begin ")
            || normalized.startsWith("declare ")) {
            return SqlRiskAnalysis.high("oracle_plsql_block");
        }
        if (normalized.startsWith("truncate ")) {
            return SqlRiskAnalysis.high("truncate");
        }
        return null;
    }

    private SqlRiskAnalysis classifySqlServerSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        // MERGE statement
        if (normalized.startsWith("merge ")
            || normalized.startsWith("merge\t")) {
            return SqlRiskAnalysis.high("sqlserver_merge");
        }
        // EXEC / EXECUTE (stored procedure execution)
        if (normalized.startsWith("exec ")
            || normalized.startsWith("exec\t")
            || normalized.startsWith("execute ")
            || normalized.startsWith("execute\t")) {
            return SqlRiskAnalysis.high("sqlserver_exec");
        }
        // BACKUP / RESTORE
        if (normalized.startsWith("backup ")
            || normalized.startsWith("restore ")) {
            return SqlRiskAnalysis.high("sqlserver_backup_restore");
        }
        // DBCC
        if (normalized.startsWith("dbcc ")) {
            return SqlRiskAnalysis.high("sqlserver_dbcc");
        }
        // KILL
        if (normalized.startsWith("kill ")
            || normalized.startsWith("kill\t")) {
            return SqlRiskAnalysis.high("sqlserver_kill");
        }
        // DENY (SQL Server-specific permission command alongside GRANT/REVOKE)
        if (normalized.startsWith("deny ")) {
            return SqlRiskAnalysis.high("sqlserver_deny");
        }
        return null;
    }

    private SqlRiskAnalysis classifyDuckDbSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        // EXPLAIN → L1
        if (normalized.startsWith("explain ")) {
            return SqlRiskAnalysis.low("explain");
        }
        // DESCRIBE → L1
        if (normalized.startsWith("describe ") || normalized.startsWith("describe\t")) {
            return SqlRiskAnalysis.low("describe");
        }

        // PRAGMA handling
        Matcher pragma = DUCKDB_PRAGMA_NAME_PATTERN.matcher(normalized);
        if (pragma.find()) {
            String pragmaName = normalizeSqlitePragmaName(pragma.group(1));
            // Configuration-setting PRAGMAs contain '=' — always high risk
            if (normalized.contains("=")) {
                return SqlRiskAnalysis.high("duckdb_config_pragma");
            }
            // Read-only PRAGMAs → L1
            if (DUCKDB_READ_ONLY_PRAGMAS.contains(pragmaName)) {
                return SqlRiskAnalysis.low("pragma_" + pragmaName);
            }
            // Unknown PRAGMA without '=' — treat as config pragma (safe default)
            return SqlRiskAnalysis.high("duckdb_config_pragma");
        }

        // ATTACH / DETACH
        if (normalized.startsWith("attach ") || normalized.startsWith("detach ")) {
            return SqlRiskAnalysis.high("duckdb_attach");
        }

        // COPY (to/from file)
        if (normalized.startsWith("copy ")) {
            return SqlRiskAnalysis.high("duckdb_copy");
        }

        // EXPORT / IMPORT DATABASE
        if (normalized.startsWith("export database")) {
            return SqlRiskAnalysis.high("duckdb_export_import");
        }
        if (normalized.startsWith("import database")) {
            return SqlRiskAnalysis.high("duckdb_export_import");
        }

        // INSTALL / LOAD (extensions)
        if (normalized.startsWith("install ") || normalized.startsWith("load ")) {
            return SqlRiskAnalysis.high("duckdb_extension");
        }

        // CREATE SECRET
        if (normalized.startsWith("create secret")) {
            return SqlRiskAnalysis.high("duckdb_secret");
        }

        // Dangerous functions in FROM clauses (read_csv, read_parquet, etc.)
        // Match patterns like: FROM read_csv(...), FROM read_parquet(...), FROM glob(...)
        if (normalized.contains(" from ")) {
            for (String fn : DUCKDB_DANGEROUS_FUNCTIONS) {
                if (normalized.contains(" from " + fn + "(") || normalized.contains(" from " + fn + " (")) {
                    return SqlRiskAnalysis.high("duckdb_file_access");
                }
            }
        }

        return null;
    }

    private SqlRiskAnalysis classifyClickhouseSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        // L1 safe: EXPLAIN
        if (normalized.startsWith("explain ")) {
            return SqlRiskAnalysis.low("clickhouse_explain");
        }
        // L1 safe: DESCRIBE / DESC
        if (normalized.startsWith("describe ") || normalized.startsWith("describe\t")
            || normalized.startsWith("desc ") || normalized.startsWith("desc\t")) {
            return SqlRiskAnalysis.low("clickhouse_describe");
        }

        // L2 mutation: safe CREATE TABLE (no AS SELECT, not CREATE OR REPLACE)
        if (normalized.startsWith("create table ") && !normalized.contains(" as ")) {
            return SqlRiskAnalysis.medium("clickhouse_create_table");
        }

        // L3 destructive: CREATE DICTIONARY
        if (normalized.startsWith("create dictionary")) {
            return SqlRiskAnalysis.high("clickhouse_create_dictionary");
        }
        // L3 destructive: CREATE USER
        if (normalized.startsWith("create user")) {
            return SqlRiskAnalysis.high("clickhouse_create_user");
        }
        // L3 destructive: CREATE ROLE
        if (normalized.startsWith("create role")) {
            return SqlRiskAnalysis.high("clickhouse_create_role");
        }

        // L3 destructive: TRUNCATE
        if (normalized.startsWith("truncate ") || normalized.startsWith("truncate\t")) {
            return SqlRiskAnalysis.high("clickhouse_truncate");
        }
        // L3 destructive: RENAME
        if (normalized.startsWith("rename table")) {
            return SqlRiskAnalysis.high("clickhouse_rename");
        }
        // L3 destructive: ALTER
        if (normalized.startsWith("alter ")) {
            return SqlRiskAnalysis.high("clickhouse_alter");
        }
        // L3 destructive: GRANT
        if (normalized.startsWith("grant ")) {
            return SqlRiskAnalysis.high("clickhouse_grant");
        }
        // L3 destructive: REVOKE
        if (normalized.startsWith("revoke ")) {
            return SqlRiskAnalysis.high("clickhouse_revoke");
        }

        // Hard reject: KILL
        if (normalized.startsWith("kill ")) {
            return SqlRiskAnalysis.high("clickhouse_kill");
        }
        // Hard reject: SYSTEM
        if (normalized.startsWith("system ")) {
            return SqlRiskAnalysis.high("clickhouse_system");
        }
        // Hard reject: OPTIMIZE
        if (normalized.startsWith("optimize ")) {
            return SqlRiskAnalysis.high("clickhouse_optimize");
        }
        // Hard reject: ATTACH / DETACH (ClickHouse table attach/detach, not SQLite)
        if (normalized.startsWith("attach ")) {
            return SqlRiskAnalysis.high("clickhouse_attach");
        }
        if (normalized.startsWith("detach ")) {
            return SqlRiskAnalysis.high("clickhouse_detach");
        }

        // Hard reject: SELECT-shaped external table functions (s3, url, file, remote, etc.)
        if (normalized.contains(" from ")) {
            for (String fn : CLICKHOUSE_DANGEROUS_TABLE_FUNCTIONS) {
                if (normalized.contains(" from " + fn + "(") || normalized.contains(" from " + fn + " (")) {
                    return SqlRiskAnalysis.high("clickhouse_external_access");
                }
            }
        }

        return null;
    }

    private String stripLeadingComments(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        while (!normalized.isEmpty()) {
            if (normalized.startsWith("--")) {
                int newline = normalized.indexOf('\n');
                normalized = newline >= 0 ? normalized.substring(newline + 1).trim() : "";
                continue;
            }
            if (normalized.startsWith("/*")) {
                int end = normalized.indexOf("*/");
                normalized = end >= 0 ? normalized.substring(end + 2).trim() : "";
                continue;
            }
            return normalized;
        }
        return normalized;
    }

    private String normalizeSqlitePragmaName(String pragmaName) {
        String normalized = pragmaName == null ? "" : pragmaName.trim().toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        return lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
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
