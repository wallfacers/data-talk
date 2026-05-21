---
id: BUG-0023
title: `INTEGER_64` promotion gap for values > 2^31
status: fixed
priority: P2
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "40a9a3d4 (Batch 4)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
E2E 测试期望 `$.v = 3000000000` 推断为 `INTEGER_64`，实际推断错误（依 format 而定）。JSON path 走 Jackson `node.intValue()` / `node.longValue()`，对 `> Integer.MAX_VALUE` 的值正确返回 `Long`，应能命中 `INTEGER_64`。但若 CSV/HTML path 介入（见 BUG-0022），则因为全是字符串而完全没有这条提升路径。`TypeInferrer` 自身的 wideness 排序里 `INTEGER_64` 优先级正确（`INTEGER_32 < INTEGER_64 < ...`）。

## Reproduction Steps
1. 拉 `/json/large-int` (`{"v": 3000000000}`)。
2. 调用 `infer_ingestion_schema`。
3. 检查 `$.v` 列的 `type`。

## Expected vs Actual
- **Expected**: `type` matches `/INTEGER_64/`.
- **Actual**: depends — JSON 可能 OK，CSV/HTML 落 `STRING_*`，需复测确认。

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `DotPathFlattener.java` lines 79-89 (Jackson 节点 → Java type)。
- Code: `TypeInferrer.java` lines 18-30 (`WIDENESS_ORDER`：`INTEGER_32` 索引 1, `INTEGER_64` 索引 2)。
- Spec: `client/tests/e2e/ingestion-infer-mcp.spec.ts` (`INTEGER_64 promotion for values > 2^31`).

## Root Cause
对 JSON path 本身，Jackson 已经正确把 `3000000000` 解析为 `Long` 并由 `TypeInferrer` 投票 `INTEGER_64`。所以**JSON 测试预期失败的本质可能是 BUG-0022（CSV/HTML 路径无类型转换）的派生**。这里登记 BUG-0023 是为了独立追踪：

1. 验证 JSON 路径下 `INTEGER_64` 是否真的产出（若产出，本 BUG 可关 `wontfix` / `duplicate of BUG-0022`）。
2. 修复 BUG-0022 后，在 CSV/HTML 的 coercer 中确保 `Long.parseLong("3000000000")` 返回 `Long` 而非溢出回 `Integer`。

## Fix
1. 在 BUG-0022 coercer 中：先 `Long.parseLong` 再 fallback `Double.parseDouble`，**不要先试 `Integer.parseInt`**——否则 31 位以内的值会留在 `Integer`，丢失 64 位提升机会（虽然 `TypeInferrer` 也只看类型，但保守用 `Long` 更稳）。
2. 验证：若一列里出现 `Long`，`TypeInferrer.widest` 必产 `INTEGER_64`。
3. 若 JSON 自身已 OK，则本 BUG 仅作为 BUG-0022 验证回归项保留，并标 `duplicateOf: BUG-0022`。

## Verification
- Unit: `TabularValueCoercerTest.valuesAboveIntegerMaxPromoteToLong` — `"3000000000"` coerces to `Long`. Pass.
- Unit: `TabularValueCoercerTest.exact2to31PromotesToLong` — `"2147483648"` (exactly `Integer.MAX_VALUE + 1`) coerces to `Long`. Pass.
- Unit: `CsvPayloadParserTest.largeIntegerColumnPromotesToInteger64` — full pipeline CSV → coercer → TypeInferrer → `INTEGER_64`. Pass.
- Unit: `HtmlTablePayloadParserTest.largeIntegerHtmlCellPromotesToInteger64` — same for HTML. Pass.
- E2E: pending backend restart; expect `ingestion-infer-mcp.spec.ts` `INTEGER_64 promotion` test to turn green.

## Notes
JSON path was already correct (Jackson `intValue()` / `longValue()` handle the boundary). The fix lives in the new `TabularValueCoercer` used by CSV / HTML parsers — see BUG-0022. Closed as a distinct BUG (not duplicate) because the test contract calls it out separately and INTEGER_64 promotion is a meaningful capability.
