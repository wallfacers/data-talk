---
id: BUG-0075
title: Report Viewer iframe 在打开时重复加载 2-3 次
status: fixed
priority: P2
source: manual-report
modules: [report, stage]
discovered: 2026-05-20
discoveredBy: agent
testRunId: null
fixCommit: pending
fixPlanRef: openspec/changes/fix-report-viewer-issues/
duplicateOf: null
regression: false
---

## Summary
打开报告详情 tab 时，iframe 会重复加载报告 HTML 2-3 次。根因是 `Date.now()` 被直接内联到 iframe src 中，`useReport` 和 `useSystemStatus` 的数据到达均触发组件 re-render，导致 URL 中 `_t` 参数变化，iframe 重新加载。

## Reproduction Steps
1. 打开 DevTools Network 面板
2. 从报告库点击任意报告
3. 观察 Network 面板中 `/api/reports/{id}/download/html` 请求次数

## Expected vs Actual
- **Expected**: 打开报告时 iframe 仅请求 1 次 HTML
- **Actual**: 同一个 HTML 端点被请求 2-3 次，`_t` 参数每次不同

## Environment
- OS / Browser: Linux / Webkit (Tauri)

## Root Cause
`report-viewer-tab.tsx:100` — `Date.now()` 在 JSX 中直接调用，每次 render 产生新值。组件挂载后 `useReport` 和 `useSystemStatus` 的数据返回触发的 re-render 均改变 iframe src URL，导致多余重载。

## Fix
用 `useMemo(() => ..., [reportId])` 将 `_t` 参数绑定到 `reportId`，确保同一 reportId 在组件生命周期内仅计算一次时间戳。

## Verification
单测 `report-viewer-tab.test.tsx` 验证 useMemo 稳定性：rerender 后 iframe src 不变。全量 vitest 无回归。

## Notes
`useMemo([reportId])` 确保同一 tab 实例只生成一次时间戳。切换 tab 会 remount → 新 Date.now() → 干净加载一次。
