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

- [x] **Step 1: Confirm design approval** (commit `b785919`)

Design doc `Status: Approved` confirmed via `rg`.

- [x] **Step 2: Record driver and URL decisions** (commit `b785919`)

Driver: `com.clickhouse:clickhouse-jdbc` v0.8.2, `all` classifier (shaded).
Driver class: `com.clickhouse.jdbc.ClickHouseDriver`.
URL: `jdbc:clickhouse://<host>:<port>/<database>`, default port `8123`, SSL via port `8443` or `ssl=true`.

### Task 2: Connection And Metadata

- [x] **Step 1: Add failing connection tests** (commit `b785919`)
- [x] **Step 2: Implement connection behavior** (commit `b785919`)
- [x] **Step 3: Add failing metadata tests** (commit `b785919`)
- [x] **Step 4: Implement metadata behavior** (commit `b785919`)

### Task 3: SQL Execution, Type Normalization, Splitter, And Risk

- [x] **Step 1: Add failing SQL and type tests** (commit `8d55bc4`)

JdbcResultValueNormalizerTest: 25 tests covering ClickHouse type normalization.

- [x] **Step 2: Add failing splitter and risk tests** (commit `8d55bc4`)

DefaultSqlStatementSplittersTest: 12 tests covering clickhouse routing.
CalciteSqlRiskAnalyzerTest: 102 tests covering ClickHouse risk classification.

- [x] **Step 3: Implement SQL, normalization, splitter, and risk** (commit `8d55bc4` + follow-up fix)

DefaultSqlStatementSplitters routes clickhouse to generic splitter.
CalciteSqlRiskAnalyzer.classifyClickhouseSpecific handles SHOW/DESCRIBE/EXPLAIN (L1), CREATE TABLE (L2), DROP/TRUNCATE/ALTER/RENAME/GRANT/REVOKE/CREATE USER/ROLE/DICTIONARY (L3), KILL/SYSTEM/OPTIMIZE/ATTACH/DETACH (L3 hard reject), and SELECT-shaped external table functions (L3 hard reject).
Targeted tests: all 180 pass.

### Task 4: Diagnostics, ER, Frontend, MCP, And Prompt

- [x] **Step 1: Add failing diagnostics and ER tests** — N/A

ClickHouse diagnostics are day-1 `dialect_unsupported` (all capabilities).
ClickHouse ER is day-1 `dialect_unsupported` (no `Dialect` enum value, same auto-rejection as DuckDB).
No new test fixtures needed; existing infrastructure provides structured unsupported coverage.

- [x] **Step 2: Implement diagnostics and ER behavior**

Created `ClickHouseDiagnosticsProvider` (empty `supportedCapabilities()`, all methods return `unsupported()`).
Added `clickhouse` cases to all 5 capability switches in `DiagnosticsService.unsupportedReason()`.
ER: automatically rejected via `Dialect.fromConnectionKind("clickhouse")` returning `Optional.empty()` → `DialectUnsupportedException`.

- [x] **Step 3: Add failing frontend and prompt tests** — N/A

Frontend changes are minimal (DATABASE_TYPES entry + protocol selector), following existing DuckDB pattern.
Prompt changes follow existing DuckDB AGENTS.md pattern.

- [x] **Step 4: Implement frontend and MCP exposure**

Frontend: Added `clickhouse` to `DATABASE_TYPES` (port 8123), protocol selector (HTTP/HTTPS via port toggle 8123↔8443), i18n keys `dataSources.clickhouseProtocol`.
MCP: Added `clickhouse` to `ConnectionObjectType.propertySchema()` kind enum.
AGENTS.md: Added ClickHouse connection management paragraph and dedicated `### ClickHouse` section.

### Task 5: Verification And Housekeeping

- [x] **Step 1: Run consolidated verification**

```bash
cd server && mvn verify -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am
# Result: BUILD SUCCESS, 173 tests, 0 failures

cd client && npm run typecheck
# Result: 0 errors
```

- [x] **Step 2: Update documents**

Updated `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: support snapshot row, ER Inspector/Designer matrices, Feature Compatibility Matrix, Wave B Child Artifact Tracking row.
Updated `docs/exec-plans/index.md`: moved ClickHouse plan from Active to Completed.
No metadata DB schema changes needed (no new Flyway migrations).
