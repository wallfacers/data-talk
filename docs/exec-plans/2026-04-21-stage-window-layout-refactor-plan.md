# Stage Window Layout Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Stage 从“底部 Dock + pill tabs + 单层内容区”重构为“左侧导航侧栏 + 右侧 Tabs 工作区 + Chrome-inspired 顶部页签”的结构化工作台。

**Architecture:** 前端继续复用 `StageStore` 作为 tab workspace 真相源，在其上增加 sidebar 导航态与统一的 tab identity / open-or-focus 规则；`StageWindow` 重构成 `StageSidebar + WorkspacePane` 两栏布局，左侧顶部是轻量工具行，下方是连接资源浏览器，右侧保留 `StageTabBar + StageTabContent`。资源节点点击同步 session data context，但真正打开 tab 的是资源节点下的工具动作项；底部 `StageDock` 删除，顶部 TabBar 视觉改成 Chrome-inspired，而非高拟真复刻。

**Tech Stack:** React 19、Zustand、TanStack Query、Vitest、Testing Library、shadcn/ui、Lucide、现有 `SessionDataContext` REST API

**Design Spec:** `docs/product-specs/2026-04-21-stage-window-layout-refactor-design.md`

---

## Spec Mapping

- Spec §4 总体布局 → Task 2 / Task 4
- Spec §5 左侧导航模型 → Task 2 / Task 3
- Spec §6 Tab 打开与去重规则 → Task 1 / Task 5
- Spec §7 状态模型 → Task 1 / Task 3
- Spec §8 组件拆分建议 → Task 2 / Task 4 / Task 5
- Spec §9 视觉设计 → Task 4
- Spec §10 首版细节决策 → Task 1 / Task 3 / Task 5
- Spec §11 迁移策略 → Task 2 / Task 5
- Spec §12 测试要求 → Task 1-6 全部覆盖

---

## File Map

### Create

| 文件 | 责任 |
|------|------|
| `client/src/features/stage/components/stage-sidebar.tsx` | 左侧侧栏总入口，负责收起/展开、工具行与资源浏览器布局 |
| `client/src/features/stage/components/stage-tool-row.tsx` | 顶部一行工具入口 |
| `client/src/features/stage/components/stage-resource-browser.tsx` | 连接 / database / schema 资源浏览器 |
| `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts` | 统一 tab identity、查重与 open/focus 规则 |
| `client/src/features/stage/utils/build-stage-resource-tree.ts` | 将连接列表 + session data context 组装成资源树视图模型 |
| `client/src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts` | tab identity / 去重测试 |
| `client/src/features/stage/utils/__tests__/build-stage-resource-tree.test.ts` | 资源树组装测试 |
| `client/src/features/stage/components/stage-sidebar.test.tsx` | sidebar 行为测试 |
| `client/src/features/stage/components/stage-tool-row.test.tsx` | 工具行动作测试 |
| `client/src/features/stage/components/stage-resource-browser.test.tsx` | 资源浏览器测试 |

### Modify

| 文件 | 改动 |
|------|------|
| `client/src/stores/stage-store.ts` | 新增 sidebar 导航态与对应 action；保留 tab CRUD 主干 |
| `client/src/stores/stage-store.test.ts` | 补 sidebar 状态与 action 测试 |
| `client/src/features/stage/components/stage-window.tsx` | 改成左右 workbench 布局，删除底部 Dock 渲染 |
| `client/src/features/stage/components/stage-window.test.tsx` | 更新整体布局断言 |
| `client/src/features/stage/components/stage-tab-bar.tsx` | 改为 Chrome-inspired 顶部页签结构与样式 |
| `client/src/features/stage/components/stage-tab-content.tsx` | 包进新的 `WorkspacePane` 语义与空态结构 |
| `client/src/features/stage/components/stage-dock.tsx` | 删除文件或保留为空壳后彻底移除引用 |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | `workspace.open` 走统一 open-or-focus 规则 |
| `client/src/features/session/hooks/use-session-data-context.ts` | 暴露可安全复用的 set/update 刷新行为给 sidebar |
| `client/src/features/connection/store.ts` | 若现有连接列表/活动连接选择器不够，补最小只读 selector 支撑资源树 |
| `client/src/i18n/messages.ts` | 新增 sidebar / tool row / resource tree / empty / coming soon 文案 |
| `docs/FRONTEND.md` | 若实现后形成新的 Stage 布局约定，则补充说明 |
| `docs/exec-plans/index.md` | 将本计划登记为 Active，完成后移入 Completed |

### Verify

| 命令 | 用途 |
|------|------|
| `cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage/components/stage-window.test.tsx src/features/stage/components/stage-sidebar.test.tsx src/features/stage/components/stage-tool-row.test.tsx src/features/stage/components/stage-resource-browser.test.tsx src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts src/features/stage/utils/__tests__/build-stage-resource-tree.test.ts` | Stage 相关专项验证 |
| `cd client && npx tsc --noEmit` | 前端类型检查 |

---

## Task 1: StageStore 导航态与统一开 tab 规则

**Files:**
- Create: `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts`
- Create: `client/src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts`
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`

- [x] **Step 1.1: 为 sidebar 导航态写失败测试**
  - 在 `stage-store.test.ts` 新增断言：
    - `toggleSidebarCollapsed(sessionId)` 能保存收起态
    - `setSidebarSelection(sessionId, selection)` 能保存当前选中节点
    - `toggleResourceExpanded(sessionId, nodeId)` 能展开 / 收起资源树节点
    - `clear(sessionId)` 会同时清理 sidebar 相关状态

- [x] **Step 1.2: 扩展 `StageState`**
  - 在 `stage-store.ts` 增加：
    - `sidebarCollapsedBySession: Map<string, boolean>`
    - `sidebarSelectionBySession: Map<string, SidebarSelection | null>`
    - `resourceTreeExpandedBySession: Map<string, string[]>`
    - `toggleSidebarCollapsed(sessionId)`
    - `setSidebarSelection(sessionId, selection)`
    - `toggleResourceExpanded(sessionId, nodeId)`
    - `setResourceExpanded(sessionId, nodeIds)`
  - `SidebarSelection` 至少覆盖：
    - `tool`
    - `connection`
    - `database`
    - `schema`
    - `resource_tool`

- [x] **Step 1.3: 跑 store 测试确认新增行为通过**
  - `cd client && npx vitest run src/stores/stage-store.test.ts`

- [x] **Step 1.4: 为统一 open-or-focus 规则写失败测试**
  - 在 `open-or-focus-stage-tool-tab.test.ts` 覆盖：
    - 顶部工具行 `sql` 第二次点击不重复创建 workspace tab
    - 资源树 `sql + connectionId + database + schema` 第二次点击激活已有 session tab
    - 不同 schema 打开的是不同 tab
    - 无 active session 时拒绝创建 session-scoped 资源工具 tab

- [x] **Step 1.5: 实现 `open-or-focus-stage-tool-tab.ts`**
  - 暴露最少两个能力：
    - `buildStageTabIdentity(input)`
    - `openOrFocusStageToolTab(store, input)`
  - identity 规则：
    - 顶部工具行：`toolType`
    - 资源树：`toolType + connectionId + database + schema`
  - 新建 tab 时把 `connectionId / database / schema` 快照写进 `StageTab`

- [x] **Step 1.6: 跑 StageStore + helper 专项验证**
  - `cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts`

- [x] **Step 1.7: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 2: Workbench 布局骨架与 Dock 移除

**Files:**
- Create: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify/Delete: `client/src/features/stage/components/stage-dock.tsx`

- [x] **Step 2.1: 为新布局写失败测试**
  - 在 `stage-window.test.tsx` 增加断言：
    - 有 session 时渲染 sidebar 容器
    - 不再渲染底部 Dock 工具按钮
    - 右侧仍能显示 tab bar 与内容区
    - sidebar 收起时有“展开资源栏”入口

- [x] **Step 2.2: 新建 `stage-sidebar.tsx` 骨架**
  - 先只渲染：
    - 收起 / 展开按钮占位
    - 顶部工具行占位 slot
    - 资源浏览器占位 slot
  - 暂不实现具体工具和树节点

- [x] **Step 2.3: 重构 `stage-window.tsx`**
  - 保留窗口顶栏与 close / maximize
  - 删除底部 `StageDock` 渲染
  - 内容区改成：
    - 左 `StageSidebar`
    - 右 `WorkspacePane`
  - 右侧继续包含：
    - `StageTabBar`
    - `StageTabContent`
    - 无 tab 时的 children slot

- [x] **Step 2.4: 处理 `stage-tab-content.tsx` 的新容器语义**
  - 保持 activeTab 查找逻辑不变
  - 让内容区能在新的 `WorkspacePane` 里正确撑满 / 滚动

- [x] **Step 2.5: 删除或废弃 `StageDock`**
  - 若删除文件会牵涉过多引用，则先保留文件但移除所有调用点
  - 最终目标：Stage 运行时完全不再显示底部 Dock

- [x] **Step 2.6: 跑布局专项测试**
  - `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx`

- [x] **Step 2.7: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 3: 顶部工具行与连接资源浏览器

**Files:**
- Create: `client/src/features/stage/components/stage-tool-row.tsx`
- Create: `client/src/features/stage/components/stage-tool-row.test.tsx`
- Create: `client/src/features/stage/components/stage-resource-browser.tsx`
- Create: `client/src/features/stage/components/stage-resource-browser.test.tsx`
- Create: `client/src/features/stage/utils/build-stage-resource-tree.ts`
- Create: `client/src/features/stage/utils/__tests__/build-stage-resource-tree.test.ts`
- Modify: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/session/hooks/use-session-data-context.ts`
- Modify: `client/src/services/api/session-data-context.ts`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 3.1: 为资源树视图模型写失败测试**
  - `build-stage-resource-tree.test.ts` 覆盖：
    - 连接节点按连接列表生成
    - 当前 session data context 对应节点有 selected/current 标记
    - database/schema 节点下挂工具动作项
    - 无 schema 的连接不生成空 schema 子层

- [x] **Step 3.2: 实现 `build-stage-resource-tree.ts`**
  - 输入：
    - 连接列表
    - 当前 session data context
    - expanded node ids
  - 输出：
    - 连接节点
    - database / schema 节点
    - 工具动作项节点
  - 只做首版粒度：连接 → database/schema → SQL/ER 工具

- [x] **Step 3.3: 为 `StageToolRow` 写失败测试**
  - 断言：
    - `SQL 编辑器` 点击后调用统一 open-or-focus helper
    - `ER 图设计器 / 报表 / Dashboard` 若 disabled，不创建 tab，只显示轻量提示或 disabled 态

- [x] **Step 3.4: 实现 `StageToolRow`**
  - 顶部只保留一行轻量入口
  - `SQL 编辑器` enabled
  - 其他工具按设计先 disabled / coming soon
  - 所有入口统一走 `openOrFocusStageToolTab`

- [x] **Step 3.5: 为 `StageResourceBrowser` 写失败测试**
  - 覆盖：
    - 单击连接 / database / schema 节点只更新选中态与展开态，不直接开 tab
    - 单击资源下 `SQL 编辑器` 时创建或激活已有 tab
    - 节点点击会调用 `setSessionDataContext` 同步 session data context

- [x] **Step 3.6: 实现 `StageResourceBrowser`**
  - 连接节点、database/schema 节点与工具动作项分离
  - 连接 / database / schema 节点点击行为：
    - 更新 sidebar selection
    - 更新 expanded state
    - 同步 `SessionDataContext`
  - 工具动作项点击行为：
    - 调用统一 open-or-focus helper

- [x] **Step 3.7: 将 `StageSidebar` 接上 ToolRow + ResourceBrowser**
  - 根据收起态决定渲染完整树还是窄条
  - 收起态只保留：
    - 工具 icon
    - 展开按钮
  - 展开后恢复上次 expanded / selection

- [x] **Step 3.8: 补 i18n 文案**
  - 至少新增：
    - 资源栏
    - 展开资源栏 / 收起资源栏
    - SQL 编辑器
    - ER 图设计器
    - 报表
    - Dashboard
    - 即将支持
    - 无可用资源 / 无 database/schema 等空态

- [x] **Step 3.9: 跑 sidebar + resource browser 专项测试**
  - `cd client && npx vitest run src/features/stage/components/stage-sidebar.test.tsx src/features/stage/components/stage-tool-row.test.tsx src/features/stage/components/stage-resource-browser.test.tsx src/features/stage/utils/__tests__/build-stage-resource-tree.test.ts`

- [x] **Step 3.10: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 4: Chrome-inspired TabBar 与整体视觉收口

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [x] **Step 4.1: 为 TabBar 结构变化补测试**
  - 保留现有 close / close others / close all / close left / close right 行为断言
  - 增加对新结构的最小语义断言：
    - 激活 tab 存在 `data-state=active` 或等价标识
    - close 按钮仍可点击

- [x] **Step 4.2: 重写 `StageTabBar` 视觉结构**
  - 从 pill tabs 改为连续浏览器式页签
  - 激活页签与内容区形成连续面
  - 非激活页签后退但可点击
  - icon 缩小，文本优先
  - 保留 context menu 行为，不改功能集

- [x] **Step 4.3: 调整 `StageWindow` / `StageSidebar` 视觉层次**
  - 移除“卡片里套卡片”的厚重感
  - 形成统一 workbench 壳体
  - 左栏与右侧 workspace 只做轻微明度区分
  - 保持 maximize / close 顶栏不回归

- [x] **Step 4.4: 实现 sidebar 收起态 polish**
  - 收起态不显示迷你树
  - 工具 icon 与展开按钮布局稳定
  - 展开/收起有轻量过渡

- [x] **Step 4.5: 跑视觉相关组件测试**
  - `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx`

- [x] **Step 4.6: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 5: WorkspaceAdapter 与 Stage 入口统一

**Files:**
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/stage/components/query-editor-tab.tsx`
- Modify: `client/src/features/stage/components/bang-query-tab.tsx`

- [x] **Step 5.1: 为 `WorkspaceAdapter` 写失败测试**
  - 覆盖：
    - `workspace.open(type=query_editor)` 走统一 open-or-focus 规则
    - 相同 `type + connection_id + database + schema` 不重复创建 tab
    - session-scoped 打开仍要求 active session

- [x] **Step 5.2: 重构 `WorkspaceAdapter.exec('open')`**
  - 不再自己拼随机 tabId 决定是否新建
  - 改为调用 `openOrFocusStageToolTab`
  - 对不走 helper 的历史类型，保留兼容策略但避免破坏现有 `bang_query`

- [x] **Step 5.3: 校正 Query Editor / Bang Query 与新上下文规则的兼容性**
  - 确认：
    - Query Editor 继续读取 tab 上下文快照
    - Bang Query 不受 sidebar 结构改造影响
  - 如现有组件依赖旧布局 className，补最小修正

- [x] **Step 5.4: 跑 adapter / stage 兼容专项测试**
  - `cd client && npx vitest run src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/components/query-editor-tab.test.tsx src/features/stage/components/bang-query-tab.test.tsx`

- [x] **Step 5.5: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 6: Consolidated Verification & Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-21-stage-window-layout-refactor-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/FRONTEND.md`

- [x] **Step 6.1: 跑 Stage 全量专项测试**
  - `cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage src/features/session/hooks/__tests__/use-session-data-context.test.tsx`

- [x] **Step 6.2: 跑前端类型检查**
  - `cd client && npx tsc --noEmit`

- [x] **Step 6.3: 手工冒烟**
  - Stage 打开后显示左侧导航侧栏与右侧工作区
  - 底部 Dock 不再出现
  - 工具行点击 `SQL 编辑器` 只保留一个全局工具 tab
  - 单击连接 / database / schema 节点不直接开 tab，但会更新当前上下文
  - 单击资源下 `SQL 编辑器` 会按资源上下文打开 / 激活 tab
  - Chrome-inspired 顶部页签激活态与内容区形成连续面
  - sidebar 收起 / 展开正常，展开后恢复上次选择与展开态
  - 说明：当前会话未启动 Tauri GUI，以上为待人工桌面 smoke checklist；对应代码路径已由 Stage 全量 vitest + `npx tsc --noEmit` 覆盖。

- [x] **Step 6.4: 文档回写**
  - 若最终形成新的 Stage 布局 / 导航约定，回写 `docs/FRONTEND.md`

- [x] **Step 6.5: 计划收尾**
  - 勾完本计划所有 checkbox
  - 在 `docs/exec-plans/index.md` 将本条目从 Active 移到 Completed
  - 若执行中发现 deferred work，登记到 `docs/exec-plans/tech-debt-tracker.md`

---

## Dependency Notes

- **Task 1** 必须先完成；Task 2 / 3 / 5 都依赖统一 open-or-focus 规则与 sidebar 状态模型
- **Task 2** 与 **Task 3** 可在 Task 1 完成后并行，但文件 ownership 必须分开：
  - Task 2 主要负责 `stage-window.tsx` / `stage-sidebar.tsx` 布局骨架
  - Task 3 主要负责 `stage-tool-row.tsx` / `stage-resource-browser.tsx` / `build-stage-resource-tree.ts`
- **Task 4** 依赖 Task 2 的新 workbench 布局先稳定
- **Task 5** 依赖 Task 1 的 helper API shape 冻结
- **Task 6** 只能在 Task 1-5 全部完成后执行

## Recommended Parallel Batches

- **Batch A:** Task 1
- **Batch B:** Task 2 + Task 3（并行，分开 ownership）
- **Batch C:** Task 4 + Task 5（并行，前提是 Task 1-3 已完成且 API shape 冻结）
- **Batch D:** Task 6

## Placeholder Scan

- 无 `TODO` / `TBD` / “类似 Task N” 占位说明
- 所有新增状态、helper、组件、测试入口与验证命令均已在任务中显式列出
