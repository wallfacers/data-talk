---
id: BUG-0011
title: sql-result-display 测试缺少 DialogClose mock 导致 10 个用例失败
status: fixed
priority: P2
source: e2e-mcp
modules: [stage]
discovered: 2026-05-10
discoveredBy: agent
testRunId: null
fixCommit: 49f2e85
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`sql-result-display.test.tsx` 的 `vi.mock('@/components/ui/dialog', ...)` 缺少 `DialogClose` 导出。expand 全屏功能引入 `DialogClose` 后，测试未同步更新 mock，导致所有渲染 `SqlResultTable` 的用例抛出 `[vitest] No "DialogClose" export is defined on the "@/components/ui/dialog" mock` 错误。

## Reproduction Steps

1. `cd client && npx vitest run src/features/stage/components/sql-result-display.test.tsx`
2. 10 个 `result_set` 相关用例全部失败

## Expected vs Actual

- **Expected**: 13 个测试全部通过
- **Actual**: 10 个 result_set 相关测试失败，错误为 `DialogClose` mock 缺失

## Environment

- Backend commit: N/A
- Frontend commit: d59b1c6
- OS / Browser: WSL2 / Node.js
- Data source: N/A

## Evidence

- 控制台错误片段：
  ```
  [vitest] No "DialogClose" export is defined on the "@/components/ui/dialog" mock.
   ❯ SqlResultTable src/features/stage/components/sql-result-table.tsx:566:14
  ```

## Root Cause

commit `76b7838` (feat(sql-result): add fullscreen expand button) 在 `SqlResultTable` 中使用了 `DialogClose` 组件，但对应的测试文件 mock 未添加该导出。

## Fix

在 `sql-result-display.test.tsx` 的 dialog mock 中添加 `DialogClose` mock 实现。

## Verification

`npx vitest run src/features/stage/components/sql-result-display.test.tsx` → 13 passed, 0 failed

## Notes

纯测试 mock 缺失，不影响产品功能。
