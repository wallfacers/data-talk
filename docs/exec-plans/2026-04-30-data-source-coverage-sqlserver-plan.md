# Data Source Coverage: SQL Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SQL Server's current stub/legacy state with verified first-class connection, metadata, SQL execution, diagnostics, frontend, MCP, prompt, and documentation support.

**Architecture:** SQL Server support must normalize `mssql` to canonical `sqlserver`, introduce a real Microsoft JDBC connection path, model database/catalog plus schema context, and explicitly handle security fields, `GO` splitting, risk analysis, diagnostics, and unsupported ER behavior.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC, Microsoft SQL Server JDBC driver selected during implementation approval, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md` is
reviewed and approved. SQL Server remains stub/legacy until every verification
step in this plan passes and `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is
updated.

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
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/`
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
- `client/src/features/stage/sql-dialects/sqlserver-keywords.json`
- `client/src/i18n/messages.ts`

Expected tests:

- backend connection, session context, SQL execution, splitter, risk,
  diagnostics, ER unsupported, and prompt contract tests;
- frontend settings, picker, Query Editor, formatter, outline, diagnostics, and
  i18n tests.

### Task 1: Approval Gate, Driver, Alias, And Persistence Decision

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "Status: Draft for review|Status: Approved" docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md
```

Expected: the design has been reviewed and marked approved before code edits
start.

- [ ] **Step 2: Re-read gates**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
sed -n '1,360p' client/DESIGN.md
```

Expected: output includes backend, frontend, MCP, Definition of Done, semantic
token, global Stage, accessibility, and i18n constraints.

- [ ] **Step 3: Record driver, alias, and persistence decisions**

Add a short note under this task with:

- Microsoft JDBC artifact and version;
- license/runtime packaging result;
- driver class;
- alias normalization boundary for `mssql -> sqlserver`;
- encryption/trust certificate defaults;
- instance name and JDBC property persistence shape.

Expected: note contains concrete values and no unresolved driver, alias, or
persistence ambiguity.

### Task 2: Connection Kind, URL, Persistence, And Connection Test

- [ ] **Step 1: Add failing backend tests**

Add tests for:

- `ConnectionKind`: `sqlserver` canonical value and `mssql` normalization;
- invalid SQL Server aliases return localized unsupported-kind errors;
- `JdbcUrlBuilder`: database URL, null database behavior, encryption, trust
  certificate, instance name, and properties based on approved persistence;
- repository/DTO round trip if new fields are introduced;
- `ConnectionServiceTest`: driver validation path and localized failure.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until SQL Server connection behavior is implemented.

- [ ] **Step 2: Implement connection behavior**

Add canonical kind routing, alias normalization, URL construction, driver
dependency, connection test behavior, persistence changes, migration, and
generated DB schema docs as required by the approved decision.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 3: Metadata, Target Resolution, SQL Execution, Splitter, And Risk

- [ ] **Step 1: Add failing metadata and target tests**

Add SQL Server cases for:

- database/catalog discovery with system database filtering;
- schema discovery inside selected database;
- `resolve_use_target` matched, ambiguous, and not-found database/schema names;
- `ReadSchemaAction` discover mode with `limit`, `cursor`, and `pattern`;
- describe mode requiring explicit tables and schema context.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until SQL Server metadata behavior is implemented or a
real fixture is wired.

- [ ] **Step 2: Implement metadata and context application**

Implement database/catalog and schema discovery, `mssql` normalization before
routing, and context application through the approved `setCatalog`, `setSchema`,
`USE`, or combined strategy.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

- [ ] **Step 3: Add failing SQL, splitter, risk, and normalization tests**

Add SQL Server coverage for:

- read-only execution with database/schema context;
- chat-path L2/L3 block;
- `GO` batch separator behavior or explicit single-statement restriction;
- risk classification for `MERGE`, `EXEC`, `BACKUP`, `RESTORE`, `ALTER`,
  `DROP`, `TRUNCATE`, permission changes, `KILL`, and `DBCC`;
- value normalization for datetimeoffset, datetime2, money, uniqueidentifier,
  varbinary, XML, geography/geometry, and decimal.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until SQL Server SQL behavior is explicit.

- [ ] **Step 4: Implement SQL execution, splitter, risk, and normalization**

Add a SQL Server splitter if `GO` scripts are in day-1 scope. Otherwise enforce
single-statement behavior and document it. Update SQL execution, risk, and value
normalization inside the approved SQL Server boundaries.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics And ER Behavior

- [ ] **Step 1: Add failing diagnostics tests**

Add SQL Server diagnostics tests for:

- provider routing for `sqlserver` and alias handling if aliases reach the
  diagnostics boundary;
- plan support if approved;
- structured unsupported for lock info, pool status, table space, terminate,
  and optimize when not implemented;
- permission-denied handling for DMV or SHOWPLAN paths.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until SQL Server diagnostics are honest.

- [ ] **Step 2: Implement diagnostics behavior**

Implement real SHOWPLAN only if session-setting cleanup, permissions, and plan
normalization are tested. Otherwise route capabilities to structured
unsupported. Keep ER Inspector and ER Designer `dialect_unsupported` unless this
plan explicitly adds and verifies SQL Server ER behavior.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=DiagnosticsServiceTest,JdbcErRelationDiscoveryServiceTest,ErDdlGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: diagnostics and ER tests pass.

### Task 5: Frontend And MCP Exposure

- [ ] **Step 1: Add failing frontend tests**

Add tests for:

- SQL Server connection form, default port `1433`, encryption and trust
  certificate controls, and optional instance field;
- `mssql` input never persists as displayed canonical kind;
- picker display;
- Query Editor database plus schema context;
- formatter mapping and SQL Server outline keywords;
- diagnostics unsupported and permission states;
- i18n coverage.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
```

Expected: tests fail until SQL Server UI is implemented.

- [ ] **Step 2: Implement frontend changes**

Expose SQL Server only after backend tests pass. Use semantic tokens, existing
form patterns, accessible controls, and i18n keys from `client/DESIGN.md`.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/diagnostics
cd client && npx tsc --noEmit
```

Expected: frontend tests and typecheck pass.

- [ ] **Step 3: Add MCP and prompt contract tests**

Update action schema and runtime prompt tests so SQL Server is mentioned only
with real capabilities or structured unsupported outputs, and `mssql` is
documented only as an input alias.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until schemas and prompt are aligned.

- [ ] **Step 4: Update MCP schemas and runtime prompt**

Update `ConnectionObjectType`, action schemas, messages, and `AGENTS.md` after
backend and frontend capability gates are true.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest,ConfirmableActionSchemasTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: prompt and schema tests pass.

### Task 6: Documentation, Verification, Smoke, And Commit

- [ ] **Step 1: Update docs**

Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot from
stub/legacy to first-class only after all code gates pass. Update
`docs/generated/db-schema.md` if migrations were added, and update product/exec
indexes during housekeeping.

Run:

```bash
rg -n "sqlserver|mssql" docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
git diff --check -- docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
```

Expected: docs are accurate and whitespace checks pass.

- [ ] **Step 2: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
```

Expected: both commands succeed.

- [ ] **Step 3: Run SQL Server smoke**

Exercise:

- create SQL Server connection with encryption settings;
- create `mssql` alias input and confirm persisted kind is `sqlserver`;
- test connection;
- list databases and schemas;
- resolve matched, ambiguous, and missing targets;
- read schema discover and describe;
- run Query Editor `SELECT`;
- run AI `datatalk_execute_sql`;
- exercise L2/L3 confirmation;
- run diagnostics or verify structured unsupported.

Expected: smoke results match the approved design and are recorded in this plan
before completion.

- [ ] **Step 4: Commit implementation**

Run:

```bash
git status --short
git add server client docs
git commit -m "feat(data-sources): add sqlserver coverage"
```

Expected: commit succeeds after all checks pass.
