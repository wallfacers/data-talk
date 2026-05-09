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
import java.util.Optional;
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

    // ====== Dameng Channel 1 — 5 anchored L3 admin command patterns ======
    private static final Pattern DAMENG_TABLESPACE_DDL = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+TABLESPACE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_USER_DDL = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+USER\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_ROLE_DDL = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+ROLE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_GRANT_REVOKE = Pattern.compile(
        "^\\s*(GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_DROP_OBJECT = Pattern.compile(
        "^\\s*DROP\\s+(TABLE|VIEW|INDEX|SEQUENCE|SYNONYM)\\b", Pattern.CASE_INSENSITIVE);

    // ====== Dameng Channel 2 — 3 anchored dialect_unsupported patterns ======
    private static final Pattern DAMENG_PLSQL_BLOCK = Pattern.compile(
        "^\\s*(DECLARE|BEGIN)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_PROCEDURE_DDL = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)(\\s+OR\\s+REPLACE)?\\s+(PROCEDURE|FUNCTION|TRIGGER|PACKAGE(\\s+BODY)?)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMENG_EXP_IMP = Pattern.compile(
        "^\\s*(EXP|IMP)\\s+", Pattern.CASE_INSENSITIVE);

    // ==== Channel 1 — KingbaseES admin / DDL L3 patterns ====
    private static final Pattern KINGBASE_SYS_TABLE_DDL =
        Pattern.compile("^\\s*(DROP|ALTER|TRUNCATE)\\s+(TABLE\\s+)?SYS_[A-Z_]+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern KINGBASE_SYS_ADMIN_SCHEMA_DDL =
        Pattern.compile("^\\s*(DROP|ALTER)\\s+(TABLE\\s+)?SYS(CRT|AUDIT)_[A-Z_]+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern KINGBASE_SYS_KILL =
        Pattern.compile("^\\s*SELECT\\s+SYS_KILL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern KINGBASE_FLASHBACK =
        Pattern.compile("^\\s*FLASHBACK\\s+TABLE\\b", Pattern.CASE_INSENSITIVE);

    // ==== Channel 2 — KingbaseES Day-1 dialect_unsupported patterns ====
    private static final Pattern KINGBASE_KB_BACKUP_RESTORE =
        Pattern.compile("^\\s*(KBBACKUP|KBRESTORE)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern KINGBASE_ORACLE_PLSQL_BLOCK =
        Pattern.compile("^\\s*(DECLARE|BEGIN)\\b", Pattern.CASE_INSENSITIVE);

    public enum DamengUnsupportedReason {
        PLSQL_BLOCK,
        PROCEDURE_DDL,
        EXP_IMP_COMMAND
    }

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
        if (ConnectionKind.APACHE_DORIS.equalsIgnoreCase(connectionKind)) {
            return classifyDorisSpecific(sql);
        }
        if (ConnectionKind.STARROCKS.equalsIgnoreCase(connectionKind)) {
            return classifyStarrocksSpecific(sql);
        }
        if (ConnectionKind.TRINO.equalsIgnoreCase(connectionKind)) {
            return classifyTrinoSpecific(sql);
        }
        if (ConnectionKind.PRESTO.equalsIgnoreCase(connectionKind)) {
            return classifyPrestoSpecific(sql);
        }
        if (ConnectionKind.HIVE.equalsIgnoreCase(connectionKind)) {
            return classifyHiveSpecific(sql);
        }
        if (ConnectionKind.TIDB.equalsIgnoreCase(connectionKind)) {
            return classifyTidbSpecific(sql);
        }
        if (ConnectionKind.OCEANBASE.equalsIgnoreCase(connectionKind)) {
            return classifyOceanBaseSpecific(sql);
        }
        if (ConnectionKind.DAMENG.equalsIgnoreCase(connectionKind)) {
            return classifyDamengSpecific(sql);
        }
        if (ConnectionKind.KINGBASE.equalsIgnoreCase(connectionKind)) {
            return classifyKingbaseSpecific(sql);
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

        // L1 safe: SHOW
        if (normalized.startsWith("show ")) {
            return SqlRiskAnalysis.low("clickhouse_show");
        }
        // L1 safe: EXPLAIN
        if (normalized.startsWith("explain ")) {
            return SqlRiskAnalysis.low("clickhouse_explain");
        }
        // L1 safe: DESCRIBE / DESC
        if (normalized.startsWith("describe ") || normalized.startsWith("describe\t")
            || normalized.startsWith("desc ") || normalized.startsWith("desc\t")) {
            return SqlRiskAnalysis.low("clickhouse_describe");
        }

        // Hard reject: external table functions (s3, url, file, remote, etc.)
        // Must run before CREATE TABLE so that CREATE TABLE ... AS SELECT FROM s3(...)
        // is correctly classified as L3 rather than L2.
        if (normalized.contains(" from ")) {
            for (String fn : CLICKHOUSE_DANGEROUS_TABLE_FUNCTIONS) {
                if (normalized.contains(" from " + fn + "(") || normalized.contains(" from " + fn + " (")) {
                    return SqlRiskAnalysis.high("clickhouse_external_access");
                }
            }
        }

        // L2 mutation: safe CREATE TABLE
        // CREATE OR REPLACE TABLE does not startWith "create table " so it naturally
        // fallthroughs to Calcite → unclassified HIGH.
        // Column-level AS (MATERIALIZED/DEFAULT expressions) and statement-level
        // AS SELECT are both L2; external functions in AS SELECT are caught above.
        if (normalized.startsWith("create table ")) {
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

        return null;
    }

    private SqlRiskAnalysis classifyDorisSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("doris_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("doris_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("doris_explain");
        }
        if (startsWithKeyword(normalized, "create table")) {
            return SqlRiskAnalysis.medium("doris_create_table");
        }
        if (startsWithKeyword(normalized, "create index")) {
            return SqlRiskAnalysis.medium("doris_create_index");
        }
        if (startsWithKeyword(normalized, "build index")) {
            return SqlRiskAnalysis.medium("doris_build_index");
        }
        if (startsWithKeyword(normalized, "analyze")) {
            return SqlRiskAnalysis.medium("doris_analyze");
        }
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("doris_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("doris_truncate");
        }
        if (startsWithKeyword(normalized, "alter system")) {
            return SqlRiskAnalysis.high("doris_alter_system");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("doris_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("doris_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("doris_revoke");
        }
        if (startsWithKeyword(normalized, "create user")) {
            return SqlRiskAnalysis.high("doris_create_user");
        }
        if (startsWithKeyword(normalized, "create role")) {
            return SqlRiskAnalysis.high("doris_create_role");
        }
        if (startsWithKeyword(normalized, "rename")) {
            return SqlRiskAnalysis.high("doris_rename");
        }
        if (startsWithKeyword(normalized, "load label")) {
            return SqlRiskAnalysis.high("doris_load");
        }
        if (startsWithKeyword(normalized, "routine load")) {
            return SqlRiskAnalysis.high("doris_routine_load");
        }
        if (startsWithKeyword(normalized, "stream load")) {
            return SqlRiskAnalysis.high("doris_stream_load");
        }
        if (startsWithKeyword(normalized, "cancel load")) {
            return SqlRiskAnalysis.high("doris_cancel_load");
        }
        if (startsWithKeyword(normalized, "export")) {
            return SqlRiskAnalysis.high("doris_export");
        }
        if (startsWithKeyword(normalized, "admin")) {
            return SqlRiskAnalysis.high("doris_admin");
        }
        if (startsWithKeyword(normalized, "shutdown")) {
            return SqlRiskAnalysis.high("doris_shutdown");
        }
        if (startsWithKeyword(normalized, "decommission")) {
            return SqlRiskAnalysis.high("doris_decommission");
        }
        return null;
    }

    /** Match keyword followed by space, tab, or end-of-string. Prevents
     *  \t-separated commands like KILL\tQUERY from bypassing risk rules. */
    private boolean startsWithKeyword(String normalized, String keyword) {
        if (normalized.startsWith(keyword)) {
            int after = keyword.length();
            if (after >= normalized.length()) return true;
            char c = normalized.charAt(after);
            return c == ' ' || c == '\t';
        }
        return false;
    }

    private SqlRiskAnalysis classifyStarrocksSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("starrocks_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("starrocks_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("starrocks_explain");
        }
        if (startsWithKeyword(normalized, "create table")) {
            return SqlRiskAnalysis.medium("starrocks_create_table");
        }
        if (startsWithKeyword(normalized, "create index")) {
            return SqlRiskAnalysis.medium("starrocks_create_index");
        }
        if (startsWithKeyword(normalized, "analyze")) {
            return SqlRiskAnalysis.medium("starrocks_analyze");
        }
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("starrocks_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("starrocks_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("starrocks_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("starrocks_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("starrocks_revoke");
        }
        if (startsWithKeyword(normalized, "create user")) {
            return SqlRiskAnalysis.high("starrocks_create_user");
        }
        if (startsWithKeyword(normalized, "create role")) {
            return SqlRiskAnalysis.high("starrocks_create_role");
        }
        if (startsWithKeyword(normalized, "create catalog") || normalized.startsWith("create external catalog")) {
            return SqlRiskAnalysis.high("starrocks_create_catalog");
        }
        if (startsWithKeyword(normalized, "drop catalog")) {
            return SqlRiskAnalysis.high("starrocks_drop_catalog");
        }
        if (startsWithKeyword(normalized, "load label")) {
            return SqlRiskAnalysis.high("starrocks_load");
        }
        if (startsWithKeyword(normalized, "routine load")) {
            return SqlRiskAnalysis.high("starrocks_routine_load");
        }
        if (startsWithKeyword(normalized, "stream load")) {
            return SqlRiskAnalysis.high("starrocks_stream_load");
        }
        if (startsWithKeyword(normalized, "broker load")) {
            return SqlRiskAnalysis.high("starrocks_broker_load");
        }
        if (startsWithKeyword(normalized, "export")) {
            return SqlRiskAnalysis.high("starrocks_export");
        }
        if (startsWithKeyword(normalized, "cancel load")) {
            return SqlRiskAnalysis.high("starrocks_cancel_load");
        }
        if (startsWithKeyword(normalized, "admin")) {
            return SqlRiskAnalysis.high("starrocks_admin");
        }
        if (startsWithKeyword(normalized, "set global")) {
            return SqlRiskAnalysis.high("starrocks_set_global");
        }
        if (startsWithKeyword(normalized, "set password")) {
            return SqlRiskAnalysis.high("starrocks_set_password");
        }
        if (startsWithKeyword(normalized, "kill")) {
            return SqlRiskAnalysis.high("starrocks_kill");
        }
        if (startsWithKeyword(normalized, "rename")) {
            return SqlRiskAnalysis.high("starrocks_rename");
        }
        if (startsWithKeyword(normalized, "submit task")) {
            return SqlRiskAnalysis.high("starrocks_submit_task");
        }
        if (startsWithKeyword(normalized, "cancel task")) {
            return SqlRiskAnalysis.high("starrocks_cancel_task");
        }
        if (normalized.startsWith("insert overwrite")) {
            return SqlRiskAnalysis.high("starrocks_insert_overwrite");
        }
        // Standard SQL statements handled by Calcite parser
        if (startsWithKeyword(normalized, "select")
            || startsWithKeyword(normalized, "with")
            || startsWithKeyword(normalized, "insert")
            || startsWithKeyword(normalized, "update")
            || startsWithKeyword(normalized, "delete")) {
            return null;
        }
        // StarRocks-specific保底: any unrecognised StarRocks statement is L3.
        return SqlRiskAnalysis.high("starrocks_unrecognized");
    }
    private SqlRiskAnalysis classifyTrinoSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("trino_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("trino_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("trino_explain");
        }
        if (startsWithKeyword(normalized, "insert")) {
            return SqlRiskAnalysis.medium("trino_insert");
        }
        if (startsWithKeyword(normalized, "create table")) {
            return SqlRiskAnalysis.medium("trino_create_table");
        }
        if (startsWithKeyword(normalized, "create view")) {
            return SqlRiskAnalysis.medium("trino_create_view");
        }
        if (startsWithKeyword(normalized, "create materialized view")) {
            return SqlRiskAnalysis.medium("trino_create_materialized_view");
        }
        if (startsWithKeyword(normalized, "update")) {
            return SqlRiskAnalysis.medium("trino_update");
        }
        if (startsWithKeyword(normalized, "delete")) {
            return SqlRiskAnalysis.medium("trino_delete");
        }
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("trino_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("trino_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("trino_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("trino_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("trino_revoke");
        }
        if (startsWithKeyword(normalized, "create user")) {
            return SqlRiskAnalysis.high("trino_create_user");
        }
        if (startsWithKeyword(normalized, "create role")) {
            return SqlRiskAnalysis.high("trino_create_role");
        }
        if (startsWithKeyword(normalized, "call")) {
            return SqlRiskAnalysis.high("trino_call");
        }
        if (startsWithKeyword(normalized, "set session")) {
            return SqlRiskAnalysis.high("trino_set_session");
        }
        if (startsWithKeyword(normalized, "reset session")) {
            return SqlRiskAnalysis.high("trino_reset_session");
        }
        return null;
    }

    private SqlRiskAnalysis classifyPrestoSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("presto_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("presto_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("presto_explain");
        }
        if (startsWithKeyword(normalized, "insert")) {
            return SqlRiskAnalysis.medium("presto_insert");
        }
        if (startsWithKeyword(normalized, "create table")) {
            return SqlRiskAnalysis.medium("presto_create_table");
        }
        if (startsWithKeyword(normalized, "create view")) {
            return SqlRiskAnalysis.medium("presto_create_view");
        }
        if (startsWithKeyword(normalized, "update")) {
            return SqlRiskAnalysis.medium("presto_update");
        }
        if (startsWithKeyword(normalized, "delete")) {
            return SqlRiskAnalysis.medium("presto_delete");
        }
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("presto_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("presto_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("presto_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("presto_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("presto_revoke");
        }
        if (startsWithKeyword(normalized, "create user")) {
            return SqlRiskAnalysis.high("presto_create_user");
        }
        if (startsWithKeyword(normalized, "create role")) {
            return SqlRiskAnalysis.high("presto_create_role");
        }
        if (startsWithKeyword(normalized, "call")) {
            return SqlRiskAnalysis.high("presto_call");
        }
        if (startsWithKeyword(normalized, "set session")) {
            return SqlRiskAnalysis.high("presto_set_session");
        }
        if (startsWithKeyword(normalized, "reset session")) {
            return SqlRiskAnalysis.high("presto_reset_session");
        }
        return null;
    }

    private SqlRiskAnalysis classifyHiveSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("hive_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("hive_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("hive_explain");
        }
        if (startsWithKeyword(normalized, "insert")) {
            return SqlRiskAnalysis.medium("hive_insert");
        }
        if (startsWithKeyword(normalized, "create table")) {
            return SqlRiskAnalysis.medium("hive_create_table");
        }
        if (startsWithKeyword(normalized, "create view")) {
            return SqlRiskAnalysis.medium("hive_create_view");
        }
        if (startsWithKeyword(normalized, "analyze")) {
            return SqlRiskAnalysis.medium("hive_analyze");
        }
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("hive_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("hive_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("hive_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("hive_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("hive_revoke");
        }
        if (startsWithKeyword(normalized, "create user")) {
            return SqlRiskAnalysis.high("hive_create_user");
        }
        if (startsWithKeyword(normalized, "create role")) {
            return SqlRiskAnalysis.high("hive_create_role");
        }
        if (startsWithKeyword(normalized, "load data")) {
            return SqlRiskAnalysis.high("hive_load_data");
        }
        if (startsWithKeyword(normalized, "msck repair")) {
            return SqlRiskAnalysis.high("hive_msck_repair");
        }
        if (startsWithKeyword(normalized, "set")) {
            return SqlRiskAnalysis.high("hive_set");
        }
        return null;
    }

    private SqlRiskAnalysis classifyTidbSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        // L1: read-only introspection (specific SHOW/ADMIN forms first)
        if (normalized.startsWith("show placement")) {
            return SqlRiskAnalysis.low("tidb_show_placement");
        }
        if (normalized.startsWith("show table") && normalized.contains("regions")) {
            return SqlRiskAnalysis.low("tidb_show_regions");
        }
        if (normalized.startsWith("show split regions")) {
            return SqlRiskAnalysis.low("tidb_show_split_regions");
        }
        if (normalized.startsWith("show stats_")) {
            return SqlRiskAnalysis.low("tidb_show_stats");
        }
        if (normalized.startsWith("admin show ddl")) {
            return SqlRiskAnalysis.low("tidb_admin_show_ddl");
        }
        // Generic SHOW catch-all (after specific SHOW forms)
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("tidb_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("tidb_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("tidb_explain");
        }

        // L2: bounded write or heavy read
        if (normalized.startsWith("split table")) {
            return SqlRiskAnalysis.medium("tidb_split_table");
        }
        if (normalized.startsWith("recover table")) {
            return SqlRiskAnalysis.medium("tidb_recover_table");
        }
        if (normalized.startsWith("alter table") && normalized.matches("alter table\\s+\\S+\\s+compact(\\s.*)?")) {
            return SqlRiskAnalysis.medium("tidb_alter_compact");
        }
        if (normalized.startsWith("admin check table") || normalized.startsWith("admin check index")) {
            return SqlRiskAnalysis.medium("tidb_admin_check");
        }
        if (startsWithKeyword(normalized, "analyze")) {
            return SqlRiskAnalysis.medium("tidb_analyze");
        }

        // L3: destructive or cluster-affecting (specific ADMIN forms before generic)
        if (normalized.startsWith("admin cancel ddl")
            || normalized.startsWith("admin pause ddl")
            || normalized.startsWith("admin resume ddl")) {
            return SqlRiskAnalysis.high("tidb_admin_ddl_jobs");
        }
        if (startsWithKeyword(normalized, "admin")) {
            return SqlRiskAnalysis.high("tidb_admin_unrecognized");
        }
        if (normalized.startsWith("backup database") || normalized.startsWith("backup table")) {
            return SqlRiskAnalysis.high("tidb_backup");
        }
        if (normalized.startsWith("restore database") || normalized.startsWith("restore table")) {
            return SqlRiskAnalysis.high("tidb_restore");
        }
        if (normalized.startsWith("import into")) {
            return SqlRiskAnalysis.high("tidb_import_into");
        }
        if (normalized.startsWith("load data")) {
            return SqlRiskAnalysis.high("tidb_load_data");
        }
        if (normalized.startsWith("flashback ")) {
            return SqlRiskAnalysis.high("tidb_flashback");
        }
        if (normalized.contains("placement policy")
            && (normalized.startsWith("create ") || normalized.startsWith("alter ") || normalized.startsWith("drop "))) {
            return SqlRiskAnalysis.high("tidb_placement_policy");
        }
        if (normalized.startsWith("kill tidb")) {
            return SqlRiskAnalysis.high("tidb_kill");
        }
        if (normalized.startsWith("set global")) {
            return SqlRiskAnalysis.high("tidb_set_global");
        }
        if (normalized.startsWith("batch on") || normalized.startsWith("batch ")) {
            return SqlRiskAnalysis.high("tidb_batch_dml");
        }

        // Standard SQL DDL/DCL — handled directly like other MySQL-protocol dialects
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("tidb_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("tidb_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("tidb_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("tidb_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("tidb_revoke");
        }
        if (startsWithKeyword(normalized, "rename")) {
            return SqlRiskAnalysis.high("tidb_rename");
        }
        // Standard SQL DML / query — fall through to Calcite generic classifier
        if (startsWithKeyword(normalized, "select")
            || startsWithKeyword(normalized, "with")
            || startsWithKeyword(normalized, "insert")
            || startsWithKeyword(normalized, "update")
            || startsWithKeyword(normalized, "delete")
            || startsWithKeyword(normalized, "create")) {
            return null;
        }

        // Session-level SET (not SET GLOBAL) — session-scoped, low risk
        if (startsWithKeyword(normalized, "set")) {
            return SqlRiskAnalysis.low("tidb_set_session");
        }

        // Catch-all for any unrecognized TiDB statement
        return SqlRiskAnalysis.high("tidb_unrecognized");
    }

    private SqlRiskAnalysis classifyOceanBaseSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        // L1: read-only introspection
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("oceanbase_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("oceanbase_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            return SqlRiskAnalysis.low("oceanbase_explain");
        }

        // L3: OceanBase-specific system commands
        if (startsWithKeyword(normalized, "alter system")) {
            return SqlRiskAnalysis.high("oceanbase_alter_system");
        }
        if (startsWithKeyword(normalized, "major compaction") || startsWithKeyword(normalized, "minor compaction")) {
            return SqlRiskAnalysis.high("oceanbase_compaction");
        }
        if (startsWithKeyword(normalized, "flashback")) {
            return SqlRiskAnalysis.high("oceanbase_flashback");
        }
        if (startsWithKeyword(normalized, "kill")) {
            return SqlRiskAnalysis.high("oceanbase_kill");
        }
        if (startsWithKeyword(normalized, "switchover")) {
            return SqlRiskAnalysis.high("oceanbase_switchover");
        }
        if (startsWithKeyword(normalized, "switch tenant")) {
            return SqlRiskAnalysis.high("oceanbase_switch_tenant");
        }

        // L3: destructive DDL/DCL (handled explicitly before Calcite fallthrough)
        if (startsWithKeyword(normalized, "drop")) {
            return SqlRiskAnalysis.high("oceanbase_drop");
        }
        if (startsWithKeyword(normalized, "truncate")) {
            return SqlRiskAnalysis.high("oceanbase_truncate");
        }
        if (startsWithKeyword(normalized, "alter")) {
            return SqlRiskAnalysis.high("oceanbase_alter");
        }
        if (startsWithKeyword(normalized, "grant")) {
            return SqlRiskAnalysis.high("oceanbase_grant");
        }
        if (startsWithKeyword(normalized, "revoke")) {
            return SqlRiskAnalysis.high("oceanbase_revoke");
        }
        if (startsWithKeyword(normalized, "rename")) {
            return SqlRiskAnalysis.high("oceanbase_rename");
        }

        // Standard SQL DML / query → fall through to Calcite generic classifier
        if (startsWithKeyword(normalized, "select")
            || startsWithKeyword(normalized, "with")
            || startsWithKeyword(normalized, "insert")
            || startsWithKeyword(normalized, "update")
            || startsWithKeyword(normalized, "delete")
            || startsWithKeyword(normalized, "create")) {
            return null;
        }

        // Session-level SET (not SET GLOBAL) — session-scoped, low risk
        if (startsWithKeyword(normalized, "set")) {
            return SqlRiskAnalysis.low("oceanbase_set_session");
        }

        // Catch-all for any unrecognized OceanBase statement → L3
        return SqlRiskAnalysis.high("oceanbase_unrecognized");
    }

    // ====== Dameng Channel 1 — L3 admin command classification ======

    public SqlRiskAnalysis classifyDamengSpecific(String sql) {
        if (sql == null) return null;
        String stripped = stripLeadingComments(sql);
        if (stripped.isEmpty()) return null;

        if (DAMENG_TABLESPACE_DDL.matcher(stripped).find()
            || DAMENG_USER_DDL.matcher(stripped).find()
            || DAMENG_ROLE_DDL.matcher(stripped).find()
            || DAMENG_GRANT_REVOKE.matcher(stripped).find()
            || DAMENG_DROP_OBJECT.matcher(stripped).find()) {
            return SqlRiskAnalysis.high("dameng_admin_command");
        }
        return null;
    }

    // ====== Dameng Channel 2 — dialect_unsupported detection ======

    public Optional<DamengUnsupportedReason> detectDamengUnsupported(String sql) {
        if (sql == null) return Optional.empty();
        String stripped = stripLeadingComments(sql);
        if (stripped.isEmpty()) return Optional.empty();

        if (DAMENG_PLSQL_BLOCK.matcher(stripped).find()) {
            return Optional.of(DamengUnsupportedReason.PLSQL_BLOCK);
        }
        if (DAMENG_PROCEDURE_DDL.matcher(stripped).find()) {
            return Optional.of(DamengUnsupportedReason.PROCEDURE_DDL);
        }
        if (DAMENG_EXP_IMP.matcher(stripped).find()) {
            return Optional.of(DamengUnsupportedReason.EXP_IMP_COMMAND);
        }
        return Optional.empty();
    }

    // ====== KingbaseES Channel 1 — L3 admin command classification ======

    public SqlRiskAnalysis classifyKingbaseSpecific(String sql) {
        if (sql == null) return null;
        String stripped = stripLeadingComments(sql);
        if (stripped.isEmpty()) return null;
        if (KINGBASE_SYS_TABLE_DDL.matcher(stripped).find()
            || KINGBASE_SYS_ADMIN_SCHEMA_DDL.matcher(stripped).find()
            || KINGBASE_SYS_KILL.matcher(stripped).find()
            || KINGBASE_FLASHBACK.matcher(stripped).find()) {
            return SqlRiskAnalysis.high("kingbase_admin_command");
        }
        return null;
    }

    // ====== KingbaseES Channel 2 — dialect_unsupported detection ======

    public Optional<KingbaseUnsupportedReason> detectKingbaseUnsupported(String sql) {
        if (sql == null) return Optional.empty();
        if (KINGBASE_KB_BACKUP_RESTORE.matcher(sql).find()) {
            return Optional.of(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI);
        }
        if (KINGBASE_ORACLE_PLSQL_BLOCK.matcher(sql).find()) {
            return Optional.of(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK);
        }
        return Optional.empty();
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
