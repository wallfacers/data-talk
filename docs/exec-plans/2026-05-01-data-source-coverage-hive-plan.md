# Data Source Coverage: Hive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add conservative Apache HiveServer2 support as canonical kind `hive`, without overclaiming unimplemented auth, metadata, or diagnostics behavior.

**Architecture:** Hive support must first choose a day-1 transport/auth subset. The implementation then adds kind routing, URL construction, metadata fallback, guarded SQL execution, Hive-specific splitter/risk rules, structured unsupported diagnostics/ER behavior, and frontend/MCP/runtime contracts aligned to the selected subset.

**Tech Stack:** Java 21, Spring Boot 3.5, Hive JDBC candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-hive-design.md` is reviewed
and approved. Hive remains unsupported until every verification step passes and
`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is updated.

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
- UI-specific Hive constraints: label `Apache Hive`, default binary transport
  port `10000`, day-1 binary transport plus username/password or username-only
  auth only, and visible unsupported states for HTTP, SSL, Kerberos, ZooKeeper,
  diagnostics, and ER gaps until separately implemented.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `hive` support before backend, frontend,
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
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

### Task 1: Approval, Driver, And Auth Scope

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-hive-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [ ] **Step 2: Record support subset**

Confirm the design-anchored minimum subset still holds: HiveServer2 binary
transport on port `10000`, database context, username/password or username-only
auth, and structured unsupported responses for HTTP, SSL, Kerberos, ZooKeeper,
custom headers/cookies, and Knox-style deployments. Record selected Hive JDBC
artifact/version/license, dependency packaging, and driver class. If the
minimum subset changes, stop and revise the design before implementation.

### Task 2: Connection And Metadata

- [ ] **Step 1: Add failing connection tests**

Cover canonical kind routing, `jdbc:hive2://` URL building, selected transport
mode, default port `10000`, password behavior, unsupported auth rejection, and
localized connection-test failures.

- [ ] **Step 2: Implement connection behavior**

Add driver dependency, URL builder support, selected auth fields, and structured
unsupported errors for excluded auth modes.

- [ ] **Step 3: Add failing metadata tests**

Cover database discovery through JDBC metadata or bounded SQL fallback, system
filtering, bounded schema discover, explicit describe, and partition metadata.

- [ ] **Step 4: Implement metadata behavior**

Use metadata APIs only where verified; otherwise implement bounded Hive SQL
fallbacks.

### Task 3: SQL, Splitter, Risk, Diagnostics, And ER

- [ ] **Step 1: Add failing SQL and normalization tests**

Cover selected database context, primitive and complex values, query
cancellation if claimed, and chat-path mutation blocking.

- [ ] **Step 2: Add failing splitter and risk tests**

Cover `SET`, `ADD JAR`, `LOAD DATA`, `TRANSFORM`, `CREATE FUNCTION`, partition
operations, grants, `DROP`, `TRUNCATE`, and broad `ALTER`.

- [ ] **Step 3: Implement SQL, normalization, splitter, and risk**

Keep rollback claims disabled unless Hive transaction behavior is proven.

- [ ] **Step 4: Implement diagnostics and ER behavior**

Map EXPLAIN only if tested. Return structured unsupported for lock, pool, table
space, terminate, optimize, ER Inspector, and ER Designer until real support
exists.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=JdbcUrlBuilderTest,ConnectionTargetDiscoveryServiceTest,ReadSchemaActionIT,SqlExecuteServiceTest,JdbcResultValueNormalizerTest,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,DiagnosticsServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Frontend, MCP, Prompt, And Verification

- [ ] **Step 1: Add failing frontend and prompt tests**

Cover label `Apache Hive`, default port `10000`, selected auth fields,
unsupported auth messaging, Query Editor database context, formatter/outline,
diagnostics state, i18n, MCP schema, and runtime prompt claims.

- [ ] **Step 2: Implement frontend and MCP exposure**

Expose only the selected Hive support subset. Keep excluded auth modes and
diagnostics explicitly unsupported.

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

Keep Hive unsupported or partial if any first-class gate remains incomplete.
