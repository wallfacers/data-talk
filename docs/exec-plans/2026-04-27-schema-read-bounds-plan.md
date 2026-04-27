# Schema Read Bounds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `datatalk_read_schema` bounded by default and usable for explicit table detail reads without OpenCode output truncation.

**Architecture:** Keep the action output under the existing `schema` key. Change only `ReadSchemaAction` behavior and the runtime `AGENTS.md` prompt: default calls list tables only, while calls with `tables` return columns for requested tables. Regression tests pin both behavior and prompt guidance.

**Tech Stack:** Spring Boot 3.5, Java 21, JUnit 5, AssertJ, Maven.

---

## Files

- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-27-schema-read-bounds-plan.md`

## Task 1: Document And Register Plan

- [x] **Step 1: Create design spec**

Create `docs/product-specs/2026-04-27-schema-read-bounds-design.md` describing default table summary behavior, explicit `tables` detail behavior, prompt guidance, tests, and non-goals.

- [x] **Step 2: Register design spec**

Add the design spec to `docs/product-specs/index.md` under `## 8. 个别设计文档`.

- [x] **Step 3: Create this implementation plan**

Create `docs/exec-plans/2026-04-27-schema-read-bounds-plan.md`.

- [x] **Step 4: Register active plan**

Add this plan to the Active section of `docs/exec-plans/index.md`.

## Task 2: Add Failing Tests

- [x] **Step 1: Add default-summary regression test**

In `ReadSchemaActionIT`, add a test named `readSchemaWithoutTablesReturnsTableSummaryOnly` that calls the action with `connectionId` and `schema`, then asserts table names include `users` and `orders`, and every returned table map does not contain `columns`.

- [x] **Step 2: Add explicit-table regression test**

In `ReadSchemaActionIT`, add a test named `readSchemaWithTablesReturnsOnlyRequestedColumnDetails` that calls the action with `tables = List.of("users")`, then asserts only `users` is returned and its columns include `id`, `name`, and `created_at`.

- [x] **Step 3: Add prompt contract regression test**

In `AgentPromptContractTest`, add a test named `runtimePromptDocumentsBoundedSchemaReadsAndTruncationHandling` asserting the prompt contains `Schema Reading Rules`, `explicit tables`, `table discovery`, `truncated`, and `Do not describe truncation as a tool failure`.

- [x] **Step 4: Run tests and verify RED**

Run:

```bash
JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 mvn -q -pl data-talk-adapter -am test -Dtest=ReadSchemaActionIT,AgentPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: failure because `ReadSchemaAction` still returns columns by default, ignores `tables`, and the prompt lacks the new guidance.

## Task 3: Implement Bounded Schema Reads

- [x] **Step 1: Read `tables` input safely**

In `ReadSchemaAction`, derive a requested table set from `input.get("tables")`. Treat missing, null, empty arrays, blank strings, and non-string elements as no explicit filter. Compare table names case-insensitively while preserving database table names in the output.

- [x] **Step 2: Return summary by default**

When no explicit tables are requested, add each table as `Map.of("name", name)` and skip the `meta.getColumns(...)` query.

- [x] **Step 3: Return columns only for requested tables**

When explicit tables are requested, skip unrequested tables. For requested tables, collect columns as before and return `Map.of("name", name, "columns", cols)`.

- [x] **Step 4: Run tests and verify GREEN**

Run:

```bash
JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 mvn -q -pl data-talk-adapter -am test -Dtest=ReadSchemaActionIT -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `ReadSchemaActionIT` passes.

## Task 4: Update Runtime Prompt

- [x] **Step 1: Add schema reading core rules**

In `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`, add concise English bullets under `## Core Rules`:

```markdown
- Schema Reading Rules: use `datatalk_read_schema` without `tables` only for table discovery. Pass explicit `tables` when column details are needed, and keep follow-up schema reads scoped to the tables relevant to the user's request.
- If any tool response says output was `truncated` and provides a saved file path, treat it as a large-output continuation. Inspect or search the saved output for the relevant facts, summarize only what matters, and do not describe truncation as a tool failure.
```

- [x] **Step 2: Tighten recommended workflows**

In the table-browsing and analytical workflows, change generic `datatalk_read_schema` steps to say first discover table names if needed, then pass explicit `tables` for column details.

- [x] **Step 3: Run prompt contract test**

Run:

```bash
JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 mvn -q -pl data-talk-adapter -am test -Dtest=AgentPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `AgentPromptContractTest` passes.

## Task 5: Verify And Close Documentation

- [x] **Step 1: Run backend compile**

Run:

```bash
JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 mvn compile -q
```

Expected: build exits 0.

- [x] **Step 2: Run targeted backend tests**

Run:

```bash
JAVA_HOME=/home/wushengzhou/.local/opt/java/jdk-21.0.9 mvn -q -pl data-talk-adapter -am test -Dtest=ReadSchemaActionIT,ReadSchemaActionTest,AgentPromptContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: all specified tests pass.

- [x] **Step 3: Mark plan checklist complete**

Update this plan so every task checkbox is checked and note any deviation.

- [x] **Step 4: Move plan index entry to completed**

Move `Schema Read Bounds` from Active to Completed in `docs/exec-plans/index.md`.

## Status Notes

- RED verified: the new tests failed before implementation on default column expansion, ignored `tables`, and missing prompt guidance.
- GREEN verified before housekeeping: targeted `ReadSchemaActionIT` and `AgentPromptContractTest` passed after the implementation change.
- Execution deviation: implemented locally in the isolated worktree because no explicit subagent delegation was requested in this session.
