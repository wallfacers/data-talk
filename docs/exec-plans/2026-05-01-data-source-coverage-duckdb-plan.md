# Data Source Coverage: DuckDB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified DuckDB support as canonical kind `duckdb`, with explicit file, in-memory, read-only, and extension-safety behavior.

**Architecture:** DuckDB is an embedded database, not a host/port server. The implementation must add a connection contract for in-memory and file modes, enforce file path safety, support read-only mode, verify metadata and SQL behavior, guard filesystem-affecting SQL, and expose frontend/MCP/runtime support only after those rules are implemented.

**Tech Stack:** Java 21, Spring Boot 3.5, DuckDB JDBC driver candidate, JUnit 5, AssertJ, React 19, TypeScript, Vitest, DataTalk MCP action registry.

---

Implementation cannot begin until
`docs/product-specs/2026-05-01-data-source-coverage-duckdb-design.md` is
reviewed and approved. DuckDB remains unsupported until every verification step
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
- UI-specific DuckDB constraints: label `DuckDB`, no host/port fields, explicit
  in-memory/file mode selector, read-only flag, backend-local file path wording,
  and visible unsupported states for external file, network, extension, and
  attached-database operations.
- Invariant until this plan completes: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  and MCP schemas must not declare `duckdb` support before backend, frontend,
  prompt, and compatibility-gate verification are complete.

## Files

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
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

### Task 1: Approval, Driver, And File Safety Contract

- [x] **Step 1: Confirm design approval**

Run:

```bash
rg -n "^Status: Approved$" docs/product-specs/2026-05-01-data-source-coverage-duckdb-design.md
```

Expected: exactly one match. If the design still says `Status: Draft for
review`, stop and return to design review.

- [x] **Step 2: Record driver and file decisions**

Record driver artifact/version/license/native packaging, driver class, platform
support, in-memory URL, file URL, read-only property, backend-local data-root
sandbox field names/storage, symlink/path rules, and extension/file/cloud
operations excluded from day-1.

### Task 2: Connection Contract And Metadata

- [x] **Step 1: Add failing connection tests**

Cover canonical kind routing, `jdbc:duckdb:` in-memory URL, file URL creation,
read-only property, invalid path rejection, symlink behavior, no host/port
requirements, and localized connection-test failures.

- [x] **Step 2: Implement connection behavior**

Add driver dependency, URL builder support, file path validation, read-only
properties, and connection testing.

- [x] **Step 3: Add failing metadata tests**

Cover schema discovery, table/view listing, internal filtering, bounded schema
discover, explicit describe, keys, indexes, and comments where available.

- [x] **Step 4: Implement metadata behavior**

Use JDBC metadata and DuckDB information schema pragmas only where bounded and
tested.

### Task 3: SQL, Splitter, Risk, Diagnostics, And ER

- [x] **Step 1: Add failing SQL/type tests**

Cover in-memory and file execution, read-only mutation blocking, schema context,
decimals, huge integers, UUID, intervals, lists, structs, maps, dates/times, and
blobs.

- [x] **Step 2: Add failing splitter/risk tests**

Cover `COPY`, `EXPORT`, `IMPORT`, `ATTACH`, `DETACH`, `INSTALL`, `LOAD`,
`CREATE SECRET`, file/cloud access, broad DDL, read-only `PRAGMA` behavior, and
SELECT-shaped file/network access through `read_csv`, `read_parquet`,
`read_json`, `glob`, `parquet_metadata`, `httpfs`, `s3`, `http`, and `https`
paths.

- [x] **Step 3: Implement SQL, normalization, splitter, and risk**

Keep filesystem/network-affecting SQL behind L3 Workbench confirmation or
structured unsupported responses.

- [x] **Step 4: Implement diagnostics and ER behavior**

Map EXPLAIN only if tested. Add ER Inspector/Designer only after DuckDB metadata
and DDL tests pass; otherwise return structured unsupported or `SkippedOp`.

Run:

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure,data-talk-adapter -am test -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest,ConnectionTargetDiscoveryServiceTest,ReadSchemaActionIT,SqlExecuteServiceTest,JdbcResultValueNormalizerTest,DefaultSqlStatementSplittersTest,CalciteSqlRiskAnalyzerTest,DiagnosticsServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: targeted tests pass.

### Task 4: Frontend, MCP, Prompt, And Verification

- [x] **Step 1: Add failing frontend and prompt tests**

Cover label `DuckDB`, mode selector, file path input, read-only flag, no
host/port fields, Query Editor schema context, formatter/outline, diagnostics
state, i18n, MCP schema, and prompt filesystem warnings.

- [x] **Step 2: Implement frontend and MCP exposure**

Expose `duckdb` only after backend behavior and file-safety prompt contracts are
honest.

- [x] **Step 3: Run consolidated verification**

Run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
git diff --check -- server client docs
```

Expected: all commands pass.

- [x] **Step 4: Update documents**

Do all housekeeping before claiming completion:

- update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` current support snapshot;
- update the Wave B Child Artifact Tracking row's Current outcome;
- update generated schema docs if needed;
- mark this plan's checkboxes and move it from Active to Completed in
  `docs/exec-plans/index.md`.

Keep DuckDB unsupported or partial if any gate remains incomplete.
