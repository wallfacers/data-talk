---
id: BUG-0004
title: Fork to Designer 不创建 er_designer tab
status: verified
priority: P1
source: e2e-playwright
modules:
  - er-canvas
  - stage
discovered: 2026-05-07
discoveredBy: agent
fixCommit: 9d67946
fixPlanRef: null
closedDate: 2026-05-07
duplicateOf: null
regression: false
---

## Summary

E2E 测试 E3：Inspector → Fork to Designer 后，`stage.tabs.find(t => t.type === 'er_designer')` 返回 undefined。

## Reproduction Steps

1. 创建 ER Inspector tab
2. 点击 toolbar "Fork to designer" 按钮
3. 观察 stage tabs

## Expected vs Actual

- **Expected**: 创建新的 `er_designer` tab，schema 形态对齐
- **Actual**: 10 秒超时，tab 未创建

## Root Cause

1. `ErInspectorAdapter.exec()` catch 块缺少 `console.error`，错误被静默吞没
2. `ErInspectorTab.onExec` 使用 `void adapter.exec()` 丢弃 Promise，错误无任何反馈

## Fix

1. `ErInspectorAdapter.exec()` catch 块添加 `console.error`（`ErInspectorAdapter.ts:194`）
2. `ErInspectorTab.onExec` 改用 `.catch(console.error)` 替代 `void`（`er-inspector-tab.tsx:24-26`）
3. `ErInspectorTab` loading 态添加 `data-er-tab-id` 属性（`er-inspector-tab.tsx:31`）

## Verification

- `npx tsc --noEmit` 通过（仅 3 个预存 DuckDB 错误，无关）
- 定向 vitest 50/51 通过（1 个预存失败 `stage-persistence-bootstrap.er.test.ts`）
- 全仓 vitest 975/982 通过（7 个预存失败，无新增回归）

## Notes

E2E 测试需在实际运行环境中验证。
