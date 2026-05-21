---
id: BUG-0025
title: `infer_ingestion_schema` action emits lowercase `type` (`string_256`) instead of canonical enum name
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 4 follow-up)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`InferIngestionSchemaActionHandler` 用 `col.type().dbValue()` 序列化推断结果，输出小写 `string_256`、`integer_32` 等。MCP 调用端与 E2E spec 期望大写 `STRING_256` 等 enum 常量名，断言失败。

## Reproduction Steps
1. 触发 `http_request` 抓取 CSV，再调 `infer_ingestion_schema`。
2. 取响应 `columns[].type`，与 `STRING_256` / `INTEGER_32` 比较。

## Expected vs Actual
- **Expected**: `columns[].type === "STRING_256"`
- **Actual**: `columns[].type === "string_256"`

## Environment
- Backend commit: 95e45c3f (working tree)

## Evidence
- Code: `InferIngestionSchemaActionHandler.java` line 113 (pre-fix) `m.put("type", col.type().dbValue())`.
- Spec: `client/tests/e2e/ingestion-infer-mcp.spec.ts` 断言 `expect(col.type).toBe('STRING_256')`.

## Root Cause
`dbValue()` 是 JSON / DB 持久化用的小写表示，便于 SQL Schema 序列化；MCP 输出按设计应使用规范的 enum 大写常量。两者职责混淆。

## Fix
改成 `col.type().name()` 输出大写 enum 常量名；持久化路径不变（仍走 `dbValue()`）。

## Verification
- Unit: `InferIngestionSchemaActionHandlerTest.happyPathReturnsMapping` 断言 `type == "STRING_256"`。
- E2E: pending backend restart.

## Notes
保留 `dbValue()` 给 DB / JSON persistence；MCP 输出与 DB 序列化解耦，避免下游断言依赖小写。
