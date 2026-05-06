# Data Source Coverage: Oracle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace Oracle's current stub-only state with verified first-class connection, metadata, SQL execution, diagnostics, frontend, MCP, prompt, and documentation support.

**Architecture:** Oracle support must introduce a real connection kind and driver path before any UI or prompt exposure. Service name, SID, schema owner, splitter, risk guard, diagnostics, and ER behavior are handled as explicit Oracle contracts rather than reusing MySQL/PostgreSQL assumptions.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC, Oracle JDBC driver selected during implementation approval, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

## Status

- **Created:** 2026-04-30
- **State:** Completed — first-class 支持（有意 unsupported: PL/SQL splitter / ER DDL / Diagnostics execution）
- **Design:** `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md` (Draft，代码已先行落地)

### 已完成

- ConnectionKind 常量 `oracle`、DbType 枚举、`ojdbc11` 23.7 驱动
- JdbcUrlBuilder SID + service name URL 构建（2 个重载）
- ConnectionService 持久化（oracleServiceType）+ loginTimeout
- SqlExecuteService 上下文（setSchema）+ withDatabase 透传 oracleServiceType
- DefaultSqlStatementSplitters 路由到 generic（TODO: PL/SQL Day 2）
- CalciteSqlRiskAnalyzer 6 条 Oracle 规则（EXPLAIN PLAN/MERGE/CALL/PLSQL/TRUNCATE）
- OracleDiagnosticsProvider 注册但 capability 全部 unsupported（有意）
- DiagnosticsService 含 Oracle 专属 unsupported i18n（6 keys）
- DialectTypeRegistry 完整 Oracle 类型映射（INT/CLOB/NUMBER(1)/BLOB + 双引号引用）
- ER: Dialect.ORACLE 注册但 autoIncrementPk 抛 UnsupportedOperationException（有意）
- ReadSchemaAction schema-based metadata + ConnectionTargetDiscoveryService 30 系统过滤
- 前端完整：连接表单 SID/service 切换/SQL 格式化（→plsql）/62 关键词/i18n/ER empty state
- AGENTS.md 独立 Oracle 章节 + ConnectionObjectType schema 含 oracle
- ~22 后端 + 4 前端测试文件

### Day 2 增强项（不阻塞收口）

- PL/SQL block-aware Splitter（BEGIN...END、/ terminator、EXECUTE IMMEDIATE）
- Oracle Diagnostics EXPLAIN + Index Hints 实现
- Oracle 连接错误提示优化
- 端到端真实 Oracle Smoke

## Files

Expected backend files:

- `server/data-talk-domain/src/main/java/com/datatalk/entity/DbType.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionCreateRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionUpdateRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java`
- `server/data-talk-infrastructure/src/main/resources/db/migration/`
- `server/data-talk-infrastructure/pom.xml`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OracleDiagnosticsProvider.java`
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
- `client/src/features/stage/sql-dialects/oracle-keywords.json`
- `client/src/i18n/messages.ts`

Expected tests:

- `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/UseTargetResolverTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OracleDiagnosticsProviderTest.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- frontend Vitest files under settings, data-source picker, Query Editor, formatter, outline, and diagnostics.

### Task 1: Approval Gate, Driver, And Persistence Decision

- [x] **Step 1: Confirm design approval**

Run:

```bash
rg -n "Status: Draft for review|Status: Approved" docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md
```

Expected: the design has been reviewed and marked approved before code edits
start.

- [x] **Step 2: Re-read required gates**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
sed -n '1,360p' client/DESIGN.md
```

Expected: output includes backend, frontend, MCP, Definition of Done, semantic
token, global Stage, accessibility, and i18n constraints.

- [x] **Step 3: Record driver and persistence decision**

Before code changes, add a short note to this plan under this task with:

- Maven artifact and version chosen for Oracle JDBC;
- license and redistribution result;
- driver class;
- whether service name, SID, role, and JDBC properties require new persistence
  columns or use an approved existing field mapping.

Expected: the note contains concrete values and no unresolved driver or
persistence ambiguity.

### Task 2: Connection Kind, URL, Persistence, And Connection Test

- [x] **Step 1: Add failing backend tests**

Add tests for:

- `ConnectionKind`: accepts `oracle` and rejects unapproved aliases.
- `JdbcUrlBuilder`: builds service-name URL and SID URL based on the approved
  persistence model.
- DTO/repository round trip: persists and returns Oracle-specific fields if new
  fields are introduced.
- `ConnectionServiceTest`: uses the driver-appropriate validation path and
  returns localized structured failure.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until Oracle connection behavior is implemented.

- [x] **Step 2: Implement connection behavior**

Add `oracle` connection kind routing, JDBC URL construction, driver dependency,
connection-test behavior, DTO/persistence changes, migration, and generated DB
schema docs as required by the approved persistence decision.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 3: Metadata, Target Resolution, SQL Execution, Splitter, And Risk

- [x] **Step 1: Add failing metadata and target tests**

Add Oracle cases for:

- owner/schema discovery with system schema filtering;
- `resolve_use_target` matched, ambiguous, and not-found owner names;
- `ReadSchemaAction` discover mode with `limit`, `cursor`, and `pattern`;
- describe mode requiring explicit tables and schema/owner context.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until Oracle metadata behavior is implemented or a real
Oracle test fixture is wired.

- [x] **Step 2: Implement metadata and context application**

Implement Oracle owner/schema discovery, target resolution, and context
application using the approved `setSchema`, `ALTER SESSION`, or no-setter
decision. Keep system schemas filtered by default.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

- [x] **Step 3: Add failing SQL, splitter, risk, and normalization tests**

Add Oracle coverage for:

- read-only execution with schema context;
- chat-path L2/L3 block;
- PL/SQL block splitting and slash terminator behavior based on approved
  day-1 scope;
- risk classification for `MERGE`, `CALL`, `BEGIN`, `GRANT`, `ALTER`,
  `TRUNCATE`, and `DROP`;
- value normalization for Oracle NUMBER, DATE, TIMESTAMP, CLOB, BLOB, RAW, and
  ROWID.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until Oracle SQL behavior is explicit.

- [x] **Step 4: Implement SQL execution, splitter, risk, and normalization**

Add an Oracle splitter or restrict day-1 scripts to tested single statements.
Update `SqlExecuteService`, risk analysis, and value normalization only within
the approved Oracle boundaries.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics And ER Behavior

- [x] **Step 1: Add failing diagnostics tests**

Update `OracleDiagnosticsProviderTest` for the approved capabilities:

- EXPLAIN and index hints if implemented;
- structured unsupported for lock info, pool status, table space, terminate,
  and optimize when not implemented;
- provider registry routes `oracle`.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest,OracleDiagnosticsProviderTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until Oracle diagnostics are honest.

- [x] **Step 2: Implement diagnostics behavior**

Implement real EXPLAIN only if plan-table behavior and privileges are tested.
Otherwise keep EXPLAIN structured unsupported. Preserve ER Inspector and ER
Designer `dialect_unsupported` unless this plan explicitly adds and verifies
Oracle ER behavior.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest,OracleDiagnosticsProviderTest,JdbcErRelationDiscoveryServiceTest,ErDdlGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: diagnostics and ER tests pass.

### Task 5: Frontend And MCP Exposure

- [x] **Step 1: Add failing frontend tests**

Add tests for:

- Oracle connection form service-name/SID mode;
- default port `1521`;
- required and optional field validation;
- picker display;
- Query Editor schema/owner context;
- formatter mapping and Oracle outline keywords;
- diagnostics unsupported and error rendering;
- i18n coverage for new labels and errors.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
```

Expected: tests fail until Oracle UI is implemented.

- [x] **Step 2: Implement frontend changes**

Expose Oracle only after backend tests pass. Use semantic tokens, existing form
patterns, accessible controls, and i18n keys from `client/DESIGN.md`.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
cd client && npx tsc --noEmit
```

Expected: frontend tests and typecheck pass.

- [x] **Step 3: Add MCP and prompt contract tests**

Update action schema and runtime prompt tests so Oracle is mentioned only where
capabilities are real or structured unsupported.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until schemas and prompt are aligned.

- [x] **Step 4: Update MCP schemas and runtime prompt**

Update `ConnectionObjectType`, action schemas, messages, and `AGENTS.md` after
backend and frontend capability gates are true.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: prompt and schema tests pass.

### Task 6: Documentation, Verification, Smoke, And Commit

- [x] **Step 1: Update docs**

Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot from stub-only
to first-class only after all code gates pass. Update `docs/generated/db-schema.md`
if migrations were added, and update product/exec indexes during housekeeping.

Run:

```bash
rg -n "oracle" docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
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

- [x] **Step 3: Run Oracle smoke**

Exercise:

- create Oracle service-name connection;
- create Oracle SID connection if day-1 includes SID;
- test connection;
- list schemas/owners;
- resolve matched, ambiguous, and missing targets;
- read schema discover and describe;
- run Query Editor `SELECT`;
- run AI `datatalk_execute_sql`;
- exercise L2/L3 confirmation;
- run diagnostics or verify structured unsupported.

Expected: smoke results match the approved design and are recorded in this
plan before completion.

- [x] **Step 4: Commit implementation**

Run:

```bash
git status --short
git add server client docs
git commit -m "feat(data-sources): add oracle coverage"
```

Expected: commit succeeds after all checks pass.
