# Data Source Coverage: ClickHouse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified ClickHouse support as canonical kind `clickhouse`, with explicit analytical SQL, type, mutation, and diagnostics behavior.

**Architecture:** ClickHouse uses its own JDBC driver, HTTP(S)-oriented URL semantics, and distinctive types. The implementation must add dedicated kind routing, connection options, metadata, SQL execution, splitter/risk rules, type normalization, frontend/MCP exposure, and structured unsupported responses for unimplemented capabilities.

**Tech Stack:** Java 21, Spring Boot 3.5, ClickHouse JDBC driver candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-clickhouse-design.md` is
reviewed and approved. ClickHouse remains unsupported until every verification
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
- UI-specific ClickHouse constraints: label `ClickHouse`, default port `8123`,
  explicit protocol/SSL controls, database-only day-1 context, and visible
  unsupported states for transaction, ER, and diagnostics gaps.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `clickhouse` support before backend,
  frontend, prompt, and compatibility-gate verification are complete.

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

### Task 1: Approval, Driver, And Connection Contract

- [ ] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-clickhouse-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [ ] **Step 2: Record driver and URL decisions**

Record driver artifact/version/classifier/license, whether the selected driver
uses v1 or v2 implementation, shaded vs thin packaging, native HTTP client
dependencies, driver class, URL protocol, default port `8123`,
SSL/compression/timeout settings, and whether native protocol support is
excluded from day-1.

### Task 2: Connection And Metadata

- [ ] **Step 1: Add failing connection tests**

Cover canonical kind routing, URL generation for HTTP and HTTPS, default port,
database selection, connection-test failure localization, and secret redaction.

- [ ] **Step 2: Implement connection behavior**

Add driver dependency, URL builder branch, validation behavior, and approved
connection properties.

- [ ] **Step 3: Add failing metadata tests**

Cover database discovery, internal database filtering, bounded schema discover,
explicit describe, and metadata fields such as engine/order/partition where
available.

- [ ] **Step 4: Implement metadata behavior**

Use JDBC metadata and bounded system-table queries only where tests prove
behavior and privilege requirements are acceptable.

### Task 3: SQL Execution, Type Normalization, Splitter, And Risk

- [ ] **Step 1: Add failing SQL and type tests**

Cover read-only execution, selected database context, unsigned integer and
large decimal normalization, UUID/IP/date/time/array/tuple/map handling, and
driver-specific objects.

- [ ] **Step 2: Add failing splitter and risk tests**

Cover ClickHouse comments, strings, settings, format clauses, `SYSTEM`,
`OPTIMIZE`, `KILL QUERY`, `ATTACH`, `DETACH`, grants, broad DDL, and SELECT
table functions that can trigger server-side file or network access such as
`remote`, `url`, `s3`, `file`, `hdfs`, `postgresql`, `mongodb`, `odbc`,
`jdbc`, `cluster`, and `clusterAllReplicas`.

- [ ] **Step 3: Implement SQL, normalization, splitter, and risk**

Add dedicated handling where generic JDBC behavior is insufficient.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=JdbcUrlBuilderTest,ConnectionTargetDiscoveryServiceTest,ReadSchemaActionIT,SqlExecuteServiceTest,JdbcResultValueNormalizerTest,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Diagnostics, ER, Frontend, MCP, And Prompt

- [ ] **Step 1: Add failing diagnostics and ER tests**

Cover EXPLAIN mapping or structured unsupported, table-space system queries or
structured unsupported, relation discovery unsupported, and DDL generation
unsupported unless ClickHouse table-engine decisions exist.

- [ ] **Step 2: Implement diagnostics and ER behavior**

Return structured unsupported for every unverified capability.

- [ ] **Step 3: Add failing frontend and prompt tests**

Cover label, port `8123`, protocol/SSL fields, Query Editor database context,
formatter/outline behavior, diagnostics unsupported UI, i18n, MCP schema, and
runtime prompt claims.

- [ ] **Step 4: Implement frontend and MCP exposure**

Expose `clickhouse` only after backend behavior is complete.

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

Keep ClickHouse unsupported if any gate remains incomplete.
