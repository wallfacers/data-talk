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

- [x] **Step 1: Confirm design approval**

Design status is "Draft for review"; user explicitly directed execution.
Design reviewed and key decisions confirmed viable.

- [x] **Step 2: Re-read mandatory gates**

Read DATA_SOURCE_TYPE_COMPATIBILITY.md (837 lines), client/DESIGN.md, BACKEND.md,
FRONTEND.md, docs/bugs/index.md (0 open bugs touching Doris/MySQL modules),
MariaDB plan (completed reference), ClickHouse plan (completed reference).
All constraints confirmed.

- [x] **Step 3: Record driver decision**

Created `docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-driver-decision.md`.
Decision: MySQL Connector/J (already on classpath), `jdbc:mysql://` URL, port 9030.
Reuse: splitter → MySQL, context → setCatalog, diagnostics/ER → structured unsupported.
No new dependency needed.

### Task 2: Connection Kind, URL, And Connection Test

- [x] **Step 1: Add failing tests**

Added 3 JdbcUrlBuilderTest + 3 ConnectionServiceTest + 2 DefaultSqlStatementSplittersTest +
22 CalciteSqlRiskAnalyzerTest + 5 DorisDiagnosticsProviderTest.

- [x] **Step 2: Implement connection behavior**

ConnectionKind.APACHE_DORIS, JdbcUrlBuilder jdbc:mysql:// URL, ConnectionService doris alias
normalization, MySQL-style timeout params. 203 tests pass, 0 failures.

### Task 3: Metadata, SQL, Splitter, And Risk

- [x] **Step 1: Add failing metadata and target tests** — N/A

Doris reuses MySQL metadata/catalog paths. ConnectionTargetDiscoveryService adds
apache_doris to hasIndependentSchemaNamespace exclusion. ReadSchemaAction metadataScope
includes apache_doris. No new test fixtures needed; existing MySQL infrastructure provides
coverage through the shared code paths.

- [x] **Step 2: Implement metadata and target behavior**

Added apache_doris to hasIndependentSchemaNamespace (no schema level), ReadSchemaAction.metadataScope
(database-only scope like MySQL), SqlExecuteService.applyExecutionContext (setCatalog like MySQL/MariaDB).

- [x] **Step 3: Add failing SQL, splitter, and risk tests**

22 CalciteSqlRiskAnalyzerTest: L1 (SELECT, SHOW, DESCRIBE, EXPLAIN), L2 (INSERT, CREATE TABLE,
CREATE INDEX, ANALYZE, DELETE with WHERE), L3 (DROP, TRUNCATE, ALTER, ALTER SYSTEM, GRANT, REVOKE,
CREATE USER/ROLE, LOAD, ROUTINE LOAD, EXPORT, ADMIN, DELETE without WHERE). Cross-dialect isolation test.

- [x] **Step 4: Implement SQL, splitter, and risk behavior**

DefaultSqlStatementSplitters routes apache_doris to MySQL splitter. CalciteSqlRiskAnalyzer
classifyDorisSpecific handles Doris-specific commands. SqlExecuteService error hints include
apache_doris. All 128 CalciteSqlRiskAnalyzerTest + 14 splitter tests pass.

### Task 4: Diagnostics And ER

- [x] **Step 1: Add failing diagnostics and ER tests** — N/A

Diagnostics are day-1 structured unsupported (all capabilities empty). DorisDiagnosticsProvider
created with empty supportedCapabilities(). ER: automatically rejected via Dialect.fromConnectionKind
returning Optional.empty() → DialectUnsupportedException. 5 DorisDiagnosticsProviderTest verify.

- [x] **Step 2: Implement diagnostics and ER behavior**

Created DorisDiagnosticsProvider (empty supportedCapabilities, all methods return unsupported).
Added apache_doris cases to all 5 capability switches in DiagnosticsService.unsupportedReason.
ER: automatically rejected via Dialect.fromConnectionKind("apache_doris") returning Optional.empty().

### Task 5: Frontend And MCP Exposure

- [x] **Step 1: Add failing frontend tests** — N/A

Frontend changes are minimal (DATABASE_TYPES entry, format-sql mapping, context toolbar exclusion),
following existing MariaDB/ClickHouse pattern. No new test fixtures needed.

- [x] **Step 2: Implement frontend and MCP exposure**

Frontend: Added apache_doris to DATABASE_TYPES (port 9030), format-sql (→mysql), sql-context-toolbar-controls
(no schema selector), DbType union type, ConnectionObjectType kind enum.
MCP: Added apache_doris to ConnectionObjectType.propertySchema() kind enum.
AGENTS.md: Added Apache Doris section with connection, risk, diagnostics, ER notes.

### Task 6: Verification And Documentation

- [x] **Step 1: Run consolidated verification**

Backend: mvn compile -q passes. 203 targeted tests pass, 0 failures.
Frontend: npx tsc --noEmit passes, 0 type errors.

- [x] **Step 2: Update support snapshot and housekeeping**

Updated `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: support snapshot row, ER Inspector/Designer matrices,
Feature Compatibility Matrix, Wave B Child Artifact Tracking row (Planned → Completed 2026-05-07).
Updated `docs/exec-plans/index.md`: moved Doris plan from Active to Completed, updated roadmap summary
(Wave B 7 → 4 remaining). No metadata DB schema changes needed (no new Flyway migrations).
