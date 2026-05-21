---
id: BUG-0076
title: Ctrl+R 刷新后报告详情 tab 显示空白页
status: fixed
priority: P1
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
当报告查看器 tab 处于打开状态时按 Ctrl+R 刷新页面，刷新后报告 tab 显示空白（无任何内容）。根因是双环死锁：`stage-tab-content.tsx` 的 `report_viewer` 分支对 `payload.reportId` 做门控——若为空直接 `return null`。刷新后 `__hydrateAll` → `toStageTab` 将 payload 重置为 `{}`，`reportId` 为 `undefined` → 门控阻断组件挂载 → `ReportViewerTab` 内部的 `ensureHydrated` 永远不会被调用 → payload 永远无法从后端恢复。

## Reproduction Steps
1. 从报告库打开任意报告
2. 确认报告内容正常显示
3. 按 Ctrl+R 刷新页面
4. 观察：stage 面板打开，但报告 tab 内容为空白

## Expected vs Actual
- **Expected**: 刷新后报告内容在数秒内恢复显示
- **Actual**: 报告 tab 区域为空白，且永远不会恢复

## Environment
- OS / Browser: Linux / Tauri (Webkit)

## Root Cause
`stage-tab-content.tsx:172-173` 的 `if (!payload.reportId) return null` 门控与 `ReportViewerTab` 缺失 `ensureHydrated` 调用形成双环死锁。其他持久化 tab（dashboard、sql-workbench、artifact-preview 等）均无此问题——它们 wrapper 不做数据门控，组件内部自行调用 `ensureHydrated` 并显示 loading。

## Fix
1. 删除 `stage-tab-content.tsx` 的 `if (!payload.reportId) return null` 门控，改为直接传 `tab` 对象
2. `ReportViewerTab` 签名改为接收 `tab: StageTab`，内部在 payload 缺失时调用 `coordinator.ensureHydrated(tab.tabId)` 并渲染 `<TabContentLoader />`
3. hydration 失败时 `catch` 降级：显示「报告不存在或加载失败」fallback，与 dashboard-tab 一致

## Verification
单测 `report-viewer-tab.test.tsx` 覆盖三个路径：payload 正常→iframe、空 payload→ensureHydrated+loader、hydration 失败→not-found fallback。全量 vitest 无回归。

## Notes
修复后 `ReportViewerTab` 完全遵循 dashboard-tab 的 hydration 模式：loading state + catch 降级 + not-found fallback。
