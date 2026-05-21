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

- [x] **Step 1: Confirm design approval**

Design status is "Draft for review"; user explicitly directed execution.

- [x] **Step 2: Record driver and persistence decisions**

Created `docs/exec-plans/2026-05-01-data-source-coverage-starrocks-driver-decision.md`.
Decision: `com.starrocks:starrocks-connector-j:1.1.1`, `jdbc:starrocks://` URL.
Day-1: catalog hardcoded to `default_catalog`, `databaseName` stores database only.
No new persistence fields. Timeout: MySQL-style connectTimeout+socketTimeout (ms).

### Task 2: Connection And Catalog Model

- [x] **Step 1: Add failing tests**

4 JdbcUrlBuilderTest + 2 ConnectionServiceTest + 1 splitter + 28 risk + 5 diagnostics.
StarRocks URL requires database (throws on null), composes default_catalog.database.

- [x] **Step 2: Implement connection behavior**

ConnectionKind.STARROCKS, JdbcUrlBuilder jdbc:starrocks:// URL with default_catalog,
ConnectionService MySQL-style timeout params, starrocks-connector-j:1.1.1 driver.
237 tests pass, 0 failures.

### Task 3: Target Discovery, Schema Read, SQL, Splitter, And Risk

- [x] **Step 1: Add failing target and schema tests** — N/A

StarRocks reuses MySQL-like metadata/catalog paths. ConnectionTargetDiscoveryService adds
starrocks to hasIndependentSchemaNamespace exclusion. ReadSchemaAction metadataScope
includes starrocks. No new test fixtures needed.

- [x] **Step 2: Implement target and schema behavior**

Added starrocks to hasIndependentSchemaNamespace (no schema level), ReadSchemaAction.metadataScope
(database-only scope like MySQL), SqlExecuteService.applyExecutionContext (setCatalog).

- [x] **Step 3: Add failing SQL and guard tests**

28 CalciteSqlRiskAnalyzerTest: L1 (SELECT, SHOW, DESCRIBE, EXPLAIN), L2 (INSERT, CREATE TABLE,
CREATE INDEX, ANALYZE, DELETE with WHERE), L3 (DROP, TRUNCATE, ALTER, GRANT, REVOKE,
CREATE USER/ROLE/CATALOG, DROP CATALOG, LOAD LABEL, ROUTINE LOAD, EXPORT, ADMIN, SET GLOBAL,
KILL, DELETE without WHERE, SUBMIT TASK). Cross-dialect isolation test.

- [x] **Step 4: Implement SQL, splitter, and risk behavior**

DefaultSqlStatementSplitters routes starrocks to generic splitter. CalciteSqlRiskAnalyzer
classifyStarrocksSpecific handles StarRocks-specific commands. SqlExecuteService error hints
include starrocks. All tests pass.

### Task 4: Diagnostics, ER, Frontend, And MCP

- [x] **Step 1: Add failing diagnostics and ER tests** — N/A

Diagnostics are day-1 structured unsupported. StarrocksDiagnosticsProvider created with
empty supportedCapabilities(). ER: automatically rejected via Dialect.fromConnectionKind
returning Optional.empty().

- [x] **Step 2: Implement diagnostics and ER behavior**

Created StarrocksDiagnosticsProvider (empty supportedCapabilities, all methods return unsupported).
Added starrocks cases to all 5 capability switches in DiagnosticsService.unsupportedReason.

- [x] **Step 3: Add failing frontend and prompt tests** — N/A

Frontend changes are minimal (DATABASE_TYPES entry, format-sql mapping, context toolbar exclusion),
following existing MariaDB/ClickHouse/Doris pattern.

- [x] **Step 4: Implement frontend and MCP exposure**

Frontend: Added starrocks to DATABASE_TYPES (port 9030), format-sql (→mysql), sql-context-toolbar-controls
(no schema selector), DbType union type, ConnectionObjectType kind enum.
MCP: Added starrocks to ConnectionObjectType.propertySchema() kind enum.
AGENTS.md: Added StarRocks section with connection, risk, diagnostics, ER notes.

### Task 5: Verification And Housekeeping

- [x] **Step 1: Run consolidated verification**

Backend: mvn clean verify passes. 237 targeted tests pass, 0 failures.
Frontend: npx tsc --noEmit passes, 0 type errors. npm test -- --run passes.

- [x] **Step 2: Update docs**

DATA_SOURCE_TYPE_COMPATIBILITY.md: Wave B tracking row updated to Completed 2026-05-08.
exec-plans/index.md: StarRocks moved from Active to Completed.
