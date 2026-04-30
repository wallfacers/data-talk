# Data Source Coverage: Presto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified PrestoDB support as canonical kind `presto`, separate from Trino.

**Architecture:** Presto support follows the federated catalog/schema model but must use Presto-specific driver, URL, metadata, SQL, diagnostics, and prompt contracts. Reuse with Trino is allowed only behind shared abstractions proven by tests for both engines.

**Tech Stack:** Java 21, Spring Boot 3.5, Presto JDBC driver candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-presto-design.md` is
reviewed and approved. Presto remains unsupported until every verification step
passes and `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is updated.

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
- UI-specific Presto constraints: label `Presto`, catalog then schema context
  selectors, connector-capability caveats in unsupported states, and no Trino
  aliasing.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `presto` support before backend, frontend,
  prompt, and compatibility-gate verification are complete.

## Files

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-infrastructure/pom.xml`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

### Task 1: Approval, Driver, And Separation From Trino

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-presto-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [ ] **Step 2: Record driver and reuse decisions**

Record selected Presto driver artifact/version/license, driver class,
catalog/schema persistence, auth properties, and which Trino-adjacent code can
be shared only after Presto-specific tests.

### Task 2: Connection, Target Discovery, And Schema Read

- [ ] **Step 1: Add failing connection tests**

Cover `jdbc:presto://` URL forms, catalog/schema URL segments, auth properties,
connection-test failures, and no accidental `trino` routing.

- [ ] **Step 2: Implement connection behavior**

Add driver dependency, URL builder branch, auth property handling, and localized
errors.

- [ ] **Step 3: Add failing target/schema tests**

Cover catalog discovery, schema discovery, `catalog.schema` resolution,
ambiguous target handling, missing `system.jdbc` privilege errors, bounded
discover, and explicit describe.

- [ ] **Step 4: Implement target/schema behavior**

Use Presto metadata or bounded system queries with connector-safe limits.

### Task 3: SQL, Splitter, Risk, Diagnostics, And ER

- [ ] **Step 1: Add failing SQL/type tests**

Cover selected catalog/schema context, read-only SQL, complex values, and
chat-path mutation blocking.

- [ ] **Step 2: Add failing splitter/risk tests**

Cover `CALL`, grants, roles, `CREATE TABLE AS`, `DROP`, `TRUNCATE`, broad
`ALTER`, session/system changes, and connector procedure risk.

- [ ] **Step 3: Implement SQL, normalization, splitter, and risk**

Share Trino-like code only where Presto tests pass.

- [ ] **Step 4: Implement diagnostics and ER behavior**

Map EXPLAIN only if tested. Return structured unsupported for connector-specific
diagnostics and ER features by default.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=JdbcUrlBuilderTest,ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT,SqlExecuteServiceTest,JdbcResultValueNormalizerTest,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,DiagnosticsServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Frontend, MCP, Prompt, And Verification

- [ ] **Step 1: Add failing frontend and prompt tests**

Cover label `Presto`, catalog/schema fields, Query Editor context controls,
formatter/outline behavior, diagnostics unsupported state, i18n, MCP schema,
and prompt separation from Trino.

- [ ] **Step 2: Implement frontend and MCP exposure**

Expose `presto` only after backend support and prompt contracts are honest.

- [ ] **Step 3: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
git diff --check -- server client docs
```

Expected: all commands pass.

- [ ] **Step 4: Update documents**

Do all housekeeping before claiming completion:

- update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` current support snapshot;
- update the Wave B Child Artifact Tracking row's Current outcome;
- update generated schema docs if needed;
- mark this plan's checkboxes and move it from Active to Completed in
  `docs/exec-plans/index.md`.

Keep Presto unsupported or partial if any gate remains incomplete.
