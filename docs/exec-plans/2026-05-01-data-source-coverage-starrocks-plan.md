# Data Source Coverage: StarRocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified StarRocks support as canonical kind `starrocks`, including explicit catalog/database handling.

**Architecture:** StarRocks uses a native JDBC driver and URL shape containing `catalog.database`. The implementation must introduce explicit kind routing, decide persistence for catalog/database context, prove metadata and SQL behavior, expose honest diagnostics, and update frontend/MCP/runtime contracts only after backend support is real.

**Tech Stack:** Java 21, Spring Boot 3.5, StarRocks JDBC driver candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-starrocks-design.md` is
reviewed and approved. StarRocks remains unsupported until every verification
step passes and `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is updated.

## Design Inputs

- `client/DESIGN.md` applies to the connection form, data-source picker, Query
  Editor context controls, diagnostics states, and unsupported states:
  semantic tokens only; Chat and Workbench remain one visual system; Stage
  state is global; controls need explicit hover/focus/disabled/error/selected
  and loading states;
  all user-visible strings go through `client/src/i18n/messages.ts`.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is the hard gate. Canonical kind
  normalization must have one boundary, preferably `ConnectionKind.normalize`
  in the application layer before persistence and routing. Do not add scattered
  `equalsIgnoreCase` checks.
- UI-specific StarRocks constraints: label `StarRocks`, default FE query port
  `9030`, explicit catalog/database fields, default catalog `default_catalog`,
  and no MySQL-branded display for StarRocks connections.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `starrocks` support before backend,
  frontend, prompt, and compatibility-gate verification are complete.

## Files

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-infrastructure/pom.xml`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/session/data-source-picker/`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

### Task 1: Approval, Gate, And Driver Decision

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-starrocks-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [ ] **Step 2: Record driver and persistence decisions**

Record selected driver artifact/version/license, driver class, URL field model,
default port `9030`, SSL/timeout behavior, and whether catalog requires new
persistence fields.

### Task 2: Connection And Catalog Model

- [ ] **Step 1: Add failing tests**

Cover canonical `starrocks` routing, URL creation for
`jdbc:starrocks://host:9030/default_catalog.db`, default catalog filling,
missing database validation, connection-test errors, and rejection of ambiguous
field combinations.

- [ ] **Step 2: Implement connection behavior**

Add driver dependency, URL builder support, connection validation, and any
approved persistence/migration work.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 3: Target Discovery, Schema Read, SQL, Splitter, And Risk

- [ ] **Step 1: Add failing target and schema tests**

Cover catalog listing, database listing, `catalog.database` resolution,
ambiguous names, bounded schema discover, and explicit describe.

- [ ] **Step 2: Implement target and schema behavior**

Use StarRocks JDBC metadata where reliable; use SQL fallback only with bounded
queries and tests.

- [ ] **Step 3: Add failing SQL and guard tests**

Cover selected context, read-only execution, chat mutation blocking, splitter
selection, catalog/load/cluster-management risk classification, and value
normalization.

- [ ] **Step 4: Implement SQL, splitter, and risk behavior**

Route StarRocks to proven components or dedicated code paths.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT,SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics, ER, Frontend, And MCP

- [ ] **Step 1: Add failing diagnostics and ER tests**

Cover EXPLAIN mapping, structured unsupported responses, relation discovery,
and DDL generation support or explicit unsupported responses.

- [ ] **Step 2: Implement diagnostics and ER behavior**

Return structured unsupported for every unverified diagnostic or ER feature.

- [ ] **Step 3: Add failing frontend and prompt tests**

Cover label, default port, catalog/database fields, Query Editor context,
formatter/outline mapping, diagnostics unsupported UI, i18n, MCP schema, and
runtime prompt claims.

- [ ] **Step 4: Implement frontend and MCP exposure**

Expose `starrocks` only after backend behavior and prompt contracts are honest.

### Task 5: Verification And Housekeeping

- [ ] **Step 1: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
git diff --check -- server client docs
```

Expected: all commands pass.

- [ ] **Step 2: Update documents**

Do all housekeeping before claiming completion:

- update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` current support snapshot;
- update the Wave B Child Artifact Tracking row's Current outcome;
- update generated schema docs if needed;
- mark this plan's checkboxes and move it from Active to Completed in
  `docs/exec-plans/index.md`.

Keep StarRocks unsupported if any gate remains incomplete.
