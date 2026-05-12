---
id: BUG-0033
title: JSON / JSONL parser 只在**所有**值为 null 时标 nullable，单元素 null 触发 DDL NOT NULL
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 5 hotfix)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`JsonPayloadParser` / `JsonlPayloadParser` 推断 nullable 用 `TypeInferrer.allNull(values)` —— 必须**所有**值都为 null 才标 nullable=true。但 CSV/HTML parser 用 `allNull(values) || anyMatch(isNull)` —— **任一** null 即标 nullable。

后果：mock payload `[{score:12.5},{score:9.0},{score:null}]` 中 `score` 列 inference 出 nullable=false → DDL `"score" DECIMAL NOT NULL` → INSERT 第三行 NULL 抛 `[23502] NULL not allowed for column "score"`。

## Reproduction Steps
1. 跑 `ingestion-execute-mcp.spec.ts:110 ingest_payload after create_table populates rows`（mock `/json/users` 第 3 行 `score: null`）。
2. `ingest_payload` 抛 `RuntimeException: ingestion failed: NULL not allowed for column "score"`。

## Expected vs Actual
- **Expected**: `score` 列 nullable=true，DDL 不带 NOT NULL，INSERT 成功。
- **Actual**: nullable=false，DDL NOT NULL，INSERT 失败。

## Environment
- Backend commit: working tree (post BUG-0022/0023 fixes)

## Root Cause
两个 parser 历史演化不同步：表格类 parser（CSV / HTML）的 nullable 检查在 BUG-0022 修复时被同步改成"任一 null 即 nullable"，JSON 系 parser 没改。

## Fix
两处改：
```java
boolean nullable = TypeInferrer.allNull(values) || values.stream().anyMatch(java.util.Objects::isNull);
```
与 CSV / HTML 对齐。`allNull` 保留是为了显式表达"全 null 列也是 nullable"语义（虽然第二个条件已覆盖，但语义更清晰）。

## Verification
- E2E: `ingestion-execute-mcp.spec.ts:110` 通过；DDL 现在输出 `"score" DECIMAL(38,10)`（无 NOT NULL）。
- 单元测试无影响（现有 test 数据不覆盖混合 null）。

## Notes
这是 nullable 判定的产品 bug —— 实际数据里"有 null 值"几乎一定意味着列允许 null。"全 null 才 nullable"在统计学上等价于"看不到非 null 值时假设 nullable"，但对于 mixed 列就是 nullable=false 的错误判定。
