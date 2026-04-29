# SQL DML Batch Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add execution-layer batching for consecutive DML and same-table `INSERT ... VALUES` rewrite while preserving existing `/api/sql/execute` response semantics.

**Architecture:** `SqlExecuteService` keeps splitter, risk gate, transaction, and result contract. A new application-layer `SqlExecutionPlanner` converts split statements into single-statement and DML-batch execution units; service execution consumes those units and records the same `dml_summary` shape as today.

**Tech Stack:** Java 21, JDBC `Statement.addBatch` / `executeBatch`, JUnit 5, AssertJ, Maven.

---

## Compatibility Gate

This plan applies `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` because it changes SQL execution behavior.

- Domain Layer: N/A; no domain enum or sealed contract changes.
- Application SQL Execution: add planner and batch execution in `SqlExecuteService`.
- SQL Statement Splitting: N/A; uses existing splitter output.
- SQL Risk Analysis And Guards: N/A; risk analysis remains on original SQL.
- Frontend: N/A; response contract remains unchanged.

## Files

- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecutionPlanner.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecutionPlannerTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/design-docs/index.md`
- Modify: `docs/exec-plans/index.md`

### Task 1: Planner tests

- [x] **Step 1: Write failing planner tests**

Add tests that assert:

- Consecutive same-prefix `INSERT INTO t(id, x) VALUES ...` statements become one `DmlBatch` with rewritten SQL.
- `INSERT INTO t SELECT ...` remains a JDBC batch candidate, not a rewrite.
- `SELECT` splits DML runs so result-set ordering remains intact.

- [x] **Step 2: Run planner tests and verify failure**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn -q -pl data-talk-application -Dtest=SqlExecutionPlannerTest test
```

Expected: test compilation fails because `SqlExecutionPlanner` does not exist.

Status: verified. Test compilation failed because `SqlExecutionPlanner` did not exist.

### Task 2: Planner implementation

- [x] **Step 1: Implement `SqlExecutionPlanner`**

Implement `SingleStatement` and `DmlBatch` units, narrow insert rewrite detection, and conservative DML batch grouping.

- [x] **Step 2: Run planner tests**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn -q -pl data-talk-application -Dtest=SqlExecutionPlannerTest test
```

Expected: tests pass.

Status: passed.

### Task 3: Service integration tests

- [x] **Step 1: Write failing service tests**

Add `SqlExecuteServiceTest` coverage for repeated same-table inserts followed by `SELECT`, and for mixed update/delete DML batch returning one `dml_summary`.

- [x] **Step 2: Run service tests and verify failure**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn -q -pl data-talk-application -Dtest=SqlExecuteServiceTest test
```

Expected: at least the planner-observable behavior fails until `SqlExecuteService` consumes execution units.

Status: verified. The strategy test failed because `SqlExecuteService` still called `Statement.execute()` for every statement.

### Task 4: Service implementation

- [x] **Step 1: Update `SqlExecuteService`**

Replace the direct statement loop with planner unit execution. Use `Statement.execute()` for single units, `executeUpdate()` for rewritten inserts, and `addBatch()` / `executeBatch()` for DML batch units.

- [x] **Step 2: Run service tests**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn -q -pl data-talk-application -Dtest=SqlExecuteServiceTest test
```

Expected: tests pass.

Status: passed.

### Task 5: Documentation and housekeeping

- [x] **Step 1: Update compatibility docs**

Document DML batch and insert rewrite rules in `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`.

- [x] **Step 2: Mark plan complete**

Mark this plan's checkboxes complete and move the index entry from Active to Completed.

### Task 6: Verification

- [x] **Step 1: Run targeted application tests**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn -q -pl data-talk-application -Dtest=SqlExecutionPlannerTest,SqlExecuteServiceTest,CalciteSqlRiskAnalyzerTest test
```

Expected: tests pass.

Status: passed.

- [x] **Step 2: Run backend compile**

Run:

```bash
cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 PATH=/home/wushengzhou/.local/opt/java/jdk-21.0.9/bin:$PATH mvn compile -q
```

Expected: zero compilation errors.

Status: passed.
