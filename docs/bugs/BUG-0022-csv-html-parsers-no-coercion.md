---
id: BUG-0022
title: CSV / HTML parsers emit raw strings — no numeric / boolean coercion
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "pending — Batch 4 of ingestion-e2e-bugs-fix-plan (code in working tree)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`CsvPayloadParser` (line 29) 与 `HtmlTablePayloadParser` (line 54) 把每个单元格作为 `String` 直接放入列向量。`TypeInferrer` 看到全是字符串就一律推断 `STRING_*`，CSV/HTML 的数值/布尔列丢失类型信息。JSON 因为 `DotPathFlattener.rawValue()` 走 Jackson `intValue/longValue/booleanValue` 不受影响。

## Reproduction Steps
1. 调用 `http_request` 拉 `/csv/orders` (含 `id,name,total` 三列，`total` 是浮点数)。
2. 调用 `infer_ingestion_schema`。
3. 观察 `total` 列被推断为 `STRING_*` 而非 `DECIMAL` / `INTEGER`。

## Expected vs Actual
- **Expected**: numeric columns inferred as `INTEGER_32/64` or `DECIMAL`; boolean values as `BOOLEAN`.
- **Actual**: all CSV/HTML columns inferred as `STRING_*`.

## Environment
- Backend commit: c3730a10
- Frontend commit: c3730a10

## Evidence
- Code: `CsvPayloadParser.java` line 29-30 — `String val = c < row.length ? row[c] : null; columns.get(c).add(val);`
- Code: `HtmlTablePayloadParser.java` line 54 — same shape.
- Reference (works): `JsonPayloadParser.java` + `DotPathFlattener.rawValue()` lines 79-89.

## Root Cause
CSV/HTML parsers never attempt numeric/boolean coercion — they push every cell as `String`. The downstream `TypeInferrer` only votes by the runtime Java type of each value, so the widest vote is always `STRING_*`. Numeric inference for tabular sources is therefore impossible by construction.

## Fix
Introduce a small `coerceTabularCell(String raw)` helper (or inline) in both parsers:
1. `null` and empty → `null`.
2. Try `Long.parseLong` → `Long`.
3. Try `Double.parseDouble` → `Double`.
4. `"true" / "false"` (case-insensitive) → `Boolean`.
5. Fallback → original `String`.

Apply at the value-append site in both parsers. This keeps existing semantics for genuinely-string columns (e.g. `"abc"` stays `String`) while allowing `TypeInferrer` to vote correctly.

## Verification
- Unit: `TabularValueCoercerTest` — 12 cases covering null/empty, boolean, integer/long boundary, decimal, locale-formatted strings, alphanumerics, leading-zero. Pass.
- Unit: `CsvPayloadParserTest` — 5 cases: `id` INTEGER_32, `total` DECIMAL, `active` BOOLEAN, large-int → INTEGER_64, mixed → STRING_*. Pass.
- Unit: `HtmlTablePayloadParserTest` — 3 cases: `rank` INTEGER_32, `score` DECIMAL, large-int → INTEGER_64. Pass.
- Unit: `RowStreamTest.csvStreamsWithHeaderRow` updated — streaming path now emits `Integer` for `id` / `score`, matching inference. Pass.
- E2E: pending backend restart; expect `ingestion-infer-mcp.spec.ts` CSV / HTML cases to turn green.

## Notes
Locale-sensitive numbers (`1,234`, `1.234,56`) intentionally stay as `String` — `TabularValueCoercer` only coerces unambiguous integer / decimal shapes per `Locale.ROOT`. Tested explicitly in `TabularValueCoercerTest.localeFormattedNumberStaysString`.
