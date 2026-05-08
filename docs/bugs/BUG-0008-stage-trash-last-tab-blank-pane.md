---
id: BUG-0008
title: Stage 永久删除最后一个 tab 后右侧工作区空白（应显示新建工作位菜单）
status: fixed
priority: P1
source: e2e-playwright
modules: [stage]
discovered: 2026-05-08
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

在 Stage 左导航栏剩 1 个 tab 时，从行 "..." 菜单点 "永久删除" 并在 AlertDialog 上点 "Delete" 确认后，右侧工作区出现空白：既不显示原 tab 内容（确实已删），也不显示应当回退到的 `StageWorkbenchEmptyState`（"新建工作位" 菜单 + SQL/ER/报表/Dashboard 四宫格）。

## Reproduction Steps

1. 打开 DataTalk 客户端，确保 Stage 已展开（"Open Workbench" 处于打开态）。
2. 在 Empty Workbench 上点 "SQL Editor"，使左侧 Workbench 列表只有 1 个 query_editor tab。
3. 在该 tab 行 hover，点末尾的 "..." 按钮 → 弹出 dropdown 菜单。
4. 在 dropdown 上点 "永久删除" → 弹出 AlertDialog "Delete this tab permanently?"。
5. 在 AlertDialog 上点 "Delete" 按钮确认。

## Expected vs Actual

- **Expected**: tab 被删除后，左侧 Workbench 显示 "Active 0 / No tabs yet…" 占位；右侧工作区显示 `StageWorkbenchEmptyState` 的 "新建工作位" 菜单（+ 按钮 + SQL/ER/报表/Dashboard 四宫格）。
- **Actual**: 左侧 Workbench 正确显示 "Active 0"，但**右侧工作区完全空白**（DOM 仅剩 `<div class="flex min-h-0 flex-1 overflow-hidden"></div>` 空壳）。`localStorage.stage.workset.order` 与 `stage.workset.active` 也未清理，仍指向已删 tabId。

## Environment

- Frontend commit: 69d01b33（develop）
- Backend commit: 同上
- OS / Browser: WSL2 Ubuntu / Chromium via playwright-cli
- Data source: N/A（与数据源无关，仅 Stage UI 状态机问题）

## Evidence

- 复现日志（playwright-cli store subscriber，按时间戳排序）：
  ```
  t=43771.1ms  detachFromWorkset →  tabsLen=1, activeTabId=null,    order=[],          openTabIds=[]
  t=43771.4ms  ?(stale restore)  →  tabsLen=1, activeTabId=ca44472a, order=[ca44472a],  openTabIds=[ca44472a]
  t=43897.1ms  tabs.filter       →  tabsLen=0, activeTabId=ca44472a, order=[ca44472a],  openTabIds=[ca44472a]
  ```
- 异常 focusTab stack（playwright `console.warn`）：
  ```
  FOCUS_TAB_CALLED ca44472a
    at handleClick (stage-left-rail.tsx:67)
    at onClick     (stage-left-rail.tsx:209)  // <li>'s onClick from <StageRailRow>
    at executeDispatch (react-dom_client.js)
    at processDispatchQueue ...
  ```
- 渲染后 DOM 的 `[data-testid="stage-workspace-pane"]` innerHTML：
  ```html
  <div class="flex min-h-0 flex-1 overflow-hidden"></div>
  ```
  即 `StageTabContent` 的 wrapper 渲染了，但 `StageTabContent` 因 `tabs.find(activeTabId)` 返回 null 而返回 null。

## Root Cause

Radix `AlertDialog` 渲染在 portal 里（DOM 上挂在 `<body>`），但其 React 父节点是 `StageRailRowMenu`，`StageRailRowMenu` 又作为 `trailingMenu` prop 渲染在 `StageRailRow` 的 `<li>` 内部。React 合成事件按 **React 组件树** 而非 **DOM 树** 冒泡（自 React 17 root delegation 起的既定行为），因此点击 AlertDialog 里的 "Delete" 按钮，click 合成事件会沿 React 树冒泡到 `<li>`，触发 `StageRailRow.onClick = () => handleClick(tab)` → `focusTab(tab.tabId)`。

时序：

1. 用户点 "Delete" → `trashTab(tabId)` 被调用，同步部分 `detachFromWorkset(tabId)` 立即把 `openTabIds / openTabIdsOrdered / activeTabId` 清空。
2. 同一次 click event 沿 React 树冒泡到 `<li>` → `focusTab(tabId)` 把刚清空的状态 **整个恢复**（活动 id、workset、order 都重新指向被删 tab）。
3. `await coordinator.delete(tabId)` 成功（不进 catch 回滚分支），最后 `set(s => ({ tabs: s.tabs.filter(...) }))` 仅过滤 `tabs`，留下 `tabs=[]` 但 `activeTabId=<deleted-id>`、`openTabIdsOrdered=[<deleted-id>]` 的悬挂状态。
4. `StageWindow` 的渲染分支 `activeTabId && !showStartPage ? <StageTabContent /> : <Empty />` 因 `activeTabId` 被错误恢复而走 `StageTabContent` 分支；`StageTabContent` 又因 `tabs.find` 找不到而返回 `null`，最终右侧只剩外层 wrapper 空壳，看上去就是 "空白"。

副作用：`useStageStore.subscribe` 里的 `persistWorksetSnapshot` 在第 (2) 步把已删 tabId 写进 localStorage，下次刷新仍带着这条 stale 记录。

## Fix

两层修复：

1. **根因修复**：在 `client/src/features/stage/components/left-rail/stage-rail-row-menu.tsx` 的 `<AlertDialogContent>` 上加 `onClick={(e) => e.stopPropagation()}`，阻断合成事件沿 React 树冒泡到行 `<li>`。dropdown trigger 已有等价 `e.stopPropagation()`，本次给 dialog 补齐对称防护。
2. **防御纵深**：在 `client/src/features/stage/components/stage-window.tsx` 把渲染条件 `activeTabId && !showStartPage` 改为 `activeTab && !showStartPage`（`activeTab = openTabsOrdered.find(t => t.tabId === activeTabId)` 已存在）。这样即便未来再出现任何把 `activeTabId` 留成悬挂值的代码路径，UI 也会回退到 `StageWorkbenchEmptyState` 而不是空白。

回归测试：
- `client/src/features/stage/components/left-rail/stage-rail-row-menu.test.tsx`（新增）：在 `StageRailRow` 的 `onClick` 上挂 spy + 调 `focusTab`，confirm 删除后断言 `activeTabId === null`、`openTabIdsOrdered === []`、`onClick` 不被冒泡触发。
- 既有 `stage-window.test.tsx` 全部 26 测试在改用 `activeTab` 后仍通过（覆盖 "delete the last tab → empty workbench" 路径）。

## Verification

- 单测：`npx vitest run src/features/stage/ src/stores/` → 78 文件 / 582 测试全绿。
- 浏览器 E2E（playwright-cli 连 dev server `localhost:1420`）：reload → 点 "SQL Editor" 创建 tab → 点 "..." → "永久删除" → "Delete"。结果：
  - `[data-testid="stage-empty-workbench"]` 出现 ✓
  - `localStorage.stage.workset.order = null`、`stage.workset.active = null`（清理干净）✓
  - 控制台无报错（仅 1 条 404 是已删 tab payload 后端预期返回，与本 BUG 无关）✓

## Notes

- 这是一个典型的 "Radix Portal + React 合成事件冒泡" 陷阱。本仓库其他用 `<AlertDialog>` 的地方若也嵌在带 onClick 的容器里，需同样审计是否需要 `stopPropagation` 屏障。当前已知 `stage-left-rail.tsx` 的 unarchive AlertDialog 同样嵌在 row 里但其 row 外层不会触发 focusTab 副作用，暂不强制改。
- 若将来 trailingMenu 容器改成更强的隔离边界（例如把 `StageRailRowMenu` 提到 `<li>` 外层、或行 `<li>` onClick 改成 `if (e.target === e.currentTarget) ...`），可移除本次的 stopPropagation。当前修复保守且最小改动。
