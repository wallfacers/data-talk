---
id: BUG-0028
title: CSV / HTML parsers emit `sourcePath = <header>` instead of `$.<header>`
status: fixed
priority: P2
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
`CsvPayloadParser` / `HtmlTablePayloadParser` 推断 mapping 时把 `sourcePath` 设成裸 header（`name`、`age`），而 JSON / JSONL parser 用 JSONPath 形式（`$.name`、`$.age`）。下游消费方判断 sourcePath 时按 `$.` 前缀辨识，CSV / HTML 列被当作非法路径丢弃。

## Reproduction Steps
1. 调 `infer_ingestion_schema` over CSV/HTML payload。
2. 检查 `columns[].sourcePath`。

## Expected vs Actual
- **Expected**: `sourcePath === "$.name"`（与 JSON 路径一致）。
- **Actual**: `sourcePath === "name"`。

## Environment
- Backend commit: 95e45c3f (working tree)

## Evidence
- Code: `CsvPayloadParser.java` / `HtmlTablePayloadParser.java` `new MappingColumn(header, header, ...)`。
- Spec: `CsvPayloadParserTest` / `HtmlTablePayloadParserTest` 断言 `cols.get("$.id")` 等。

## Root Cause
表格类 parser 与 JSON parser 各自演化，没有共享 sourcePath 规范。

## Fix
`new MappingColumn("$." + header, header, ...)` — 表格 parser 也吐 `$.<header>` 形态，与 JSON parser 对齐。

## Verification
- Unit: `CsvPayloadParserTest`、`HtmlTablePayloadParserTest` 通过。
- E2E: pending backend restart。

## Notes
JSON 路径里的 `$.` 是 JSONPath 根节点 + 字段访问；表格类没有真正 JSONPath 语义，但保留前缀让下游 / DDL 生成代码不分支处理。
