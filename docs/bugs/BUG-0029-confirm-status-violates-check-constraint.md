---
id: BUG-0029
title: `POST /api/ingestion/jobs/{id}/confirm` 返回 HTTP 500 — `status='confirmed'` 违反 CHECK 约束
status: fixed
priority: P0
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
`IngestionController.confirm()` 调用 `jobRepo.updateStatus(id, "confirmed", ...)`，但 `ingestion_job.status` CHECK 约束只允许 `pending | fetching | fetched | mapping | awaiting_confirm | writing | completed | failed | cancelled`，**没有 `confirmed`**。SQLite 抛 `SQLITE_CONSTRAINT_CHECK`，Spring 反射成 `UncategorizedSQLException` → HTTP 500。下游所有 confirm → createTable → ingest 链路全部断在第一步。

## Reproduction Steps
1. 完整跑 ingestion 链路到 `awaiting_confirm` 状态。
2. `POST /api/ingestion/jobs/<id>/confirm` body `{}`。

## Expected vs Actual
- **Expected**: HTTP 200，返回 `{ tokenId, expiresAt, mappingHash }`。
- **Actual**: HTTP 500，`SQLITE_CONSTRAINT_CHECK` stack trace。

## Environment
- Backend commit: 95e45c3f (working tree)
- DB schema: `V20__ingestion.sql` lines 27-29 (status CHECK enum)

## Evidence
- Stack trace (from `tmp/backend-e2e.log`):
  ```
  SQLITE_CONSTRAINT_CHECK: CHECK constraint failed: status IN
   ('pending','fetching','fetched','mapping','awaiting_confirm',
    'writing','completed','failed','cancelled')
  at JdbcIngestionJobRepository.updateStatus(JdbcIngestionJobRepository.java:135)
  at IngestionController.confirm(IngestionController.java:301)
  ```
- Source: `IngestionController.java:301` `jobRepo.updateStatus(id, "confirmed", null, ...)`.

## Root Cause
设计上 `confirm` 端点只发放 token；状态机的 `awaiting_confirm → writing` 由 `IngestionExecutor.createTable()`（接收 token 后）owning。`confirm` 端点错误地额外写了一个 status 转换，且这个新值压根不在 schema 允许的枚举里。代码与状态机设计不一致。

## Fix
删除 `confirm` 端点的 `jobRepo.updateStatus()` 调用。`confirm` 只 issue token；"已确认未写入"的语义由 `tokenStore` 中是否存在该 jobId 的 token 表示，不依赖 status 字段。状态推进规则：
- `awaiting_confirm` → confirm() [仅发 token] → 仍是 `awaiting_confirm`
- → `IngestionExecutor.createTable(token)` [消费 token] → `writing`
- → `IngestionExecutor.ingestPayload()` → `completed`

## Verification
- 手动 curl `POST /jobs/{id}/confirm` 返回 200 + token payload。
- E2E: pending；所有 `ingestion-*.spec.ts` 中 confirm → createTable → ingest 链路恢复。

## Notes
此 bug 阻断 27/40 E2E 之外的 9 个失败用例全部。是当前阻塞 E2E 通过率的最高优先级问题。
副作用：observability — list jobs 接口 status 列在 confirm 后仍显示 `awaiting_confirm`，前端 UI 区分 "未 confirm" 与 "已 confirm 待写入" 需依赖 token store 查询，而不能仅看 status。若未来需要 status 区分，应该走加 enum + migration V23 的方式，而不是在控制器里硬塞非法值。
