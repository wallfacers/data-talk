# Composer Data Source Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Composer 底部新增与模型并列的数据源选择器，支持搜索和最近使用排序；当用户发送消息或触发需要数据库上下文的动作但当前没有活动数据源时，直接拉起选择弹框并在选中后自动恢复原动作，而不是先报错阻断；同时为 Stage 卡片固化来源数据源并提供显式“用此数据源继续”，并给 UI Object Protocol 增加 `ui_exec(workspace, choose_connection)` 适配器能力。

**Architecture:** 复用现有 `activeConnectionId` 作为唯一“当前活动数据源”状态，不新增平行 source store。新增一个全局 `DataSourcePickerDialogHost` + zustand 请求/解析器 store，统一服务于两条路径：1) Composer 本地动作缺库时的待恢复流程；2) `ui_exec choose_connection` 这类需要显式向用户索取连接的前端协议动作。Stage 继续沿用 `StageTab.connectionId`，新增来源显示与显式回切动作，不做隐式自动切换。

**Tech Stack:** React 19 + TypeScript + Zustand + TanStack Query + shadcn/ui `Dialog` + Vitest / Testing Library。

**Spec:** [../product-specs/2026-04-20-composer-data-source-picker-design.md](../product-specs/2026-04-20-composer-data-source-picker-design.md)

---

## File Structure Map

### Create

- `client/src/features/session/data-source-picker/data-source-picker.tsx`
- `client/src/features/session/data-source-picker/data-source-picker-dialog.tsx`
- `client/src/features/session/data-source-picker/data-source-picker-dialog-host.tsx`
- `client/src/features/session/data-source-picker/data-source-picker-store.ts`
- `client/src/features/session/data-source-picker/recent-connections.ts`
- `client/src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx`
- `client/src/features/session/data-source-picker/__tests__/data-source-picker-dialog.test.tsx`
- `client/src/features/session/data-source-picker/__tests__/data-source-picker-store.test.ts`
- `client/src/features/session/hooks/use-pending-connection-resume.ts`
- `client/src/features/session/hooks/__tests__/use-pending-connection-resume.test.tsx`

### Modify

- `client/src/routes/__root.tsx` — 挂全局 `DataSourcePickerDialogHost`
- `client/src/features/session/prompt-composer.tsx` — 接入 `DataSourcePicker`，发送前缺库时走 chooser + 自动恢复
- `client/src/features/session/session-canvas.tsx` — 挂 `usePendingConnectionResume`
- `client/src/stores/session-store.ts` — 新增 pending connection 相关状态
- `client/src/features/connection/store.ts` — 仅保留 `activeConnectionId` 为唯一活动连接源；必要时补 helper，不新增平行状态
- `client/src/services/ui-router/UIRouter.ts` — 放通 `workspace.choose_connection`
- `client/src/features/actions/ui-handlers.ts` — `ui_exec` 复用 chooser host 结果
- `client/src/features/stage/components/stage-window.tsx`
- `client/src/features/stage/components/bang-query-tab.tsx`
- `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- `client/src/features/stage/adapters/BangQueryAdapter.ts`
- `client/src/stores/stage-store.ts` — 如需补 `connectionName` 等来源展示字段
- `client/src/features/stage/**/__tests__/*` — 补来源展示 / 回切测试

### Likely Untouched

- `server/**` — 本期仅前端实现，不新增后端 schema / API
- `client/src/features/settings/data-sources/**` — 复用现有连接查询，不改设置页结构
- `client/src/services/api/session.ts` — 会话创建接口保持 `connectionId` 入参不变

---

## Task 1: 全局 Data Source Picker 基础设施

**Files:**
- Create: `client/src/features/session/data-source-picker/data-source-picker-store.ts`
- Create: `client/src/features/session/data-source-picker/data-source-picker-dialog.tsx`
- Create: `client/src/features/session/data-source-picker/data-source-picker-dialog-host.tsx`
- Create: `client/src/features/session/data-source-picker/recent-connections.ts`
- Create: `client/src/features/session/data-source-picker/__tests__/data-source-picker-store.test.ts`
- Create: `client/src/features/session/data-source-picker/__tests__/data-source-picker-dialog.test.tsx`
- Modify: `client/src/routes/__root.tsx`

**Intent:** 建立唯一的“请求用户选择数据源”基础设施。任何地方需要连接时，不直接各自弹 dialog，而是调用统一 store / host，拿到 Promise 结果。

- [x] **Step 1.1: 写失败测试 — chooser store 可打开、解析和取消**
- [x] **Step 1.2: 实现 `data-source-picker-store.ts`**
  - 提供 `requestPick(options?) => Promise<{ connectionId: string; connectionName: string } | { cancelled: true }>`
  - 提供 `resolvePick(...)` / `cancelPick()`
  - store 持有 `open`、`reason`、`resolver`、`preferredConnectionId?`
- [x] **Step 1.3: 写失败测试 — dialog 支持搜索和最近使用优先**
- [x] **Step 1.4: 实现 `recent-connections.ts`**
  - `readRecentConnectionIds()`
  - `writeRecentConnectionIds(nextIds)`
  - `rankConnections(connections, recentIds, q)`
- [x] **Step 1.5: 实现 `DataSourcePickerDialog`**
  - 列表项显示：连接名、`kind · host · databaseName`、状态信息
  - 搜索维度：名称 / 类型 / 主机 / databaseName
  - 排序：最近使用优先，其余按名称
  - 点击项后调用 `onPick`
- [x] **Step 1.6: 实现全局 `DataSourcePickerDialogHost`**
  - 读取 chooser store 的 `open`
  - 用 `useConnections()` 拉取连接列表
  - 选中后写 recent ids、调用 `resolvePick`
  - 取消时 `cancelPick`
- [x] **Step 1.7: 在 `__root.tsx` 挂载 host**
- [x] **Step 1.8: 运行专项测试**
  - `cd client && npx vitest run src/features/session/data-source-picker/__tests__/data-source-picker-store.test.ts src/features/session/data-source-picker/__tests__/data-source-picker-dialog.test.tsx`
- [x] **Step 1.9: 类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 2: Composer 集成 + 缺库自动恢复原动作

**Files:**
- Create: `client/src/features/session/data-source-picker/data-source-picker.tsx`
- Create: `client/src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx`
- Create: `client/src/features/session/hooks/use-pending-connection-resume.ts`
- Create: `client/src/features/session/hooks/__tests__/use-pending-connection-resume.test.tsx`
- Modify: `client/src/stores/session-store.ts`
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/session-canvas.tsx`

**Intent:** 把数据源选择器放进 Composer 主工作流，并在“没有活动数据源”时自动补全上下文再继续，而不是报错后让用户重试。

- [x] **Step 2.1: 扩 `session-store.ts` 的 pending connection 状态**
  - 新增 `pendingConnectionPrompt: boolean`
  - 新增 `pendingActionAfterConnectionPick`（最小必要信息，避免把函数放进 persist）
  - 新增 setter / clear API
- [x] **Step 2.2: 写失败测试 — `PromptComposer` 无活动数据源时发送会拉起 chooser 而非报错**
- [x] **Step 2.3: 实现 `DataSourcePicker` trigger**
  - 视觉对齐 `ModelPicker`
  - 已选中显示连接名；未选中显示“选择数据源”
  - 点击按钮通过 chooser store 请求选择
  - 选中后 `useConnectionStore.getState().setActive(id)`
- [x] **Step 2.4: 修改 `prompt-composer.tsx`**
  - 在 `ModelPicker` 旁插入 `DataSourcePicker`
  - 发送 / 建会话 / 需要库上下文的 `!<sql>` 路径，在缺 `activeConnectionId` 时调用 chooser
  - 选完后自动恢复原动作；取消时保留输入
- [x] **Step 2.5: 实现 `use-pending-connection-resume.ts`**
  - 风格对齐现有 `usePendingPromptResume`
  - 在 `SessionCanvas` 挂载
  - 对“先选库再继续发送”做统一恢复，避免 `prompt-composer` 内部散落恢复逻辑
- [x] **Step 2.6: 运行专项测试**
  - `cd client && npx vitest run src/features/session/data-source-picker/__tests__/data-source-picker.test.tsx src/features/session/hooks/__tests__/use-pending-connection-resume.test.tsx`
- [x] **Step 2.7: 类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 3: UI Object Protocol 扩展 `ui_exec(workspace, choose_connection)`

**Files:**
- Modify: `client/src/services/ui-router/UIRouter.ts`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/actions/ui-handlers.ts`
- Create or Modify relevant tests under:
  - `client/src/services/ui-router/__tests__/UIRouter.test.ts`
  - `client/src/features/actions/__tests__/ui-handlers.test.ts`
  - `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`

**Intent:** 把“向用户索取数据源”的能力接入现有 UI Router，让 AI / Stage / 其他前端动作都能复用统一 chooser，而不是只服务 Composer。

- [x] **Step 3.1: 写失败测试 — `workspace.choose_connection` 返回用户选中的连接**
- [x] **Step 3.2: 扩 `WorkspaceAdapter` actions**
  - 在 `read('actions')` 或 `exec` 定义里增加 `choose_connection`
  - `exec('choose_connection')` 内部调用 chooser store 的 `requestPick`
- [x] **Step 3.3: 确认 `UIRouter.handleExec` 无需特殊分支**
  - 尽量复用现有 action schema / `instance.exec()` 流
  - 只在测试里覆盖 `ui_exec(workspace, choose_connection)`
- [x] **Step 3.4: 更新 `ui-handlers.ts` / bridge 测试**
  - 断言 `datatalk.ui.exec` 收到 `choose_connection` 时能透传结果
  - 用户取消时返回 `{ cancelled: true }`，不抛异常
- [x] **Step 3.5: 运行专项测试**
  - `cd client && npx vitest run src/services/ui-router/__tests__/UIRouter.test.ts src/features/actions/__tests__/ui-handlers.test.ts src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- [x] **Step 3.6: 类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 4: Stage 来源固化与“用此数据源继续”

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/bang-query-tab.tsx`
- Modify: `client/src/features/stage/adapters/BangQueryAdapter.ts`
- Modify relevant tests:
  - `client/src/stores/stage-store.test.ts`
  - `client/src/features/stage/**/__tests__/*`
  - `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts`

**Intent:** 保持“当前活动数据源”和“历史卡片来源数据源”分离。Stage 卡片展示来源，但绝不因为浏览卡片而自动改当前连接。

- [x] **Step 4.1: 明确 Stage tab 元数据**
  - `StageTab.connectionId` 继续作为来源唯一真相源
  - 视 UI 需要补 `connectionName`，避免每次展示都反查列表
- [x] **Step 4.2: 写失败测试 — bang_query / workspace tab 渲染来源 badge**
- [x] **Step 4.3: 更新 `open-bang-query-tab` / tab payload 写入来源数据源**
  - 新创建的 Tab 固化当时的 `activeConnectionId`
  - 如果能拿到连接展示名，同步固化
- [x] **Step 4.4: 在 `BangQueryTab` 或 `StageWindow` 增加来源展示**
  - 显示 badge：连接名优先，缺省时回退 id
- [x] **Step 4.5: 增加“用此数据源继续”动作**
  - 点击时只做 `useConnectionStore.getState().setActive(tab.connectionId)`
  - 可加轻量成功反馈
  - 明确不自动 rerun / 不改历史 tab 元数据
- [x] **Step 4.6: 写测试覆盖**
  - 切当前活动数据源不影响历史卡片来源展示
  - “用此数据源继续”只更新当前活动数据源
- [x] **Step 4.7: 运行专项测试**
  - `cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage`
- [x] **Step 4.8: 类型检查**
  - `cd client && npx tsc --noEmit`

---

## Task 5: Consolidated Verification

**Files:** no new files; verify the whole touched surface.

- [x] **Step 5.1: Run targeted front-end test batches**
  - `cd client && npx vitest run src/features/session/data-source-picker src/features/session/hooks src/features/actions src/services/ui-router src/features/stage`
- [x] **Step 5.2: Run full type-check**
  - `cd client && npx tsc --noEmit`
- [ ] **Step 5.3: Manual smoke checklist**
  - Composer 未选数据源时发送普通消息：直接弹选择器，选后自动发出
  - Composer 未选数据源时输入 `!select 1`：直接弹选择器，选后自动执行
  - 手动点击数据源 trigger：可搜索、可切换、最近使用排序生效
  - Stage 历史卡片显示来源数据源；切换当前活动数据源后旧卡片不变
  - 点击“用此数据源继续”只切换当前活动数据源，不自动重跑
  - 若 AI / action 调 `ui_exec(workspace, choose_connection)`：能弹 chooser 并返回结果
- [ ] **Step 5.4: Plan housekeeping after implementation**
  - 勾完本计划所有 checkbox
  - 在 `docs/exec-plans/index.md` 把条目从 Active 移到 Completed
  - 如实现中沉淀出新的连接绑定约定，同步回写 `ARCHITECTURE.md` / `docs/FRONTEND.md` / `docs/product-specs/index.md`

---

## Dependency Notes

- Task 1 必须先完成；Task 2 和 Task 3 都依赖 chooser host / store
- Task 4 依赖 Task 2 的活动数据源选择器已经稳定，因为 Stage 回切复用同一 `setActive`
- 全量验证只在 Task 1-4 代码写完后做一次 consolidated pass；中间按仓库规则保留必要的 `npx tsc --noEmit`

## Open Questions Resolved By Spec

- 主入口位置：放 Composer，不放 Stage
- 缺库行为：直接拉 chooser 并恢复原动作，不先 toast 阻断
- Stage 行为：固化来源、显式回切、不自动切换
- 协议扩展：使用 `ui_exec(workspace, choose_connection)`，不新增另一套前端 RPC
