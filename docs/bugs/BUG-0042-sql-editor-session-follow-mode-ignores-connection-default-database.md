---
id: BUG-0042
title: SQL editor session-follow 模式下不应用 connection 默认 database
status: open
priority: P1
source: manual-report
modules: [stage, query-editor, connection]
discovered: 2026-05-14
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: openspec/changes/query-editor-connection-default-fallback/
duplicateOf: null
regression: false
---

## Summary

新建工作台（Stage toolbar `+` → SQL Editor）打开 SQL 编辑器，editor 默认走 session-follow 模式（`useSessionContext=true`，无 `contextOverride`）。当 session 的 `connectionId` 已设但 `database` 为 null 时，UI 不会回填 Connection 配置的 `databaseName`，toolbar 的 database 选择器显示空，运行按钮被禁用。

这是 `query-editor-connection-default-fallback` change 的实施缺口：write-time fallback 已写入 `StageTab.database` 字段，但渲染层 `resolveTabDataContext` 在 session-follow 分支只读 `sessionContext.database`（line 120），忽略 tab 字段，导致 fallback 形同虚设。

## Reproduction Steps

1. Settings → 编辑某个 Connection（如 TiDB-prod），`databaseName = "analytics"`，保存
2. 在 chat 中切换到一个会话，会话已绑定该 Connection 但未指定 database（`session.dataContext.database = null`）
3. Stage toolbar `+` → SQL Editor
4. 观察 SQL 编辑器 toolbar 的 database 选择器

## Expected vs Actual

- **Expected**: database 选择器自动显示 `"analytics"`（Connection 配置的默认值），运行按钮启用
- **Actual**: database 选择器为空 / "请选择"，运行按钮 disabled，用户必须手动从下拉里选 database

## Environment

- Frontend commit: develop / (在分支 develop，本次 change apply 后)
- Backend: 无需启
- Browser: N/A（代码诊断）
- Data source: 任意（与 connection kind 无关）

## Evidence

代码诊断（无截图）：
- `client/src/stores/stage-store.ts:225` `resolveQueryEditorOpenContext` session-context 分支 ✓ 已调用 `applyConnectionDefaultDatabase`，把 fallback database 写到 `openContext.database`
- `client/src/stores/stage-store.ts:660` `openQueryEditor` 把 `openContext.database` 写到 `StageTab.database`
- 但 `buildQueryEditorPayload` (`stage-store.ts:266-274`) 在 `useSessionContext=true` 时把 `contextOverride: null`
- UI 渲染时 `client/src/features/stage/components/sql-workbench-tab.tsx:299-323` 调用 `resolveTabDataContext`，options.inheritSessionContext=true
- `client/src/features/stage/utils/resolve-tab-data-context.ts:118-138` session-follow 分支：
  ```ts
  if (useSessionContext && inheritSessionContext && sessionConnectionId != null) {
    const database = normalizeContextValue(sessionContext?.database)  // ← session.database=null 直接返回 null
    ...
  }
  ```
  这里完全忽略 `tab.database` / `payload.database` / connection store 中的 `databaseName`

## Root Cause

`query-editor-connection-default-fallback` 设计采用 **write-time fallback**（design D1: "resolver stays pure"）。但 session-follow 模式下 `resolveTabDataContext` 的实际数据源是 `sessionContext.database`（每次 render 都读），与 write-time 写入的 `StageTab.database` 是两条独立路径。

write-time fallback 在 session-follow 分支无效，因为：
1. helper 把 fallback 写到 `openContext.database`
2. `buildQueryEditorPayload` 因 `useSessionContext=true` 将 `contextOverride` 设为 null
3. resolver 看到 `useSessionContext=true` 跳进 line 118 分支直接读 `sessionContext.database`，绕过 tab 字段

修复方向（待定，由 BUG-0042 修复计划决定）：
- 方案 A：在 resolver 的 session-follow 分支加 connection-default 兜底（破坏 "resolver stays pure" 原则但最干净）
- 方案 B：在 session-follow 分支的 fallback fires 时翻转 `useSessionContext=false`，把 fallback 写入 contextOverride（破坏 "新 editor follow session" default 语义）
- 方案 C：在 `sql-workbench-tab.tsx` 计算 `effectiveContext` 时 post-process 兜底（最小侵入）

## Fix

TBD — 跟 BUG-0043 一起评估，可能需要更新 `query-editor-connection-default-fallback` change 的 design.md。

## Verification

修复后通过以下场景验证（来自 `openspec/changes/query-editor-connection-default-fallback/tasks.md` Section 8）：
- 8.2 Toolbar `+` → SQL Editor → Connection 自带 `databaseName` 时，toolbar 显示该 databaseName
- 8.4/8.5 切换 connection 时 database 自动重新回填

## Notes

- 关联 change: `openspec/changes/query-editor-connection-default-fallback/`
- 该 change 的 26/36 单元测试全绿，但浏览器走查（Section 8）暴露此 BUG —— 说明现有单测验证的是"write 路径写入 StageTab.database"，没覆盖 "resolver-time 实际显示" 的 end-to-end 行为
- 该 change 的 spec scenario `Toolbar + with session whose connectionContext has connection A (databaseName "x") and database null → contextOverride.database = "x"` 的措辞 (`contextOverride.database`) 与实际架构（session-follow 模式下 contextOverride=null）不一致，spec 需要同步修正
