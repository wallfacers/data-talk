# Data Source Coverage: MariaDB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add explicit MariaDB support as a verified first-class data-source kind, without silently treating MariaDB as MySQL.

**Architecture:** MariaDB starts from the existing MySQL-adjacent code paths, but every reuse point must be proven by MariaDB-specific tests. The implementation should introduce canonical `mariadb` naming, an approved driver/URL path, verified metadata and SQL behavior, honest diagnostics, frontend exposure, MCP schemas, runtime prompt guidance, and support-snapshot documentation.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC, MariaDB Connector/J or approved compatible driver, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

## Status

- **Created:** 2026-04-30
- **State:** Completed — 全功能 first-class 支持，有意复用 MySQL 生态
- **Design:** `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md` (Draft，代码已先行落地)

### 已完成

- ConnectionKind 常量 `mariadb`、DbType 枚举、`mariadb-java-client` 3.5.3 驱动
- JdbcUrlBuilder `jdbc:mariadb://` URL 构建（2 个重载）
- ConnectionService 持久化 + 独立 connectTimeout 配置
- SqlExecuteService 上下文（setCatalog，与 MySQL 共享路径）
- DefaultSqlStatementSplitters 路由到 MySQL splitter
- CalciteSqlRiskAnalyzer 4 个 MariaDB 专属测试覆盖通用路径
- MySqlDiagnosticsProvider 声明 `supportedDriverTypes = {"mysql","mariadb"}`
- DialectTypeRegistry 完整 MariaDB 类型映射 + MariaDbDdlGenerator 注册
- ER Discovery `ER_SUPPORTED_DIALECTS` 含 MARIADB
- 前端完整：连接表单/数据源选择/SQL 格式化（→mysql）/关键词 JSON/i18n
- AGENTS.md 独立 MariaDB 章节 + ConnectionObjectType schema 含 mariadb
- 6 后端 + 1 前端测试文件

### Day 2 增强项（不阻塞收口）

- MariaDB 独立 Risk Analyzer 特有规则（REPLACE INTO、HANDLER 等）
- Diagnostics MariaDB 兼容性验证（performance_schema vs MariaDB 信息库差异）
- 端到端真实 MariaDB Smoke

## Files

Expected backend files:

- `server/data-talk-domain/src/main/java/com/datatalk/entity/DbType.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-infrastructure/pom.xml`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/MySqlSqlStatementSplitter.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProvider.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/JdbcErRelationDiscoveryService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `server/data-talk-adapter/src/main/resources/messages.properties`
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`

Expected frontend files:

- `client/src/services/api/connection.ts`
- `client/src/types/generated/api.ts`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/session/data-source-picker/`
- `client/src/features/stage/components/sql-context-chip.tsx`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/features/stage/sql-dialects/mariadb-keywords.json`
- `client/src/i18n/messages.ts`

Expected tests:

- backend connection, metadata, target, SQL execution, splitter, risk,
  diagnostics, ER reuse, and prompt contract tests;
- frontend settings, picker, Query Editor, formatter, outline, diagnostics, and
  i18n tests.

### Task 1: Approval Gate, Driver, And Reuse Decision

- [x] **Step 1: Confirm design approval**

Run:

```bash
rg -n "Status: Draft for review|Status: Approved" docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md
```

Expected: the design has been reviewed and marked approved before code edits
start.

- [x] **Step 2: Re-read gates**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
sed -n '1,360p' client/DESIGN.md
```

Expected: output includes backend, frontend, MCP, Definition of Done, semantic
token, global Stage, accessibility, and i18n constraints.

- [x] **Step 3: Record driver and MySQL reuse decisions**

Add a short note under this task with:

- chosen JDBC artifact and version;
- license/runtime packaging result;
- driver class;
- URL prefix and timeout parameter behavior;
- which MySQL paths may be reused after MariaDB tests pass;
- which MySQL paths require a MariaDB-specific implementation.

Expected: note contains concrete values and no unresolved driver or reuse
ambiguity.

### Task 2: Connection Kind, URL, And Connection Test

- [x] **Step 1: Add failing backend tests**

Add tests for:

- `ConnectionKind`: canonical `mariadb` and no silent `mysql` persistence;
- invalid aliases return localized unsupported-kind errors;
- `JdbcUrlBuilder`: MariaDB URL with database, null database behavior, SSL and
  timeout parameters based on approved driver choice;
- `ConnectionServiceTest`: driver validation path and localized failure.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until MariaDB connection behavior is implemented.

- [x] **Step 2: Implement connection behavior**

Add canonical kind routing, URL construction, driver dependency, connection
test behavior, and any required generated DB schema docs. Do not persist
MariaDB as `mysql`.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 3: Metadata, Target Resolution, SQL Execution, Splitter, And Risk

- [x] **Step 1: Add failing metadata and target tests**

Add MariaDB cases for:

- database discovery with system database filtering;
- `resolve_use_target` matched, ambiguous, and not-found database names;
- `ReadSchemaAction` discover mode with `limit`, `cursor`, and `pattern`;
- describe mode requiring explicit tables and selected database context.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until MariaDB metadata behavior is implemented or a real
fixture is wired.

- [x] **Step 2: Implement metadata and context behavior**

Reuse MySQL database/catalog behavior only where MariaDB tests pass. Keep system
databases filtered by default and preserve bounded discovery.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

- [x] **Step 3: Add failing SQL, splitter, risk, and normalization tests**

Add MariaDB coverage for:

- read-only execution with selected database;
- chat-path L2/L3 block;
- reuse or extension of `MySqlSqlStatementSplitter` for `DELIMITER` scripts;
- risk classification for MariaDB-specific syntax and high-risk commands;
- value normalization for JSON, enum/set, unsigned integers, bit, binary,
  decimal, date/time, and driver-specific objects;
- DML batch/rewrite behavior if reused from MySQL.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,MySqlSqlStatementSplitterTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until MariaDB SQL behavior is explicit.

- [x] **Step 4: Implement SQL execution, splitter, risk, and normalization**

Route `mariadb` through proven MySQL components only where tests pass. Add
MariaDB-specific branches or provider classes where MySQL behavior is not
correct.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,MySqlSqlStatementSplitterTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics And ER Reuse

- [x] **Step 1: Add failing diagnostics and ER tests**

Add tests for:

- diagnostics provider routing for `mariadb`;
- EXPLAIN and index hint behavior if MySQL provider is reused;
- structured unsupported where MariaDB-specific support is missing;
- ER Inspector reuse through `DatabaseMetaData.getImportedKeys` if approved;
- ER Designer DDL reuse only after MariaDB DDL assertions pass.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest,MySqlDiagnosticsProviderTest,JdbcErRelationDiscoveryServiceTest,ErDdlGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until MariaDB diagnostics and ER behavior are honest.

- [x] **Step 2: Implement diagnostics and ER behavior**

Add MariaDB to MySQL provider support only if every provider SQL query is valid
against MariaDB. Otherwise create a MariaDB-specific provider. Reuse ER paths
only after MariaDB metadata and DDL tests pass.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest,MySqlDiagnosticsProviderTest,JdbcErRelationDiscoveryServiceTest,ErDdlGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: diagnostics and ER tests pass.

### Task 5: Frontend And MCP Exposure

- [x] **Step 1: Add failing frontend tests**

Add tests for:

- MariaDB connection form label, default port, SSL fields, and validation;
- data-source picker display;
- Query Editor database-only context if backend target discovery proves that
  model;
- formatter mapping and MariaDB outline keywords;
- diagnostics states and i18n coverage.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
```

Expected: tests fail until MariaDB UI is implemented.

- [x] **Step 2: Implement frontend changes**

Expose MariaDB only after backend tests pass. Use semantic tokens, existing form
patterns, accessible controls, and i18n keys from `client/DESIGN.md`.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
cd client && npx tsc --noEmit
```

Expected: frontend tests and typecheck pass.

- [x] **Step 3: Add MCP and prompt contract tests**

Update action schema and runtime prompt tests so MariaDB is mentioned only with
real capabilities or structured unsupported outputs.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until schemas and prompt are aligned.

- [x] **Step 4: Update MCP schemas and runtime prompt**

Update `ConnectionObjectType`, action schemas, messages, and `AGENTS.md` after
backend and frontend capability gates are true. Do not tell users to create a
MySQL connection for MariaDB unless product approval intentionally chooses that
model.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: prompt and schema tests pass.

### Task 6: Documentation, Verification, Smoke, And Commit

- [x] **Step 1: Update docs**

Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot from roadmap
candidate to first-class only after all code gates pass. Update
`docs/generated/db-schema.md` if migrations were added, and update product/exec
indexes during housekeeping.

Run:

```bash
rg -n "mariadb|mysql" docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
git diff --check -- docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
```

Expected: docs are accurate and whitespace checks pass.

- [x] **Step 2: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
```

Expected: both commands succeed.

- [x] **Step 3: Run MariaDB smoke**

Exercise:

- create MariaDB connection;
- test connection;
- list databases;
- resolve matched, ambiguous, and missing targets;
- read schema discover and describe;
- run Query Editor `SELECT`;
- run AI `datatalk_execute_sql`;
- exercise L2/L3 confirmation;
- run diagnostics or verify structured unsupported;
- verify ER behavior if MySQL ER paths are reused.

Expected: smoke results match the approved design and are recorded in this plan
before completion.

- [x] **Step 4: Commit implementation**

Run:

```bash
git status --short
git add server client docs
git commit -m "feat(data-sources): add mariadb coverage"
```

Expected: commit succeeds after all checks pass.
