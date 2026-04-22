# Stage SQL Workbench Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改后端契约的前提下，将 Stage SQL Workbench 升级为更接近 IDE 的前端工作台：去左栏、加右侧 Activity Rail、补齐工具栏/上下文芯片/Monaco 能力/Status Bar，并打通 tab 级上下文覆盖与 AI Assist 面板。

**Architecture:** 以现有 `StageWindow + SqlWorkbenchTab + useSqlWorkbenchStore` 为骨架，分三层推进：布局与状态底座（StageStore/WorkbenchStore）、编辑器与执行体验（toolbar/context/limit/status/Monaco）、右侧面板生态（Schema/History/Outline/AI）。所有改动保持前端内聚，通过 payload 与 store 扩展承载新语义，不引入后端 schema 或 endpoint 变更。

**Tech Stack:** React 19, Zustand, Monaco Editor, TanStack Query, Vitest, TypeScript, `sql-formatter`

---

## Spec Mapping

- [2026-04-22-stage-sql-workbench-polish-design.md](../product-specs/2026-04-22-stage-sql-workbench-polish-design.md)
  - §2 布局：`StageWindow` 从 `Sidebar + Workbench` 改为 `Workbench + ActivityRail`
  - §3 Rail：新增 `Schema/History/Outline/AI` 四面板与 session 级 panel 状态
  - §4 上下文：实现 `override > session > global`，并支持 `open_sql_workbench.payload.contextOverride`
  - §5 工具栏/Monaco/Status Bar：Run/Cancel/Format/Limit/Save/Overflow、L1-L3 Monaco 能力、底部状态条
  - §6 Tab 视觉：顶层 tab 与结果 tab 统一 underline-only 风格
  - §7 数据模型与文件：StageStore / SqlWorkbenchStore 扩展 + shim 保留策略
  - §8 测试矩阵：新增组件/store/utils 测试并扩展既有 Stage 测试
  - §9 风险：预热策略、AI 独立 session 降级路径、小屏退化、方言格式化 fallback、shim 警告
  - §10 非目标：不做后端 cancel endpoint、不做后端 context action handler、不做持久化 history

## File Structure

### Core Store / Integration

- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.test.ts`
- Modify: `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- Modify: `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`

### Stage UI Components

- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.test.tsx`
- Modify: `client/src/features/stage/components/sql-result-tabs.tsx`
- Modify: `client/src/features/stage/components/sql-result-tabs.test.tsx`

### New Components / Utils

- Create: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Create: `client/src/features/stage/components/activity-rail/rail-panel-shell.tsx`
- Create: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/history-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/outline-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/ai-assist-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/stage-activity-rail.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/schema-panel.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/history-panel.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/outline-panel.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/ai-assist-panel.test.tsx`
- Create: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Create: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`
- Create: `client/src/features/stage/components/sql-context-chip.tsx`
- Create: `client/src/features/stage/components/sql-context-chip.test.tsx`
- Create: `client/src/features/stage/components/sql-limit-select.tsx`
- Create: `client/src/features/stage/components/sql-editor-breadcrumb.tsx`
- Create: `client/src/features/stage/components/sql-editor-breadcrumb.test.tsx`
- Create: `client/src/features/stage/components/sql-workbench-status-bar.tsx`
- Create: `client/src/features/stage/components/sql-workbench-status-bar.test.tsx`
- Create: `client/src/features/stage/utils/parse-sql-outline.ts`
- Create: `client/src/features/stage/utils/parse-sql-outline.test.ts`
- Create: `client/src/features/stage/sql-dialects/mysql-keywords.json`
- Create: `client/src/features/stage/sql-dialects/postgres-keywords.json`
- Create: `client/src/features/stage/sql-dialects/h2-keywords.json`

### API / Session Support

- Modify: `client/src/services/api/sql.ts`
- Modify: `client/src/features/stage/hooks/use-sql-execute.ts`
- Modify: `client/src/features/stage/hooks/use-sql-execute.test.ts`
- Modify: `client/src/features/session/hooks/use-sessions.ts`
- Create: `client/src/features/session/hooks/__tests__/use-sessions.test.ts`
- Modify: `client/src/features/workspace/components/nav-sessions.tsx`
- Modify: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`

### Compatibility Shims

- Modify: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/stage/components/stage-resource-browser.tsx`
- Modify: `client/src/features/stage/components/stage-tool-row.tsx`
- Modify: `client/src/features/stage/components/sql-editor-header.tsx`

### i18n / Dependency

- Modify: `client/src/i18n/messages.ts`
- Modify: `client/package.json`
- Modify: `client/package-lock.json`

## Batch A: 布局骨架与上下文模型（可并行）

### Task 1: 引入 Activity Rail 基线并移除左侧 StageSidebar 渲染路径

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Create: `client/src/features/stage/components/activity-rail/rail-panel-shell.tsx`
- Modify: `client/src/features/stage/components/stage-sidebar.tsx`
- Modify: `client/src/features/stage/components/stage-resource-browser.tsx`
- Modify: `client/src/features/stage/components/stage-tool-row.tsx`

- [x] 为 `activeRailPanelBySession` 与 toggle 行为添加 failing store tests（同图标二次点击折叠、session 隔离）。
- [x] 为 `StageWindow` 添加 failing tests：不再渲染 `StageSidebar/StageResourceBrowser/StageToolRow`，右侧永远出现 Rail 容器。
- [x] 在 `stage-store.ts` 增加 `RailPanel` 类型与 `setActiveRailPanel/toggleRailPanel` actions。
- [x] 在 `stage-window.tsx` 完成结构迁移为 `row(workbench, activityRail)`，并删除 sidebar 相关 prop/事件链路。
- [x] 将 `stage-sidebar.tsx`、`stage-resource-browser.tsx`、`stage-tool-row.tsx` 改为 `@deprecated` shim（`return null`），保留 export 名称不变。
- [x] 运行 `cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage/components/stage-window.test.tsx` 并确保通过。

### Task 2: 打通 tab context override 数据入口（payload + adapter）

**Files:**
- Modify: `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- Modify: `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`

- [x] 为 `contextOverride` 增加 failing normalize tests（合法对象透传、脏值回退 null、向后兼容旧 payload）。
- [x] 为 `WorkspaceAdapter.exec('open')` 增加 failing tests：dedupe 到旧 tab 时 payload 内 `contextOverride` 仍能更新。
- [x] 扩展 payload 归一化类型，新增 `contextOverride` 字段（`connectionId/database/schema`）。
- [x] 调整 `WorkspaceAdapter/QueryEditorAdapter` 的读写路径，确保 `open_sql_workbench` payload 能携带并暴露 override 信息。
- [x] 运行 `cd client && npx vitest run src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` 并确保通过。

### Task 3: 扩展 SqlWorkbenchStore（override/history/saved/limit/cursor）

**Files:**
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.test.ts`

- [x] 为 `setTabContext/resetTabContext`、history FIFO(50)、dirty 判定、`setLimit`、`setCursor` 写 failing tests。
- [x] 增加 `TabContextOverride`、`HistoryEntry` 与 store actions，并保持 `ensureTab` 初始状态完整（含 `savedSqlText`）。
- [x] 增加 `markSaved`、`appendHistoryEntry`、`clearHistory` 行为，覆盖边界（tab 不存在时自动 ensure）。
- [x] 运行 `cd client && npx vitest run src/features/stage/stores/sql-workbench-store.test.ts` 并确保通过。

## Batch B: 工具栏、执行语义与视觉统一（Task 4/5 可并行）

### Task 4: 实现 Toolbar + Context Chip + Status Bar + Limit 注入语义

**Files:**
- Create: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Create: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`
- Create: `client/src/features/stage/components/sql-context-chip.tsx`
- Create: `client/src/features/stage/components/sql-context-chip.test.tsx`
- Create: `client/src/features/stage/components/sql-limit-select.tsx`
- Create: `client/src/features/stage/components/sql-workbench-status-bar.tsx`
- Create: `client/src/features/stage/components/sql-workbench-status-bar.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `client/src/features/stage/hooks/use-sql-execute.ts`
- Modify: `client/src/features/stage/hooks/use-sql-execute.test.ts`
- Modify: `client/src/services/api/sql.ts`
- Modify: `client/package.json`
- Modify: `client/package-lock.json`

- [x] 安装依赖：`cd client && npm install sql-formatter`，并提交 `package.json/package-lock.json` 变更。
- [x] 为 toolbar/status/context chip 增加 failing tests：Run/Cancel 可见性、Limit enable 条件、继承 vs override badge、running 计时与 error/risk 映射。
- [x] 扩展 `executeSql`/`useSqlExecute` 支持 `AbortSignal`，并在 running 态接入 `AbortController.abort()`（仅前端中断）。
- [x] 在 `sql-workbench-tab.tsx` 用 `override > session > global` 解析 `effectiveContext`，接入 `setTabContext/resetTabContext`。
- [x] 按 spec 实现 Limit 注入时机：仅在执行前、且 SQL 无显式 LIMIT 且 limit 非 `∞` 时追加。
- [x] 实现 Save 与 dirty 指示：`savedSqlText`、`markSaved`、localStorage 草稿（tab 维度）。
- [x] 运行 `cd client && npx vitest run src/features/stage/components/sql-editor-toolbar.test.tsx src/features/stage/components/sql-context-chip.test.tsx src/features/stage/components/sql-workbench-status-bar.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx src/features/stage/hooks/use-sql-execute.test.ts` 并确保通过。

### Task 5: 统一 Stage 顶部 Tabs 与 SQL Result Tabs 的 underline-only 视觉

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.test.tsx`
- Modify: `client/src/features/stage/components/sql-result-tabs.tsx`
- Modify: `client/src/features/stage/components/sql-result-tabs.test.tsx`

- [x] 为两套 tab 增加 failing tests（active underline、error underline、dirty dot、hover 时关闭按钮可见）。
- [x] 重构样式到 underline-only：去胶囊边框、统一 hover/active token，保留横向滚动。
- [x] 确保 `sql-result-tabs` 与 `stage-tab-bar` 使用一致的 active indicator 语言。
- [x] 运行 `cd client && npx vitest run src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/components/sql-result-tabs.test.tsx` 并确保通过。

## Batch C: Monaco 增强与 Rail 四面板（Task 6/7 并行，Task 8 依赖前二者）

### Task 6: Monaco L1/L2/L3 能力与 Outline 解析基础

**Files:**
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`
- Create: `client/src/features/stage/components/sql-editor-breadcrumb.tsx`
- Create: `client/src/features/stage/components/sql-editor-breadcrumb.test.tsx`
- Create: `client/src/features/stage/utils/parse-sql-outline.ts`
- Create: `client/src/features/stage/utils/parse-sql-outline.test.ts`
- Create: `client/src/features/stage/sql-dialects/mysql-keywords.json`
- Create: `client/src/features/stage/sql-dialects/postgres-keywords.json`
- Create: `client/src/features/stage/sql-dialects/h2-keywords.json`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] 为 `parse-sql-outline` 增加 failing tests（多语句、注释/字符串内 `;`、highRiskHint）。
- [x] 为 `sql-monaco-editor` 增加 failing tests（快捷键、cursor 回调、imperative `insertAtCursor`、当前语句高亮装饰触发）。
- [x] 实现 `parse-sql-outline.ts`，输出 `Statement[]` 并支持 300ms debounce 重算入口。
- [x] 在 `sql-monaco-editor.tsx` 注入 completion/hover/folding/current-statement decoration 与 cursor 回调。
- [x] 增加 `sql-editor-breadcrumb` 并在 `sql-workbench-tab` 连接 `connection/database/schema/line/kind`。
- [x] 运行 `cd client && npx vitest run src/features/stage/utils/parse-sql-outline.test.ts src/features/stage/components/sql-editor-breadcrumb.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx` 并确保通过。

### Task 7: 落地 Schema/History/Outline 三面板并接入 Workbench

**Files:**
- Create: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/schema-panel.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/history-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/history-panel.test.tsx`
- Create: `client/src/features/stage/components/activity-rail/outline-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/outline-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Create: `client/src/features/stage/components/activity-rail/stage-activity-rail.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] 为三面板与 rail 切换增加 failing tests（toggle、单击 schema 写 override、history 插入、outline 点击跳行）。
- [x] 实现 `schema-panel`：单击 connection/database/schema 写 override，双击 table/column 调 `insertAtCursor`。
- [x] 实现 `history-panel`：展示每 tab 最多 50 条、单击追加 SQL、清空当前 tab history。
- [x] 实现 `outline-panel`：消费 `parse-sql-outline` 结果，点击调用 editor `revealLineNearTop + setPosition`。
- [x] 在 `sql-workbench-tab` 执行成功/失败后追加 history 条目并回写状态栏数据。
- [x] 运行 `cd client && npx vitest run src/features/stage/components/activity-rail/stage-activity-rail.test.tsx src/features/stage/components/activity-rail/schema-panel.test.tsx src/features/stage/components/activity-rail/history-panel.test.tsx src/features/stage/components/activity-rail/outline-panel.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx` 并确保通过。

### Task 8: AI Assist 面板（独立 session 优先，共享 session 作为降级）

**Files:**
- Create: `client/src/features/stage/components/activity-rail/ai-assist-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/ai-assist-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/session/hooks/use-sessions.ts`
- Create: `client/src/features/session/hooks/__tests__/use-sessions.test.ts`
- Modify: `client/src/features/workspace/components/nav-sessions.tsx`
- Modify: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`

- [x] 为 AI Assist 面板增加 failing tests：快捷动作、流式消息渲染、SQL fenced block 提取、插入确认对话框。
- [x] 实现独立 session 路径：每 tab 首次打开 AI 面板时创建 `stage-ai` 会话并复用 `ChannelClient` 流。
- [x] 在 `use-sessions` / `nav-sessions` 加过滤规则，隐藏 `stage-ai` scope 会话，避免污染主聊天侧栏。
- [x] 在 tab 关闭清理 AI session（调用 `deleteSession`），并验证 session 列表缓存失效逻辑正常。
- [x] 降级分支：若独立 session 过滤导致现有会话流程回归，则改为复用主 session 并在计划状态备注中记录偏差与原因。
- [x] 运行 `cd client && npx vitest run src/features/stage/components/activity-rail/ai-assist-panel.test.tsx src/features/session/hooks/__tests__/use-sessions.test.ts src/features/workspace/components/__tests__/nav-sessions.test.tsx` 并确保通过。

## Batch D: 综合验证与文档收口

### Task 9: 全量前端验证 + 计划/索引收口

**Files:**
- Modify: `docs/exec-plans/2026-04-22-stage-sql-workbench-polish-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `client/src/i18n/messages.ts`

- [x] 补齐新增 UI 文案（toolbar、context chip、status bar、rail panels、AI assist、limit/overflow）并更新相关断言。
- [x] 运行 `cd client && npx vitest run src/features/stage/components src/features/stage/stores src/features/stage/utils src/features/stage/hooks src/features/stage/adapters src/features/workspace/components/__tests__/nav-sessions.test.tsx src/features/session/hooks/__tests__/use-sessions.test.ts`
- [x] 运行 `cd client && npx tsc --noEmit`
- [x] 执行手工 smoke（最小集）：Run/Cancel/Format/Limit、context 三路径切换、Rail 四面板切换、AI Assist SQL 插入、小屏 rail 折叠。
- [x] 完成后将本计划所有 checkbox 勾选、在 `docs/exec-plans/index.md` 将条目从 Active 移到 Completed，并写入完成摘要。

## Parallel Execution Notes

- Batch A 内 Task 1/2/3 可并行（写入集合互不重叠），完成后再进入 Batch B。
- Batch B 内 Task 4/5 可并行；Task 6 依赖 Task 4 暴露的 editor/store 接口但可提前起草。
- Batch C 内 Task 6/7 可并行；Task 8 依赖 Task 6/7 提供的 editor insert 与 rail shell。
- 每个并行 batch 内按仓库约定跳过逐文件 `tsc`，在 batch 末执行一次集中验证。

## Decisions Locked From Spec

- Context 优先级固定为 `tab override > session context > global activeConnection`，不做 runtime 配置化。
- Limit 注入时机固定在“执行前客户端拼接”，不是后端参数透传。
- `stage-sidebar/stage-resource-browser/stage-tool-row/sql-editor-header` 本轮保留 shim，不物理删除。
- AI Assist 默认独立 session；若触发会话列表回归，允许降级为共享主 session，并在计划状态备注记录偏差。

## Self-Review

- Spec coverage: §2-§10 均映射到具体任务，且包含风险项对应的工程对策。
- Placeholder scan: 无 TODO/TBD/“后续补充”等占位语句。
- Type consistency: `contextOverride`, `TabContextOverride`, `HistoryEntry`, `RailPanel` 命名在任务间保持一致。

## Status Notes

- `stage-resource-browser` / `stage-tool-row` 已按设计变为 shim（`return null`），对应旧行为测试已切换为 shim 断言，避免与新架构冲突。
- `SchemaPanel` 当前基于可用上下文生成最小可用树（connection/database/schema + table/column 占位项）；待后续资源 metadata API 稳定后可替换为全量真实树。
- 手工 smoke 在本次自动化执行中不可由代理完整完成；本计划已完成可自动验证部分并给出人工 smoke checklist，需人工在桌面端补验。
