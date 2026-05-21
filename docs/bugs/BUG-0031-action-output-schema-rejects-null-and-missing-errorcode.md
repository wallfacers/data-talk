---
id: BUG-0031
title: `create_ingestion_table` / `ingest_payload` action 输出 schema 拒 null + 缺顶层 `errorCode`，吞掉根因
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 5 hotfix)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: BUG-0013
regression: false
---

## Summary
两个 action handler 在 success path `out.put("error", null); out.put("userHint", null);`，但输出 schema 声明 `error: object` / `userHint: string`（type 不允许 null），导致 MCP `output schema validation` 失败，返回 `-32602 invalid`。E2E 看不到 `result.rowsInserted`（被 wrap 成 error）。

此外，错误路径 errorNode 没有顶层 `errorCode` 字段（HttpRequestActionHandler 已加），E2E 用 `result.errorCode ?? error.message` 断言时只能 fallback 到 reason 字符串，类型耦合脆弱。

## Reproduction Steps
1. 跑 `ingestion-execute-mcp.spec.ts:51 create_ingestion_table with valid token works` 或 `:110 ingest_payload after create_table populates rows`。
2. `expect(create.error).toBeUndefined()` 失败：`error.message` 含 `output invalid: [$.error: null found, object expected, $.userHint: null found, string expected]`。
3. 或 `ingestion-ddl-mcp.spec.ts:44 Unsupported dialect`：断言 `errorCode` 含 `DIALECT_UNSUPPORTED` 失败（errorCode 不存在）。

## Expected vs Actual
- **Expected**: success path 不写 null，错误路径有顶层 `errorCode`。
- **Actual**: success path 写 null 触发 schema validation；错误路径只在嵌套 `error.code` 内有 code。

## Root Cause
这是 BUG-0013 同类问题的扩散 —— `HttpRequestActionHandler` 已修，但 `CreateIngestionTableActionHandler` / `IngestPayloadActionHandler` 漏了。

## Fix
两个 handler 同时改：
1. **success path 删 null 字段**：`error` / `userHint` 在 success 不写（缺失而非 null）。
2. **errorNode 加顶层 `errorCode`**：与 `HttpRequestActionHandler` 对齐。
3. **catch 加 log.error**：之前 catch 完全静默吞 stack trace，运维盲；改为 `log.error("ingest_payload failed", e)`。

## Verification
- E2E: `ingestion-execute-mcp.spec.ts:51` 通过。
- E2E: `ingestion-execute-mcp.spec.ts:110` 通过（rowsInserted=3）。
- E2E: `ingestion-ddl-mcp.spec.ts:44 Unsupported dialect returns INGESTION_DIALECT_UNSUPPORTED` 通过。

## Notes
本质和 BUG-0013 一致，但 plan 阶段只 grep `http_request` action handler 修复，没覆盖兄弟 handlers。建议把 errorNode + success-path null-skip 抽到公共 base class 或者 utility 避免再扩散。
