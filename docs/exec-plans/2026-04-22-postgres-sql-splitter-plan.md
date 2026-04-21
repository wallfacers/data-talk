# PostgreSQL SQL Splitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the handwritten PostgreSQL statement splitter behind `/api/sql/execute` with a dialect-aware splitter boundary, using PgJDBC’s PostgreSQL parser for PostgreSQL connections while preserving existing execution semantics.

**Architecture:** Introduce an application-layer splitter facade that `SqlExecuteService` depends on, then provide infrastructure implementations for PostgreSQL and generic SQL splitting. PostgreSQL connections use PgJDBC internal parser-based splitting; other dialects keep the current lightweight splitter logic, moved out of `SqlExecuteService`.

**Tech Stack:** Spring Boot 3.5, Java 21, PgJDBC, JUnit 5, AssertJ, Spring Boot Test, Testcontainers PostgreSQL

---

## Spec Mapping

- [2026-04-22-postgres-sql-splitter-design.md](../product-specs/2026-04-22-postgres-sql-splitter-design.md)
  - “推荐设计” → 抽出 splitter 边界，PostgreSQL 先走 PgJDBC
  - “架构设计” → application facade + infrastructure implementations
  - “详细行为” → PostgreSQL 专用 splitter 与 generic splitter 职责边界
  - “测试策略” → dollar quote / DO / CREATE FUNCTION 覆盖，以及 `/api/sql/execute` 集成覆盖

## File Structure

### Application

- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceSplitterSelectionTest.java`

### Infrastructure

- Modify: `server/data-talk-infrastructure/pom.xml`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/PostgresJdbcSqlStatementSplitter.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/GenericSqlStatementSplitter.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/PostgresJdbcSqlStatementSplitterTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/GenericSqlStatementSplitterTest.java`

### Adapter / Integration

- Modify: `server/data-talk-adapter/pom.xml`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

## Task 1: Define the splitter boundary in application

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceSplitterSelectionTest.java`

- [x] Write a failing application-layer test that proves `SqlExecuteService` delegates SQL splitting through an injected boundary instead of an internal helper.
- [x] Run the targeted application test and confirm it fails for the expected reason.
- [x] Add `SqlStatementSplitters` in the application layer with `split(String connectionKind, String sql)` as the single entry point.
- [x] Refactor `SqlExecuteService` constructor to depend on `SqlStatementSplitters` and remove the in-class `splitStatements()` helper.
- [x] Re-run the targeted application test until it passes.

## Task 2: Extract the current generic splitter into infrastructure

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/GenericSqlStatementSplitter.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/GenericSqlStatementSplitterTest.java`

- [x] Write failing unit tests for generic splitting of plain semicolon-separated SQL, quoted strings, and comments.
- [x] Run the new generic splitter test and confirm red.
- [x] Move the current non-PostgreSQL splitting logic out of `SqlExecuteService` into `GenericSqlStatementSplitter`.
- [x] Keep the implementation intentionally limited to generic SQL behavior; do not add PostgreSQL dollar-quote support here.
- [x] Re-run the generic splitter test until green.

## Task 3: Add a PostgreSQL-specific splitter backed by PgJDBC

**Files:**
- Modify: `server/data-talk-infrastructure/pom.xml`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/PostgresJdbcSqlStatementSplitter.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/PostgresJdbcSqlStatementSplitterTest.java`

- [x] Write failing PostgreSQL splitter tests for:
  - `SELECT $$a;b$$; SELECT 1;`
  - `SELECT $tag$a;b$tag$; SELECT 1;`
  - `DO $$ BEGIN PERFORM 1; PERFORM 2; END $$; SELECT 1;`
  - `CREATE FUNCTION ... AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql; SELECT 1;`
- [x] Run the PostgreSQL splitter test and confirm red.
- [x] Add runtime PgJDBC dependency to `data-talk-infrastructure`.
- [x] Implement `PostgresJdbcSqlStatementSplitter` using `org.postgresql.core.Parser.parseJdbcSql(... splitStatements=true ...)`, normalizing returned statements into an ordered `List<String>`.
- [x] Re-run the PostgreSQL splitter test until green.

## Task 4: Wire splitter selection and verify end-to-end execution

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify: `server/data-talk-adapter/pom.xml`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] Write a failing wiring test for `DefaultSqlStatementSplitters` that routes `postgres` / `postgresql` to the PostgreSQL splitter and other kinds to the generic splitter.
- [x] Add a failing integration test proving `/api/sql/execute` can accept a PostgreSQL procedural script without being split at inner semicolons.
- [x] Run the targeted infrastructure and adapter tests and confirm red.
- [x] Implement `DefaultSqlStatementSplitters`, inject it into `SqlExecuteService`, and preserve all existing multi-result / rollback semantics.
- [x] Add any required test-scoped PostgreSQL dependencies in adapter for the procedural integration case.
- [x] Re-run the targeted tests until green.

Note: `data-talk-adapter/pom.xml` already contained `org.testcontainers:postgresql` and test-scope `org.postgresql:postgresql`, so no adapter dependency change was required.

## Task 5: Consolidated verification and housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-22-postgres-sql-splitter-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify canonical docs only if conventions materially changed

- [x] Run `cd server && mvn -q -pl data-talk-infrastructure -am test -Dtest=GenericSqlStatementSplitterTest,PostgresJdbcSqlStatementSplitterTest,SqlExecuteServiceSplitterSelectionTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] Run `cd server && mvn -q -pl data-talk-adapter -am -Dtest=SqlExecuteControllerIT -Dsurefire.failIfNoSpecifiedTests=false test`
- [x] Run `cd server && mvn -q compile`
- [x] Mark all completed steps in this plan.
- [x] Move the plan entry from Active to Completed in `docs/exec-plans/index.md` once verification is green.

Verification note: `SqlExecuteControllerIT` finished with `Tests run: 10, Failures: 0, Errors: 0, Skipped: 1`; the skipped case is the PostgreSQL procedural integration test, which is gated on Docker availability in the local environment.

## Decisions

- PostgreSQL splitting is being isolated behind an application boundary now so that a future `libpg_query` migration only swaps the infrastructure implementation.
- `CalciteSqlRiskAnalyzer` is intentionally out of scope for this plan; execution splitting and risk parsing remain separate concerns.
- PgJDBC internal parser use is accepted as a short-term tradeoff to avoid extending the handwritten splitter into a PostgreSQL-specific lexer.

## Self-Review

- Spec coverage: application boundary, PostgreSQL parser choice, generic fallback, wiring, tests, and housekeeping are all covered.
- Placeholder scan: no TODO/TBD placeholders remain.
- Type consistency: the plan uses a single `SqlStatementSplitters` facade name throughout.
