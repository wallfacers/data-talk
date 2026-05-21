# Large Schema Context Guards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent large database schemas and broad query results from exceeding OpenCode context or backend memory budgets without adding schema cache/index latency.

**Architecture:** Keep realtime JDBC metadata reads, but make `datatalk_read_schema` paginated and searchable by default, make describe calls explicit and bounded, add an MCP output budget fallback, and make chat-path SQL execution honor `pageSize`. Prompt rules steer AI behavior, while backend guards enforce the contract.

**Tech Stack:** Spring Boot 3.5, Java 21, JDBC `DatabaseMetaData`, MCP tool result bridge, JUnit 5, AssertJ, Maven.

---

## Files

- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/DataTalkMcpService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/DataTalkMcpServiceTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-29-large-schema-context-guards-plan.md`

## Task 1: Register Design And Plan

- [x] Add `docs/product-specs/2026-04-29-large-schema-context-guards-design.md`.
- [x] Register the design under `docs/product-specs/index.md` section 8.
- [x] Add this execution plan under `docs/exec-plans/`.
- [x] Register this plan in `docs/exec-plans/index.md` Active section.

## Task 2: RED Tests

- [x] Add `ReadSchemaActionIT` tests for default discovery limit, cursor pagination, pattern filtering, `searchColumns`, too many describe tables, and wide-table column truncation.
- [x] Add `ExecuteSqlActionTest` coverage proving `pageSize=2` returns two rows and `truncated=true`.
- [x] Add `DataTalkMcpServiceTest` coverage proving oversized output is replaced by a compact `truncated=true` payload.
- [x] Add `AgentPromptContractTest` assertions for pattern/limit/truncated large-schema workflow.
- [x] Run targeted tests and confirm the new assertions fail before production code changes.

## Task 3: Implement Realtime Bounded Schema Reads

- [x] Extend `ReadSchemaAction.inputSchema()` with `mode`, `pattern`, `limit`, `cursor`, and `searchColumns`.
- [x] Implement discovery mode with default `limit=50`, max `limit=100`, integer cursor offsets, case-insensitive pattern filtering, `returnedCount`, `totalCount`, `truncated`, and `nextCursor`.
- [x] Implement optional realtime column-name search only when `searchColumns=true` and `pattern` is non-blank.
- [x] Implement describe mode with explicit `tables`, max 20 requested tables, per-table column cap, and `columnsTruncated`.

## Task 4: Implement Output And SQL Result Guards

- [x] Add a serialized-output budget to `DataTalkMcpService` and return a compact truncation payload when exceeded.
- [x] Make `ExecuteSqlAction` honor `pageSize`, default to 100, cap at 1000, read at most `pageSize + 1`, and return `truncated`.

## Task 5: Prompt And Verification

- [x] Update `AGENTS.md` with the large-schema pattern/limit workflow and SQL page-size guidance.
- [x] Run targeted backend tests for schema, MCP bridge, SQL execution, and prompt contracts.
- [x] Run `cd server && mvn compile -q`.
- [x] Mark every checkbox complete, move this plan to Completed, and note deviations.

## Status Notes

- RED verified: the new MCP output budget test failed at compile time before `DataTalkMcpService` exposed a test budget constructor. The schema, SQL, and prompt tests also targeted behavior not present before this plan.
- GREEN verified: targeted backend tests passed for `ReadSchemaActionIT`, `ExecuteSqlActionTest`, `AgentPromptContractTest`, and `DataTalkMcpServiceTest`.
- Compile verified: `cd server && mvn compile -q` exited 0.
- Deviation: implemented inline in this session because the current agent instructions only allow subagent spawning when the user explicitly asks for parallel agents.
