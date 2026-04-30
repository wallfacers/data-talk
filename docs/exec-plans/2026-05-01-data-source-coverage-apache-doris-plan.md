# Data Source Coverage: Apache Doris Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified Apache Doris support as canonical kind `apache_doris`, without silently treating Doris as MySQL.

**Architecture:** Doris support starts from a MySQL-protocol hypothesis, but every reuse point must be proven by Doris-specific tests. The implementation owns kind normalization, JDBC URL construction, metadata, SQL execution, splitter/risk behavior, diagnostics honesty, frontend exposure, MCP schemas, prompt guidance, and support-snapshot updates.

**Tech Stack:** Java 21, Spring Boot 3.5, JDBC/MySQL Connector/J candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md` is
reviewed and approved. Apache Doris remains unsupported until every verification
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
- UI-specific Doris constraints: label `Apache Doris`, default FE query port
  `9030`, database-only day-1 context unless schema behavior is proven, and no
  MySQL-branded display for Doris connections.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `apache_doris` support before backend,
  frontend, prompt, and compatibility-gate verification are complete.

## Files

Expected backend files:

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
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

Expected frontend files:

- `client/src/services/api/connection.ts`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/session/data-source-picker/`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`

### Task 1: Approval, Gate, And Driver Decision

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [ ] **Step 2: Re-read mandatory gates**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
sed -n '1,360p' client/DESIGN.md
```

Expected: backend, frontend, MCP, semantic token, global Stage, accessibility,
and i18n constraints are visible.

- [ ] **Step 3: Record driver decision**

Create or update
`docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-driver-decision.md`
before Task 2 starts. It must include a decision table for MySQL Connector/J and
MariaDB Connector/J candidates: artifact/version, license, shaded/transitive
packaging, driver class, Doris protocol behavior, timeout properties, known
Doris quirks, and whether MySQL splitter/diagnostics/ER paths may be reused.
If license review is incomplete, do not start Task 2.

### Task 2: Connection Kind, URL, And Connection Test

- [ ] **Step 1: Add failing tests**

Cover `apache_doris` canonical persistence, `doris` alias normalization, URL
construction with default port `9030`, null database behavior, driver loading,
and localized connection-test failures.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests fail until Doris connection support exists.

- [ ] **Step 2: Implement connection behavior**

Add canonical kind routing, alias normalization, URL construction, driver
dependency, and connection-test behavior. Do not persist Doris as `mysql`.

Run the same Maven command. Expected: targeted tests pass.

### Task 3: Metadata, SQL, Splitter, And Risk

- [ ] **Step 1: Add failing metadata and target tests**

Cover database discovery, system database filtering, `resolve_use_target`
matched/ambiguous/not-found cases, bounded `datatalk_read_schema` discover, and
explicit describe with selected database context.

- [ ] **Step 2: Implement metadata and target behavior**

Reuse MySQL database/catalog paths only where Doris tests pass.

- [ ] **Step 3: Add failing SQL, splitter, and risk tests**

Cover read-only SQL, chat-path mutation blocking, MySQL splitter reuse or
fallback, Doris load/alter/drop management statements, and result normalization.

- [ ] **Step 4: Implement SQL, splitter, and risk behavior**

Route Doris through proven components only. Add Doris-specific guard branches
where MySQL behavior is wrong.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=ConnectionTargetDiscoveryServiceTest,UseTargetResolverTest,ReadSchemaActionIT,SqlExecuteServiceTest,ExecuteSqlActionIT,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics And ER

- [ ] **Step 1: Add failing diagnostics and ER tests**

Cover EXPLAIN behavior, structured unsupported diagnostics, ER Inspector
structured unsupported or metadata-backed relation discovery, and ER Designer
DDL structured unsupported or Doris-specific generation.

- [ ] **Step 2: Implement diagnostics and ER behavior**

Add real Doris diagnostics only where SQL and privileges are proven. Otherwise
return structured unsupported responses.

### Task 5: Frontend And MCP Exposure

- [ ] **Step 1: Add failing frontend tests**

Cover label `Apache Doris`, default port `9030`, alias display behavior, Query
Editor database context, formatter/outline mapping, diagnostics unsupported
state, and i18n.

- [ ] **Step 2: Implement frontend and MCP exposure**

Expose `apache_doris` only after backend behavior is complete. Update runtime
`AGENTS.md` without overclaiming diagnostics, ER, or mutation support.

### Task 6: Verification And Documentation

- [ ] **Step 1: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
git diff --check -- server client docs
```

Expected: all commands pass.

- [ ] **Step 2: Update support snapshot and housekeeping**

Do all housekeeping before claiming completion:

- update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` current support snapshot;
- update the Wave B Child Artifact Tracking row's Current outcome;
- update generated schema docs if needed;
- mark this plan's checkboxes and move it from Active to Completed in
  `docs/exec-plans/index.md`.

Keep support status unchanged if any acceptance gate remains incomplete.
