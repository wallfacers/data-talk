# Data Source Coverage: SQLite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete first-class user SQLite support by exposing the existing backend/runtime path through verified frontend, metadata, SQL execution, diagnostics, MCP, prompt, and documentation gates.

**Architecture:** SQLite remains file-scoped. The implementation should reuse existing `ConnectionRecord.kind()`, `JdbcUrlBuilder`, guarded SQL execution, and SQLite JDBC driver paths where tests prove they are correct, while adding frontend exposure and structured unsupported diagnostics without pretending SQLite has server catalogs or schemas.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC, SQLite JDBC, JUnit 5, AssertJ, React 19, TypeScript, Vitest, `sql-formatter`, DataTalk MCP action registry.

---

Implementation began after
`docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md` was
approved. The working-tree implementation and automated verification completed
on 2026-05-01, and `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` now reflects
first-class file-scoped SQLite support.

## Files

Expected backend files:

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/session/UseTargetResolverTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

Expected frontend files:

- `client/src/services/api/connection.ts`
- `client/src/types/generated/api.ts`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx`
- `client/src/features/session/data-source-picker/`
- `client/src/features/stage/components/sql-context-chip.tsx`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/format-sql.test.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/features/stage/utils/parse-sql-outline.test.ts`
- `client/src/features/stage/sql-dialects/sqlite-keywords.json`
- `client/src/i18n/messages.ts`

Expected docs:

- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- `docs/product-specs/index.md`
- `docs/exec-plans/index.md`
- `docs/generated/db-schema.md` only if persistence changes.

### Task 1: Approval Gate And Current-State Verification

- [x] **Step 1: Confirm design approval**

Run:

```bash
rg -n "Status: Draft for review|Status: Approved" docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md
```

Expected: the design has been reviewed and marked approved before code edits
start.

- [x] **Step 2: Re-run mandatory gate reads**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
sed -n '1,360p' client/DESIGN.md
```

Expected: output includes the data-source compatibility checklist and frontend
semantic token, global Stage, accessibility, and i18n constraints.

- [x] **Step 3: Re-run SQLite implementation scan**

Run:

```bash
rg -n "sqlite|SQLITE|org.sqlite|jdbc:sqlite|DATABASE_TYPES|formatSql|parse-sql-outline|ConnectionKind|JdbcUrlBuilder|DynamicSqlExecutionRepository|ReadSchemaAction|SqlExecuteService" server client/src docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: output shows existing backend SQLite traces and missing frontend
exposure.

### Task 2: Backend Connection And URL Tests

- [x] **Step 1: Add failing URL and kind tests**

Add cases to
`server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`:

- `sqlite_file_path_builds_jdbc_sqlite_url`: `databaseName="/tmp/app.db"` yields
  `jdbc:sqlite:/tmp/app.db`.
- `sqlite_memory_builds_exact_memory_url`: `databaseName=":memory:"` yields
  `jdbc:sqlite::memory:`.
- `sqlite_blank_database_uses_documented_default`: blank `databaseName`
  normalizes to the documented `:memory:` fallback instead of `jdbc:sqlite:`.
- `sqlite_null_database_uses_documented_default`: preserves or intentionally
  changes the existing null fallback, with the expected value asserted.

Run:

```bash
cd server && mvn -q -pl data-talk-application -Dtest=JdbcUrlBuilderTest test
```

Expected: new tests fail if the current URL behavior does not match the
approved design.

- [x] **Step 2: Implement minimal URL and kind behavior**

Update `JdbcUrlBuilder` and `ConnectionKind` only if the failing tests require
changes. Preserve existing MySQL, PostgreSQL, and H2 behavior.

Run:

```bash
cd server && mvn -q -pl data-talk-application -Dtest=JdbcUrlBuilderTest test
```

Expected: `JdbcUrlBuilderTest` passes.

### Task 3: Backend Metadata, Target, SQL, Splitter, Risk, And Diagnostics

- [x] **Step 1: Add failing metadata and target tests**

Add SQLite cases for:

- `ConnectionTargetDiscoveryServiceTest`: selected SQLite connection returns a
  file-scoped target and does not synthesize schemas.
- `UseTargetResolverTest`: matching by connection name succeeds, ambiguous
  names return suggestions, and missing names return not-found.
- `ReadSchemaActionIT`: discover mode filters `sqlite_%` internal objects,
  applies `limit`, `cursor`, and `pattern`, and describe mode requires explicit
  tables.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail where SQLite behavior is incomplete.

- [x] **Step 2: Implement metadata and target behavior**

Update discovery, resolver, and `ReadSchemaAction` scope handling so SQLite is
file-scoped and schema-less by default.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

- [x] **Step 3: Add failing SQL, splitter, and risk tests**

Add SQLite coverage for:

- `SqlExecuteServiceTest`: `SELECT` against SQLite file, bounded rows,
  statement result ordering, and mutation confirmation behavior.
- `ExecuteSqlActionIT`: chat-path read-only execution works and L2/L3 SQL is
  blocked.
- `DefaultSqlStatementSplittersTest`: comments, strings, pragmas, and
  multi-statement SQLite scripts split safely.
- `CalciteSqlRiskAnalyzerTest`: `EXPLAIN QUERY PLAN`, `PRAGMA table_info`,
  read-only metadata pragmas, `ATTACH`, `DETACH`, `VACUUM`, `DROP`, and broad
  mutations are classified, including mixed multi-statement SQLite scripts.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until SQLite splitter and risk behavior is explicit.

- [x] **Step 4: Implement SQL, splitter, risk, and diagnostics behavior**

Use the generic splitter only if the new SQLite tests prove it is safe for the
approved day-1 script set. Add a dedicated SQLite splitter if generic behavior
cannot cover SQLite scripts. Implement EXPLAIN diagnostics only with provider
tests; otherwise route every SQLite diagnostic capability to structured
unsupported.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,DiagnosticsServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted backend tests pass.

### Task 4: Frontend Exposure And Query Editor Behavior

- [x] **Step 1: Add failing frontend tests**

Add or update Vitest cases for:

- settings connection form shows `SQLite` and maps the file path field to
  `databaseName`;
- data-source picker lists SQLite saved connections without schema assumptions;
- Query Editor context hides schema controls for SQLite;
- `formatSql` maps `sqlite` to a safe formatter language;
- SQL outline recognizes SQLite keywords and read-only pragmas;
- new labels and unsupported states use i18n keys.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts
```

Expected: tests fail until SQLite UI is exposed.

- [x] **Step 2: Implement frontend changes**

Update connection type lists, the settings form, picker display, Query Editor
context controls, formatter mapping, outline keywords, and i18n messages. Use
semantic tokens and existing component patterns from `client/DESIGN.md`.

Run:

```bash
cd client && npx vitest run src/features/settings/data-sources src/features/session/data-source-picker src/features/stage/utils/format-sql.test.ts src/features/stage/utils/parse-sql-outline.test.ts
cd client && npx tsc --noEmit
```

Expected: targeted frontend tests and typecheck pass.

### Task 5: MCP, Runtime Prompt, ER, And Docs

- [x] **Step 1: Add prompt and schema contract tests**

Update `AgentPromptContractTest` and any MCP schema tests so SQLite appears in
runtime guidance only with file-scoped context and honest diagnostics.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until schemas and prompt are aligned.

- [x] **Step 2: Update MCP schemas and runtime prompt**

Expose `sqlite` in action schemas only where implementation is real. Update
`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` with SQLite
file-scoped guidance, no schema-switching claim, and no placeholder
host/port/username/password requirement for SQLite create/update flows.

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: prompt contract tests pass.

- [x] **Step 3: Update compatibility and generated docs**

Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot from partial
to first-class only after automated gates pass. Update
`docs/generated/db-schema.md` only if persistence changed.

Run:

```bash
rg -n "sqlite" docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
git diff --check -- docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/generated/db-schema.md docs/product-specs/index.md docs/exec-plans/index.md
```

Expected: docs are accurate and whitespace checks pass.

### Task 6: Consolidated Verification And Commit

- [x] **Step 1: Run consolidated backend and frontend gates**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
```

Expected: both commands succeed.

- [x] **Step 2: Run manual or integration smoke**

Exercise:

- create SQLite file connection;
- verify `datatalk_create_connection` / `datatalk_update_connection_confirmable`
  accept SQLite without fake server fields;
- test connection;
- list targets;
- read schema discover and describe;
- run Query Editor `SELECT`;
- run AI `datatalk_execute_sql`;
- verify L2/L3 confirmation;
- verify diagnostics implementation or structured unsupported;
- verify ER Designer CREATE-only SQLite behavior.

Expected: smoke results match the approved design and are recorded in this
plan before completion.

Recorded 2026-05-01 integration smoke:

- create/test SQLite file connection: `ConnectionManagementActionsIT` added a
  `kind=sqlite` file-path create/test case and passed.
- list targets: `ConnectionTargetDiscoveryServiceTest` verified file-scoped
  target discovery without synthesized schemas and passed.
- read schema discover/describe: `ReadSchemaActionIT` verified
  `sqlite_%` filtering, pagination, and explicit describe and passed.
- run Query Editor context path: frontend tests verified SQLite connection type
  exposure plus schema-less context controls and passed.
- run AI `datatalk_execute_sql`: `ExecuteSqlActionIT` added a SQLite read-only
  query case and passed.
- verify L2/L3 guard: `CalciteSqlRiskAnalyzerTest` verified SQLite
  `EXPLAIN QUERY PLAN`, read-only pragma allowlisting, `ATTACH`, `DETACH`,
  `VACUUM`, and mixed multi-statement maintenance-command classification;
  chat-path mutation blocking remained covered by `ExecuteSqlActionIT`.
- verify SQLite MCP connection schemas: `ConfirmableActionSchemasTest` and
  `ConnectionManagementActionsIT` verified SQLite create/update flows no longer
  depend on fake host/port/username/password placeholders.
- verify SQLite `:memory:` semantics remain documented as temporary-only:
  frontend placeholder copy and runtime prompt guidance were updated to call
  out the per-JDBC-connection lifetime.
- verify diagnostics or structured unsupported: `SqliteDiagnosticsProviderTest`
  and `AgentPromptContractTest` passed.
- verify ER Designer CREATE-only SQLite behavior: `SqliteDdlGeneratorTest`
  passed.

- [ ] **Step 3: Commit implementation**

Run:

```bash
git status --short
git add server client docs
git commit -m "feat(data-sources): complete sqlite coverage"
```

Expected: commit succeeds after all checks pass.

Status note: deferred for now because the shared `develop` worktree contains
other unrelated local changes and untracked Wave B planning artifacts. Commit
only the SQLite slice after those unrelated changes are separated or approved
for inclusion.
