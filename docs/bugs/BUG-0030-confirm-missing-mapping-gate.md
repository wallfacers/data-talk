---
id: BUG-0030
title: `POST /jobs/{id}/confirm` 缺少 mapping/terminal-state 校验，错误状态下可发放 token
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
修复 BUG-0029 后，confirm 端点不再写非法 status，但也丢掉了对 job 状态的所有校验：任何 job（mapping 尚未 infer、已 cancelled、已 completed）都能获得 token。E2E `confirm without prior infer returns 409` 与 `cancel flips status; confirm-after-cancel rejected` 全部失败。

## Reproduction Steps
1. 仅做 `http_request`（不 infer），直接 `POST /jobs/{id}/confirm`。
2. 或：先 `cancel`，再 `confirm` 同一个 jobId。

## Expected vs Actual
- **Expected**: HTTP 4xx (409) with `INGESTION_JOB_NOT_CONFIRMABLE`.
- **Actual**: HTTP 200 + token, 即使没有 mapping / 已经 cancelled.

## Environment
- Backend commit: working tree (post BUG-0029 hotfix)

## Root Cause
`IngestionController.confirm()` 修 BUG-0029 时把校验整体移除，只保留 `findById`。需要补回业务逻辑：
- 没有 mapping → 没法生成 hash，confirm 没意义。
- 状态是 `cancelled` / `failed` / `completed` / `writing` → 终态不可再 confirm。

注：状态字段本身从来没主动转到 `awaiting_confirm` —— infer 完后 status 还是 `fetched`，这是另一个历史遗留（未在本 plan 范围）。

## Fix
`IngestionController.confirm()` 增加两段校验：
1. `j.mapping() == null` → 409 `INGESTION_JOB_NOT_CONFIRMABLE`，userHint 引导先 infer。
2. status ∈ {`cancelled`, `failed`, `completed`, `writing`} → 409 `INGESTION_JOB_NOT_CONFIRMABLE`，userHint 提示 job 已终态。

不再依赖 `status == awaiting_confirm`（该值实际生产代码从未写入）。

## Verification
- E2E: `ingestion-execute-mcp.spec.ts:28 confirm without prior infer returns 409` 通过。
- E2E: `ingestion-execute-mcp.spec.ts:38 cancel flips status; confirm-after-cancel rejected` 通过。
- E2E: `ingestion-execute-mcp.spec.ts:14 confirm returns tokenId/expiresAt/mappingHash` 仍通过（normal path）。

## Notes
确认语义的合理化：未来若要让 list-jobs 接口在 UI 上区分"已 confirm 等待 write" 与"未 confirm"，应该走加 enum 状态 + migration 的路径，而不是再在 controller 里硬塞非法值。
