---
id: BUG-0026
title: `http_request` with `payloadFormat=html` throws `UnsupportedOperationException` at fetch time
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
HTML 抓取路径调用 `IngestionPayloadFetcher` 时，fetcher 在 fetch 循环和 `createAccumulator()` switch 中都抛 `UnsupportedOperationException("HTML payload parsing is not yet implemented (P3)")`，导致 `http_request` 返回 `INGESTION_FORMAT_UNSUPPORTED`。但 `HtmlTablePayloadParser` 已实现 — 失败在 fetch 层把 HTML 当作未实现处理。

## Reproduction Steps
1. 调 `http_request` with `payloadFormat: 'html'`，URL 返回简单 HTML table。
2. 检查响应 `errorCode`。

## Expected vs Actual
- **Expected**: 抓取成功，落盘 `payload.html`，后续 `infer_ingestion_schema` 由 `HtmlTablePayloadParser` 解析为 mapping。
- **Actual**: `errorCode === 'INGESTION_FORMAT_UNSUPPORTED'`。

## Environment
- Backend commit: 95e45c3f (working tree)

## Evidence
- Code: `IngestionPayloadFetcher.java` 第 153 行 fetch 循环 + 第 347 行 `createAccumulator` switch，均显式抛出。
- Spec: `client/tests/e2e/ingestion-html-table.spec.ts` 期望成功路径。

## Root Cause
HTML 解析逻辑只在 `infer` 阶段实现（`HtmlTablePayloadParser`），fetch 阶段一直留着 placeholder 占位。两阶段职责未对齐。

## Fix
- 新增 `HtmlAccumulator`：fetch 阶段把每页响应原样拼接为字符串，落盘 `payload.html`，行数返回 `0/1`（用作分页终止信号，真正解析延迟到 infer）。
- 删除 fetch 循环里的 HTML 显式抛错。

## Verification
- Unit: `IngestionPayloadFetcherTest` HTML 路径不再抛异常。
- E2E: pending backend restart；`ingestion-html-table.spec.ts` 应走通 fetch → infer → confirm 链路。

## Notes
HTML 不在 fetch 阶段解析，是因为 HTML 没有"行"概念，多页 HTML 拼接由后续 parser 处理。分页 HTML 极为罕见，但保留通用机制。
