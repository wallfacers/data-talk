# MySQL SQL Splitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a MySQL-specific SQL statement splitter that handles semicolons inside strings, comments, identifiers, and stored-program scripts using `DELIMITER`.

**Architecture:** Keep `SqlStatementSplitters` as the application-layer boundary. PostgreSQL continues to use PgJDBC's parser; MySQL gets a dedicated infrastructure splitter; H2 and other kinds continue through the generic fallback until they have an explicit dialect decision.

**Tech Stack:** Java 21, Spring component injection, JUnit 5, AssertJ, Maven.

---

## Compatibility Gate

This plan applies `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` because it changes SQL statement splitting.

- Domain Layer: N/A; no new domain enum or sealed type branch.
- Application Connection Layer: N/A; no connection kind or JDBC URL change.
- Dynamic SQL Execution Repository: Applies only through `SqlExecuteService`'s existing splitter facade.
- SQL Statement Splitting: Add MySQL-specific splitter and update facade routing.
- SQL Risk Analysis And Guards: N/A; statement risk classification remains unchanged.
- Frontend: N/A; no client UI or formatter behavior change.

## Files

- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/MySqlSqlStatementSplitter.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/MySqlSqlStatementSplitterTest.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/exec-plans/index.md`

### Task 1: MySQL splitter tests

- [x] **Step 1: Write failing tests**

Add tests for:

- Semicolons inside single quotes, double quotes, backtick identifiers, `--`, `#`, and `/* ... */` comments.
- Backslash-escaped quotes inside MySQL strings.
- `DELIMITER` scripts for stored procedures; delimiter commands are client commands and must not be sent to JDBC.

- [x] **Step 2: Run tests to verify failure**

Run:

```bash
cd server && mvn -q -pl data-talk-infrastructure -Dtest=MySqlSqlStatementSplitterTest test
```

Expected: compilation/test failure because `MySqlSqlStatementSplitter` does not exist yet.

Status: verified with `JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9`; after correcting the shell's default Java 8 environment, the test failed at test compilation because the new splitter class did not exist.

### Task 2: MySQL splitter implementation

- [x] **Step 1: Implement minimal splitter**

Implement a Java state-machine scanner that tracks single quotes, double quotes, backticks, line comments, block comments, backslash escapes, and the current statement delimiter.

- [x] **Step 2: Run MySQL splitter tests**

Run:

```bash
cd server && mvn -q -pl data-talk-infrastructure -Dtest=MySqlSqlStatementSplitterTest test
```

Expected: tests pass.

Status: passed.

### Task 3: Facade routing

- [x] **Step 1: Add failing routing assertion**

Update `DefaultSqlStatementSplittersTest` so `mysql` routes to `MySqlSqlStatementSplitter` and handles `DELIMITER` scripts.

- [x] **Step 2: Run routing test to verify failure**

Run:

```bash
cd server && mvn -q -pl data-talk-infrastructure -Dtest=DefaultSqlStatementSplittersTest test
```

Expected: failure until `DefaultSqlStatementSplitters` routes MySQL to the new splitter.

Status: verified; test compilation failed because `DefaultSqlStatementSplitters` did not yet accept `MySqlSqlStatementSplitter`.

- [x] **Step 3: Update routing implementation**

Inject `MySqlSqlStatementSplitter` into `DefaultSqlStatementSplitters` and route `mysql` to it.

- [x] **Step 4: Run routing test**

Run:

```bash
cd server && mvn -q -pl data-talk-infrastructure -Dtest=DefaultSqlStatementSplittersTest test
```

Expected: tests pass.

Status: passed.

### Task 4: Documentation

- [x] **Step 1: Update compatibility documentation**

Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` so future database kinds must explicitly declare their SQL splitter strategy and MySQL is recorded as using a dedicated splitter with `DELIMITER` support.

- [x] **Step 2: Mark plan status**

Mark all plan tasks complete and move this plan from Active to Completed in `docs/exec-plans/index.md`.

### Task 5: Verification

- [x] **Step 1: Run targeted infrastructure tests**

Run:

```bash
cd server && mvn -q -pl data-talk-infrastructure -Dtest=MySqlSqlStatementSplitterTest,DefaultSqlStatementSplittersTest,GenericSqlStatementSplitterTest,PostgresJdbcSqlStatementSplitterTest test
```

Expected: all targeted splitter tests pass.

Status: passed.

- [x] **Step 2: Run backend compile**

Run:

```bash
cd server && mvn compile -q
```

Expected: zero compilation errors.

Status: passed.
