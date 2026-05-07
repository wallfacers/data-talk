# Apache Doris Driver Decision

Date: 2026-05-07
Decision: Use MySQL Connector/J (already in runtime classpath)

## Candidate Evaluation

| Criterion | MySQL Connector/J | MariaDB Connector/J |
|---|---|---|
| Artifact | `com.mysql:mysql-connector-j` (already in pom.xml) | `org.mariadb.jdbc:mariadb-java-client` 3.5.3 |
| License | GPL v2 + Universal FOSS Exception | LGPL v2.1 |
| Shaded/packaging | Shaded, single jar | Shaded, single jar |
| Driver class | `com.mysql.cj.jdbc.Driver` | `org.mariadb.jdbc.Driver` |
| URL prefix | `jdbc:mysql://` | `jdbc:mariadb://` |
| Doris protocol compatibility | Official: Doris docs show `jdbc:mysql://` examples | Works but Doris docs reference MySQL Connector/J |
| Timeout properties | `connectTimeout` (ms), `socketTimeout` (ms) | `connectTimeout` (ms) |
| Known Doris quirks | Doris FE emulates MySQL server protocol on port 9030 | Same MySQL protocol emulation |
| DELIMITER support | Handled by MySqlSqlStatementSplitter | Would need separate splitter or MariaDB splitter |

## Decision: MySQL Connector/J

Rationale:
1. Apache Doris official documentation uses MySQL Connector/J URL examples
2. Already on the runtime classpath — zero packaging changes
3. MySQL splitter handles DELIMITER, backtick identifiers, comments — all applicable to Doris SQL
4. `setCatalog()` behavior proven for MySQL-protocol databases in DataTalk
5. MariaDB Connector/J would add an unnecessary dependency when Doris explicitly targets MySQL protocol compatibility

## Reuse Map

| Component | Decision | Reason |
|---|---|---|
| JdbcUrlBuilder | `jdbc:mysql://` prefix, port 9030 | Doris uses MySQL protocol |
| ConnectionService timeout | Reuse MySQL timeout params (`connectTimeout` + `socketTimeout` in ms) | Same driver, same timeout behavior |
| SqlExecuteService context | Reuse `setCatalog()` path (same as mysql/mariadb) | MySQL protocol catalogs map to Doris databases |
| DefaultSqlStatementSplitters | Route to MySQL splitter | Same SQL syntax for strings, comments, DELIMITER |
| CalciteSqlRiskAnalyzer | Add Doris-specific classification | Doris has LOAD, ROUTINE LOAD, ALTER SYSTEM, etc. |
| DiagnosticsProvider | Structured unsupported (day-1) | Doris EXPLAIN output differs from MySQL; needs dedicated work |
| ER Inspector | Structured unsupported (day-1) | Doris foreign-key metadata unverified |
| ER Designer DDL | Structured unsupported (day-1) | Doris DDL has unique distribution/partition syntax |

## Kind Normalization

- Canonical kind: `apache_doris`
- Accepted alias: `doris`, normalized in `ConnectionService.create()` / `update()`
- No `DbType` enum value needed (follows ClickHouse/DuckDB pattern — string-only kind)
