# ER 模块端到端浏览器测试设计

> Status: Draft · 2026-05-06 · wallfacers
>
> Scope: 为 ER Inspector + ER Designer 两个 Stage Tab 全表面、全按钮、全交互建立 Playwright 端到端浏览器测试基线。**纯 UI-only 路线**，不依赖 OpenCode 模型，CI 稳定可跑。补齐已 closed `agents-mcp-full-coverage-e2e-plan.md`（MCP 路由 + 契约层）未触及的 UI 表面层。

---

## 1. 背景与定位

### 1.1 现状

- ER 产品形态见 [`docs/product-specs/2026-04-29-er-graph-browsing-design.md`](./2026-04-29-er-graph-browsing-design.md)：`er_inspector`（只读浏览真库）+ `er_designer`（独立 schema 草稿 + DDL 生成 → query_editor → guarded apply）。
- 协议契约见 [`docs/references/er-tab-protocol.md`](../references/er-tab-protocol.md)：`ui_read` / `ui_patch` / `ui_exec` 三段式，路径白名单 + exec verbs + 错误码。
- 现有 E2E 覆盖：[`client/tests/e2e/agents-batch5-er-tabs.spec.ts`](../../client/tests/e2e/agents-batch5-er-tabs.spec.ts)（已随 [`2026-05-06-agents-mcp-full-coverage-e2e-plan.md`](../exec-plans/2026-05-06-agents-mcp-full-coverage-e2e-plan.md) closed）覆盖：
  - **AI 路由层**：`workspace.open_er_inspector` / `workspace.open_er_designer` / `er_inspector.add_neighbors` / `fork_to_designer` / `er_designer.auto_layout` 等 5 条自然语言路由 case，依赖 `DATATALK_REAL_OPENCODE_MODEL` 环境变量，无 model 时整体 skip
  - **MCP 契约层**：12 条 `test.fixme`（client-side action 后端 `/mcp` 直调返 `UnsupportedOperationException`，结构性无法直测）
- POM 骨架：`pom/er-inspector.page.ts` / `pom/er-designer.page.ts` 已存在但仅有部分方法，未充分利用。

### 1.2 缺口

现有 E2E **几乎不覆盖 UI 交互层**。所有"按钮按了有没有反应、弹窗能不能闭合、拖节点位置回写不回写、刷新后 Tab 还在不在"这类用户最直接感知的回归，无任何端到端断言。Toolbar 13 个按钮 / 控件（Inspector 6 + Designer 7）、右键菜单 3 项、列内联 7 个字段、Bind Target Dialog 全流程、空态 CTA、Delete 键删选——全部裸跑。

### 1.3 目标

为 ER 全表面建立可在 CI 持续运行、对产品代码改动零侵入（除 1-2 处 `data-testid` 钩子）、对 AI/OpenCode 零依赖的浏览器端到端基线。每按钮、每交互、每弹窗状态、每持久化路径都有 1 个 happy-path test + 必要的失败 / 边界 test。

---

## 2. Design Inputs

- [`CLAUDE.md`](../../CLAUDE.md) — 项目工作守则（BUG Tracking Gate / Frontend Plan Gate / Parallel Plan Execution / Post-Edit Verification）
- [`client/DESIGN.md`](../../client/DESIGN.md) — 前端设计契约（cobalt-only accent / Stage 全局非 per-session / 密度分层）
- [`docs/references/er-tab-protocol.md`](../references/er-tab-protocol.md) — Inspector / Designer payload schema、patch path 白名单、exec verbs、错误码表
- [`docs/product-specs/2026-04-29-er-graph-browsing-design.md`](./2026-04-29-er-graph-browsing-design.md) — ER 模块产品 spec（不变量、12 条 AI Ergonomics 原则、DDL 生成范围）
- [`docs/product-specs/2026-04-30-er-canvas-redesign-design.md`](./2026-04-30-er-canvas-redesign-design.md) — Canvas 视觉契约
- [`docs/exec-plans/2026-05-06-agents-mcp-full-coverage-e2e-plan.md`](../exec-plans/2026-05-06-agents-mcp-full-coverage-e2e-plan.md) — 已 closed 的 MCP 全量 E2E 计划（互补不重复）
- [`docs/exec-plans/2026-05-05-sql-editor-mcp-e2e-test-plan.md`](../exec-plans/2026-05-05-sql-editor-mcp-e2e-test-plan.md) — SQL Editor E2E 计划（fixture / POM 复用基线）
- [`docs/bugs/README.md`](../bugs/README.md) — BUG 登记规范

## 3. Applicable Constraints from `client/DESIGN.md`

- **Stage 全局非 per-session**：`useStageStore` 单值；切 session 后 ER Tab 必须仍可见。本设计 §6 E6 用例直接验。
- **`StageTab` 无 `scope` 字段**：type-level scope 在 `tab-type-registry.ts`。断言读 store 时不假设 per-session shape。
- **cobalt 仅用于 focus / selection / primary action**：本设计不做像素级颜色断言（vitest 单测已覆盖 `ErTableNode`、`ErEdgeMarkers` 颜色）。
- **`prefers-reduced-motion` 下不强制动画**：拖节点 / Auto layout 后位置写入的等待用 `waitForFunction(payloadVersion)` 判完成，**不**用动画 settle 时间硬等。

---

## 4. 范围

### 4.1 In scope（必测）

| 表面 | 覆盖项 |
|---|---|
| ER Inspector Toolbar | Refresh、Auto layout、Fit view、Neighbor depth (0/1/2) 三档切换、Add virtual relation、Fork to designer |
| ER Inspector Canvas | 节点拖（位置回写 `/positions/{name}`）、viewport pan / zoom（写 `/viewport`）、空态文案 |
| ER Designer Toolbar | Add table、Auto layout、Fit view、Bind target、Diff vs DB（含未 bind 时 disabled + tooltip）、Generate DDL（同上）、Dialect 4 档切换 |
| ER Designer 右键菜单 | Rename、Add column、Delete table（伴随 relations 清理） |
| ER Designer 列内联编辑 | name / type / nullable / isPrimaryKey / isAutoIncrement / default / comment 七字段 |
| ER Designer 节点底部 | `+ Add column` 默认值 `new_column VARCHAR(255)`；列行 trash 删列 |
| ER Designer 拖连边 | 列 source-handle → 列 target-handle 创建 `relations/-`（ReactFlow handle 定位策略见 §7.3 R9）；Edge 改 relation type；Edge 删除 |
| ER Designer 键盘 | 选中节点 + Delete 键删（含 relations 清理）；选中 edge + Delete 键删 |
| Bind Target Dialog | 连接列表按 dialect 过滤、级联加载 database / schema、MySQL 不显示 schema select / PG 显示、Confirm / Cancel、empty state |
| ER Designer 空态 | `empty_designer` 文案 + Add table CTA 触发 |
| 入口 | 产品 UI 真实 ER 入口（Sidebar / 连接面板 / 单表右键），grep 后存在则点真入口；不存在 → BUG 登记 + 快捷路径 fallback |
| 跨 Tab | Inspector → Fork to Designer → 新 Designer Tab 出现，schema 形态对齐 |
| 持久化 | 刷新页面后 ER Tab 仍在、payload 字段（selection / positions / virtualRelations / notes / tables / relations / dialect / targetConnectionId）原样恢复 |
| 跨 session | 切 session 后 ER Tab 不消失 |
| 不变量 | Inspector 无 `/api/sql/execute` 调用；Designer Generate DDL 不自动执行；payload version 单调递增 |

### 4.2 Out of scope

- **AI 自然语言路由**：已 closed 的 `agents-batch5-er-tabs.spec.ts` 覆盖
- **MCP `/mcp` JSON-RPC 直调契约**：同上，且 `ui_exec` 多为 client-side action，结构性 fixme
- **多 dialect 真实连接矩阵**：单连接路径，Dialect 切换通过同一连接切 dialect → Generate DDL → 读 query_editor SQL 文本验证
- **性能 / 大 schema 压力**：100 表硬上限断言留 follow-up
- **像素级视觉断言**：`ErTableNode` / `ErEdge` / `ErEdgeMarkers` 颜色 / 字号 / 几何已有 vitest 覆盖
- **i18n 双语断言**：本测试用英文 fallback 文本（`useFallbackLabel` 兜底，稳定）
- **Plan B 专属功能**：若执行环境为 Plan A，`fork_to_designer` 返 `error.code = 'plan_b_only'` 直接 BUG 登记 + `test.fixme`，不算 spec 缺陷
- **Schema discovery / `/api/er/sync-from-db` 后端逻辑**：仅断言 UI 触发与 Tab payload 反映，不重写后端单测覆盖范围
- **Inspector `notes` CRUD**：payload schema 定义 `notes: { [tableName]: string }` 字段，但当前 `ErCanvas.tsx` / `er-inspector-tab.tsx` **无任何 UI 路径**（已 grep 验证：无创建 / 编辑 / 删除入口）。Inspector 的 notes 等同纯协议字段，UI-only spec 无法触发。如未来落地 notes UI，开新 follow-up plan 补 D31。E4 持久化断言相应**不**包含 `notes` 字段
- **Inspector `virtualRelations` 通过 toolbar 创建路径**：`onAddVirtualRelation` 是 noop（BUG-A），UI 触发不可能。E4/E5 断言不包含 virtualRelations 字段；I5 维持 `test.fixme`
- **Rename / 列名输入边界**：D14 仅覆盖正常改名 happy path；空字符串 / 重名 / SQL 保留字 / 超长名 等输入验证留 follow-up
- **多 Toast 提示文案断言**：仅断言 toast 出现 / 不出现，不断言文案具体字符串

### 4.3 不变量（端到端断言）

| ID | 不变量 | 断言机制 |
|---|---|---|
| INV-1 | Inspector 不写真库 | 全程 `startFetchRecorder`（hook `window.fetch`），spec 末尾断言 `/api/sql/execute` 命中 0。**前提**：DataTalk client services 全部走 `fetch()`（已 grep 验证：`src/services/api/sql.ts` / `diagnostics.ts` / `channel-client.ts` / `artifacts/promote-chart.ts` 均为 `fetch`，无 `XMLHttpRequest`、无 `@tauri-apps/api/http`；Tauri `invoke` 仅用于非 HTTP 原生命令）。若未来 ER 模块引入新传输层（例如 axios / Tauri HTTP plugin），断言会失效——执行计划 Task 0 必须重新 grep 验证；不通过则改用 `page.route('**/api/sql/execute', …)` 兜底。 |
| INV-2 | Designer Generate DDL 不自动执行 | ① `/api/sql/execute` 命中 0；② 生成的 query_editor Tab `state.results === []` |
| INV-3 | Stage 全局非 per-session | 切 session（`useSessionStore.setActive(otherId)`）后 `[data-er-tab-id]` count 不变 |
| INV-4 | payload version 单调递增 | 每次 mutation 后读 `[data-payload-version]` 严格 `>` 上一次基线 |
| INV-5 | force-flush 后立即可读 | `waitForPayloadVersion` 超时阈值 2s；超时 fail（说明 coordinator.flush 未触发） |

---

## 5. 文件结构

| File | 角色 |
|---|---|
| `client/tests/e2e/er-inspector-ui.spec.ts` | Inspector Toolbar + Canvas 全交互（11 tests） |
| `client/tests/e2e/er-designer-ui.spec.ts` | Designer Toolbar + 右键菜单 + 列内联 + 连边 + 键盘 + 空态 + Bind Dialog + DDL（30 tests） |
| `client/tests/e2e/er-entry-and-persistence.spec.ts` | 真实产品入口 + Fork 跨 Tab + 刷新持久化 + 跨 session（6 tests） |
| `client/tests/e2e/fixtures/er-seed.sql` | **条件创建**：当 `test-seed.sql` 缺 FK / 缺隐式关联候选列时新建。3 表 + 2 FK + 1 张含 `*_email` / `*_code` 隐式列 |
| `client/tests/e2e/fixtures/er-test-helpers.ts` | `openErInspectorViaShortcut` / `openErDesignerViaShortcut` / `readInspectorPayload` / `readDesignerPayload` / `waitForPayloadVersion` / `startFetchRecorder` |
| `client/tests/e2e/pom/er-inspector.page.ts` | **扩充骨架**：现 6 个 async 方法 → 14（新增 8：`clickRefresh / clickAutoLayout / clickFitView / setNeighborDepth / clickAddVirtualRelation / dragNode / getNodePosition / zoomIn / zoomOut / panCanvas / expectEmptyState / getMode / getPayloadVersion`） |
| `client/tests/e2e/pom/er-designer.page.ts` | **扩充骨架**：现 7 个 async 方法 → 28（新增 21：toolbar 完整集 / Bind Dialog 完整集 / 右键菜单 / 列内联 / 拖连边 / Edge 操作 / 键盘 / 空态） |
| `docs/bugs/BUG-NNNN-*.md` | 按需 |
| `docs/bugs/index.md` | 按需更新 |

---

## 6. 测试矩阵

### 6.1 Inspector（spec 1 — `er-inspector-ui.spec.ts`，11 tests）

| ID | 表面 | 测试名 | 关键断言 |
|----|------|--------|---------|
| I1 | Toolbar | `Refresh button re-fetches schema and bumps snapshotAt` | 调用前后 `payload.snapshotAt` 增大；`tablesSnapshot` 字段回写 |
| I2 | Toolbar | `Auto layout assigns non-zero positions to all selected tables` | `/positions` map 含 selection 全表；不存在 (0,0) 堆叠（除单表场景） |
| I3 | Toolbar | `Fit view writes /viewport patch` | 触发后 `payload.viewport.zoom > 0` 且 `x / y` 字段已写入；不断言具体范围（zoom 受 viewport 尺寸影响在 CI headless / 本地 dev 间差异显著，精确范围由 vitest 单测覆盖） |
| I4 | Toolbar | `Neighbor depth 0 → only selected; 1 → 直接邻居; 2 → 二跳邻居` | 每档下 DOM 节点数变化、edge 数变化；payload `/neighborDepth` 回写 |
| I5 | Toolbar | `Add virtual relation 进入编辑模式 / 弹窗 / 回写 /virtualRelations` | **现状**：`onAddVirtualRelation` 是 noop（`ErCanvas.tsx:223`），按钮点击无效 → **预登 BUG-A**（P1）+ `test.fixme` 标 `// see docs/bugs/BUG-NNNN-er-add-virtual-relation-noop.md` |
| I6 | Toolbar | `Fork to designer opens new er_designer tab with matching tables/columns` | 新 Tab 出现、`type === 'er_designer'`、tables 数对齐；若返 `plan_b_only` → BUG + `test.fixme` |
| I7 | Canvas | `Drag node persists position to /positions/{name}` | 拖完后 `data-payload-version` +1；store 中位置等于拖动后坐标（容忍 ±2px） |
| I8 | Canvas | `Pan canvas writes /viewport patch` | viewport `x/y` 变更 |
| I9 | Canvas | `Zoom in/out via Controls writes /viewport zoom` | zoom 增量符合方向 |
| I10 | Empty | `Empty selection 显示 ErEmptyState reason='empty_selection'` | i18n key `erCanvas.emptyState.empty_selection` 文本可见 |
| I11 | 不变量 | `Inspector 操作链路无 /api/sql/execute 调用` | `fetchRecorder.callsMatching(/\/api\/sql\/execute/) === 0` |

### 6.2 Designer（spec 2 — `er-designer-ui.spec.ts`，30 tests）

| ID | 表面 | 测试名 |
|----|------|--------|
| D1 | Toolbar | `Add table 添加 new_table 到 /tables/-` |
| D2 | Toolbar | `Auto layout 重排所有节点位置` |
| D3 | Toolbar | `Fit view 写 /viewport` |
| D4 | Toolbar | `Bind target 打开 dialog` |
| D5 | Toolbar | `Diff vs DB 在未 bind 时 disabled + 显示 tooltip "Bind a target database first"` |
| D6 | Toolbar | `Generate DDL 在未 bind 时 disabled + 显示同 tooltip` |
| D7 | Toolbar | `Dialect 切换 4 选项可选，写 /dialect 路径` |
| D8 | Bind Dialog | `连接列表按 dialect 过滤（mysql dialect 只看到 mysql kind）` |
| D9 | Bind Dialog | `选连接 → database / schema 级联加载` |
| D10 | Bind Dialog | `MySQL 不显示 schema select；PostgreSQL 显示` |
| D11 | Bind Dialog | `空连接列表显示 erCanvas.bindDialog.empty 文案` |
| D12 | Bind Dialog | `Confirm 后写 /targetConnectionId / /targetDatabase / /targetSchema 并启用 Diff/DDL 按钮` |
| D13 | Bind Dialog | `Cancel 不写 payload` |
| D14 | 右键菜单 | `Rename 进入名称编辑态，回车写 /tables[id=X]/name` |
| D15 | 右键菜单 | `Add column 写 /tables[id=X]/columns/-` |
| D16 | 右键菜单 | `Delete table 写 remove，关联 relations 一并删除` |
| D17 | 列内联 | `改列 name / type / nullable / isPrimaryKey / isAutoIncrement / default / comment 各自走对应 replace patch` |
| D18 | 列内联 | `点 + 添加列追加 new_column VARCHAR(255)` |
| D19 | 列内联 | `点 trash 删列 写 remove /columns[id=...]` |
| D20 | 拖连边 | `从列 source-handle 拖到另一列 target-handle 创建 relations/-` |
| D21 | Edge | `Edge 改 relation type 写 /relations[id=X]/type` |
| D22 | Edge | `Edge 删除 写 remove` |
| D23 | 键盘 | `选中节点 + Delete 键删除，伴随 relations 清理` |
| D24 | 键盘 | `选中 edge + Delete 键删除` |
| D25 | 空态 | `tables 为空显示 'empty_designer' + Add table CTA` |
| D26 | DDL | `Bind 后 Generate DDL 生成 query_editor Tab；切到 Tab 后 SQL 含 CREATE TABLE` |
| D27 | DDL | `Generate DDL 后 query_editor results === [] (未自动执行)` |
| D28 | DDL | `切 dialect = postgresql 后 Generate DDL，新 query_editor SQL 用 PG 语法（如 SERIAL / BIGSERIAL）` |
| D29 | DDL | `dialect = sqlite 时 Generate DDL 含 skipped 信息（仅 CREATE TABLE，无 ALTER ADD FK）` |
| D30 | 不变量 | `Designer 全程无任何 /api/sql/execute 调用` |

### 6.3 入口 + 持久化（spec 3 — `er-entry-and-persistence.spec.ts`，6 tests）

| ID | 测试名 | 备注 |
|----|--------|------|
| E1 | `Sidebar / 连接面板真实 "View ER" 入口存在则点击打开 inspector tab` | grep `client/src/features/connection /` `client/src/features/stage/components/left-rail/` 找入口；找到走真路径 |
| E2 | `若无真实入口 → BUG 登记 + skip + fallback 用快捷路径开 Tab 验证 §6.1 全集仍能跑` | 兜底用例 |
| E3 | `Inspector → Fork to Designer 后 Designer 出现，refresh viewport / payload version 正常` | `Inspector.fork_to_designer` exec |
| E4 | `刷新页面后 ER Inspector Tab 仍在、selection / positions / viewport / neighborDepth 完整恢复` | `page.reload()` 后读 store。**不**包含 `virtualRelations` / `notes`（无 UI 写入路径，见 §4.2 Out of scope） |
| E5 | `刷新页面后 ER Designer Tab 仍在、tables / relations / dialect / targetConnectionId 完整恢复` | 同上 |
| E6 | `切换 session 后 ER Tab 不消失（验证 stage 全局非 per-session）` | INV-3 |

---

## 7. POM + Helper 接口契约

### 7.1 `er-test-helpers.ts`（新增）

```ts
export async function openErInspectorViaShortcut(
  page: Page,
  opts: { connectionId: string; tables: string[]; neighborDepth?: 0|1|2 }
): Promise<{ tabId: string }>
//   实现：page.evaluate 调 useStageStore.getState().openTab + useErTabsStore.getState().hydrateInspector
//   返回新 tabId（DOM `[data-er-tab-id]` 上读）

export async function openErDesignerViaShortcut(
  page: Page,
  opts: { dialect: 'mysql'|'postgresql'|'h2'|'sqlite'; seedTables?: ErTableDraft[] }
): Promise<{ tabId: string }>

export async function readInspectorPayload(page: Page, tabId: string): Promise<ErInspectorPayload>
export async function readDesignerPayload(page: Page, tabId: string): Promise<ErDesignerPayload>

export async function waitForPayloadVersion(
  page: Page,
  tabId: string,
  predicate: (v: number) => boolean,
  timeoutMs?: number  // 默认 2000
): Promise<number>

export async function startFetchRecorder(page: Page): Promise<{
  callsMatching: (urlPattern: RegExp) => number
  stop: () => void
}>
```

### 7.2 `pom/er-inspector.page.ts` 扩充

现有方法保留：`getTableNodes / getRelations / getVirtualRelations / clickAddNeighbors / clickForkToDesigner / getActiveTabId`。新增：

```ts
class ErInspectorPage {
  // toolbar
  async clickRefresh(): Promise<void>
  async clickAutoLayout(): Promise<void>
  async clickFitView(): Promise<void>
  async setNeighborDepth(depth: 0 | 1 | 2): Promise<void>
  async clickAddVirtualRelation(): Promise<void>
  // canvas
  async dragNode(tableName: string, dx: number, dy: number): Promise<void>
  async getNodePosition(tableName: string): Promise<{ x: number; y: number }>
  async zoomIn(steps?: number): Promise<void>
  async zoomOut(steps?: number): Promise<void>
  async panCanvas(dx: number, dy: number): Promise<void>
  // assertions
  async expectEmptyState(reason: 'empty_selection'): Promise<void>
  async getMode(): Promise<'inspector'>           // 读 [data-er-mode]
  async getPayloadVersion(): Promise<number>      // 读 [data-payload-version]
}
```

### 7.3 `pom/er-designer.page.ts` 扩充

```ts
class ErDesignerPage {
  // toolbar
  async clickAddTable(): Promise<void>
  async clickAutoLayout(): Promise<void>
  async clickFitView(): Promise<void>
  async clickBindTarget(): Promise<void>
  async clickDiffVsDb(): Promise<{ disabled: boolean }>
  async clickGenerateDdl(): Promise<{ disabled: boolean }>
  async setDialect(d: 'mysql'|'postgresql'|'h2'|'sqlite'): Promise<void>
  async getDialect(): Promise<string>
  async getDiffDisabledHint(): Promise<string | null>
  async getGenerateDdlDisabledHint(): Promise<string | null>
  // bind dialog
  async fillBindTarget(opts: { connectionId; database?; schema? }): Promise<void>
  async confirmBindTarget(): Promise<void>
  async cancelBindTarget(): Promise<void>
  async getBindDialogConnectionOptions(): Promise<string[]>
  async getBindDialogEmptyText(): Promise<string | null>
  async isSchemaSelectVisible(): Promise<boolean>
  // table / column edit
  async openContextMenu(tableName: string): Promise<void>
  async clickContextMenuItem(label: 'rename'|'addColumn'|'deleteTable'): Promise<void>
  async renameTable(oldName: string, newName: string): Promise<void>
  async addColumnViaToolbarPlus(tableName: string): Promise<void>
  async editColumnField(
    table: string, column: string,
    field: 'name'|'type'|'nullable'|'isPrimaryKey'|'isAutoIncrement'|'default'|'comment',
    value: any
  ): Promise<void>
  async deleteColumn(table: string, column: string): Promise<void>
  // relations
  async dragConnect(fromTable: string, fromColumn: string, toTable: string, toColumn: string): Promise<void>
  //   实现策略（R9）：
  //   ① 通过 ReactFlow 既有 sourceHandle 命名规则 `${columnId}-source` / `${columnId}-target` 定位（见 buildDesignerConnectPatch）
  //   ② 优先用 selector `[data-handleid="${columnId}-source"]` + `[data-handlepos]` 命中 ReactFlow handle DOM
  //   ③ 兜底：定位列行 `[data-testid="er-row-${col}"]` 后取右侧 4×8px hit area 几何中心
  //   ④ 拖拽用 page.mouse.down → 多步 page.mouse.move（≥6 步缓动，避免 ReactFlow 把单步识别成点击） → page.mouse.up
  async setEdgeRelationType(edgeId: string, type: 'one_to_one'|'one_to_many'|'many_to_one'|'many_to_many'): Promise<void>
  async deleteEdge(edgeId: string): Promise<void>
  // keyboard
  async selectNode(tableName: string): Promise<void>
  async selectEdge(edgeId: string): Promise<void>
  async pressDelete(): Promise<void>
  // empty state
  async expectEmptyState(reason: 'empty_designer'): Promise<void>
  async clickEmptyAddTable(): Promise<void>
  // assertions
  async getPayloadVersion(): Promise<number>
}
```

### 7.4 选择器协议

按优先级使用：

1. **已有 `data-testid` / `data-er-*` 属性**（最稳）
   - `[data-er-tab-id="<tabId>"]` Tab 根容器（已存在，`ErCanvas.tsx:289`）
   - `[data-er-mode="inspector|designer"]`（已存在；位置：`ErToolbar.tsx` ModeBadge **以及** `ErTableNode.tsx:75 / :119` 节点容器）
   - `[data-testid="er-mode-badge"]`（已存在）
   - `[data-er-edge]` / `[data-from-table]` / `[data-to-table]` / `[data-relation-type]` / `[data-is-virtual]`（部分已存在）
   - `[data-testid="er-row-<columnName>"]`（已存在，列行）
   - ReactFlow 自带：`[data-handleid="${columnId}-source"]` / `[data-handleid="${columnId}-target"]`（`ErCanvas.tsx` 设的 `sourceHandle / targetHandle`，由 ReactFlow 渲染为 DOM 属性）

2. **`role + accessible name`**（i18n + fallback 兜底）
   - Toolbar 按钮所有 `aria-label` 来自 `useFallbackLabel(t)`，CI 上若 i18n 字典缺失自动 fallback 英文
   - Bind Dialog 字段 `Label htmlFor=` 已规整（`er-bind-target-connection` / `er-bind-target-database` / `er-bind-target-schema`）

3. **新增 testid 钩子**（plan Task 0 前置，1-3 行产品代码改动）
   - `data-payload-version={version}` 标在 `[data-er-tab-id]`：让 POM 拿 payload 版本无需 evaluate
   - `data-er-table-id="<id>"` + `data-er-table-name="<name>"` 标在 `ErTableNode`：节点定位不依赖文本匹配
   - `data-er-column-handle="<columnId>:source"` / `:target` 显式标在列行 source/target Handle DOM（兜底，若 ReactFlow `[data-handleid]` 在版本升级中变化）
   - `data-testid="er-toolbar-<action>"`（refresh / auto-layout / fit-view / add-table / bind-target / diff / generate-ddl / fork-to-designer / add-virtual-relation）：toolbar 按钮稳定 lookup

   **若产品代码不允许加**：POM 退化为 `page.evaluate` 直读 store + `aria-label` 文本匹配；spec 加 `Risks.R1` 长期登记。

---

## 8. Seed 数据策略

执行计划 Task 0 必做：cat `client/tests/e2e/fixtures/test-seed.sql`，按以下决策树：

```
  含 ≥2 张带 FK 的表（FOREIGN KEY 显式声明）？
    是 → 直接复用 test-seed.sql
    否 → 新建 client/tests/e2e/fixtures/er-seed.sql
         至少包含：
         - users(id PK, email UNIQUE)
         - orders(id PK, user_id FK → users.id, user_email VARCHAR)  -- user_email 是隐式关联候选
         - products(id PK, sku_code UNIQUE)
         - order_items(id PK, order_id FK → orders.id, product_id FK → products.id)
         spec setup 在 fixture 里调用 setupErSeed() 替代或追加 setupH2Connection()
```

**当前已知**：`test-seed.sql` 现有 `users(id, name, email, status)` + `orders(id, user_id, amount, status)`，**无 `FOREIGN KEY` 声明**。`getImportedKeys()` 不会返回 FK，Inspector 跑 Auto layout / Neighbor depth 时无 edge 可见。**执行计划默认走"新建 er-seed.sql"分支**。

**Bind Dialog dialect 过滤前置**（D8 / D10 专用）：当前 `setupH2Connection` 仅注入 1 个连接。D8 要"mysql dialect 只看到 mysql kind"必须 ≥ 2 个不同 kind 的连接。执行计划 Task 0 在 fixture 启动后追加：

```ts
// e2e fixture 在 setupH2Connection 之外追加 mock 连接（仅 D8/D10 用）
await apiContext.post('/api/connections', {
  data: { name: 'e2e-mock-pg', kind: 'postgresql', host: 'localhost', port: 5432, ... }
})
// kind 字段够 dialect 过滤；lastTestStatus 不需 ok（UI 不过滤这个）
```

不增加多 dialect 真实连接矩阵（仍为 §4.2 Out of scope），仅借用元数据过滤路径。若 fixture 注入失败 → D8/D10 `test.skip` 加 `console.warn`，与 R3 风险对齐。

---

## 9. BUG 登记策略（强匹配 CLAUDE.md BUG Tracking Gate）

### 9.1 写入触发

执行 AI 跑测发现偏差时**必须**：

1. 首次失败 → 登记 `docs/bugs/BUG-NNNN-<slug>.md`，状态 `open`，priority 按 §9.3 评级
2. 同步更新 `docs/bugs/index.md` Open BUGs / By Module 表
3. 把对应 test 改为 `test.fail` 或 `test.fixme` 并在注释里写 `// see docs/bugs/BUG-NNNN-...md` —— **不允许删 test 跳过**
4. 最终响应里**必须**报"本次发现 N 个 BUG，已登记到 …"，N=0 也要明说

### 9.2 读取触发

修任何 BUG 前**必须** grep `docs/bugs/` 关键字 / 模块名，确认不是已知 / wontfix / duplicate。

### 9.3 已知必登 BUG（设计阶段已识别）

- **BUG-A**：`onAddVirtualRelation` 是 noop（`client/src/features/stage/components/er-canvas/ErCanvas.tsx:223`），Inspector "Add virtual relation" toolbar 按钮点击无效
  - **Priority**：P1（功能缺失但 AI 仍可走 ui_patch 兜底路径）
  - **Modules**：er-canvas, er-inspector
  - **预登 slug**：`er-add-virtual-relation-noop`

- **BUG-B**（跑测时确认）：Inspector 端没有右键菜单代码，但 [`docs/product-specs/2026-04-29-er-graph-browsing-design.md`](./2026-04-29-er-graph-browsing-design.md) §1.2 提到 A2 "单表右键 View ER" 入口
  - **Priority**：P2（不阻塞主流程，但 spec 里写明的入口未落地）
  - **Modules**：er-inspector, connection-panel
  - **预登 slug**：`er-inspector-table-right-click-missing`

- **BUG-C**（潜在）：`data-payload-version` 当前未挂 DOM；执行 AI 实施 Task 0 前置时若产品代码不允许加 → 找替代方案并登 P3 BUG
  - **Priority**：P3
  - **Modules**：er-canvas, e2e-testability
  - **预登 slug**：`er-canvas-missing-payload-version-attr`

### 9.4 报告触发

执行计划 Task 7（housekeeping）必须在最终响应里明确："本次发现 N 个 BUG，已登记到 docs/bugs/…"。N=0 也要明说。

---

## 10. 风险

| ID | 风险 | Mitigation |
|---|---|---|
| R1 | UI selector 脆弱：ER 节点 / toolbar 按钮当前没全套 `data-testid`，POM 靠 `aria-label` + 文本匹配 | plan Task 0 把"加 `data-testid` 到 Toolbar / 节点 / 列行"列为前置；不允许加则退化 POM 用 `page.evaluate` 直读 store + Risks 长期登记 |
| R2 | dagre worker 异步：Auto layout / Fit view 写入异步 | `waitForPayloadVersion` 超时阈值默认 2s；30 表以上场景按需放宽到 5s |
| R3 | Bind Target Dialog 依赖真连接列表过滤：执行环境必须有至少一个 `lastTestStatus='ok'` 的对应 dialect 连接，否则 Bind Dialog 显示 empty | fixture `setupH2Connection`（实为通用 `setupConnection`）启动时检测，无满足 dialect 时 spec 内 `test.skip` 加 `console.warn` —— **不静默跳过** |
| R4 | Generate DDL 跨 dialect 输出不可用：若产品代码 `ErDdlGeneratorService` 对某 dialect 走 unsupported 分支 | D28/D29 测试兼容（输出可能含 `skippedOps`），断言用宽松匹配（含 `CREATE TABLE` + 可选 `skippedOps` 数组） |
| R5 | Fork to Designer 在 Plan A 环境返 `error.code === 'plan_b_only'` | I6 / E3 检测到此 error 直接 BUG 登记 + `test.fixme`，不算 spec 缺陷 |
| R6 | 刷新页面持久化依赖 Cross-Session Tabs `StagePersistenceCoordinator` | E4/E5 失败直接 BUG（持久化是 ER 模块本就该具备的能力） |
| R7 | CI 与本地 dev 上 ReactFlow 拖动行为可能因 `pointer-events` / 缩放系数不同导致拖距偏差 | `dragNode` 用 `mouse.move` 多步而非单步，避免 ReactFlow 把单步当点击；位置断言带 ±2px 容忍 |
| R8 | 入口 grep 出多个候选（Sidebar `[+]` / 连接面板 / 单表右键）但只有部分接通 | E1 spec 内逐一尝试每个候选；至少 1 个真通 → 走真路径；全部不通 → BUG-B + E2 fallback |
| R9 | ReactFlow handle DOM hit area 仅几像素，Playwright 拖拽精度在 CI headless 下脆弱 | ① 优先 `[data-handleid="${columnId}-source"]`（ReactFlow 自带）；② 兜底 `[data-er-column-handle]`（Task 0 testid 钩子）；③ `mouse.down → 6+ 步缓动 move → mouse.up`；④ 失败时 `dragConnect` 内部 fallback：直接 `page.evaluate` 调 `useErTabsStore.applyDesignerPatch` 注入 `relations/-`，spec 标注 D20 走 fallback 路径 + Risks 登记 |
| R10 | `notes` 字段无 UI 写入路径，E4 持久化断言不能依赖它 | §4.2 Out of scope 显式排除 notes；E4 修订断言仅含 `selection / positions / viewport / neighborDepth` |

---

## 11. 跑测顺序

1. 启动后端 + 前端 dev server（参考 `agents-batch*` fixture 现有约定）
2. **Task 0** 验证 fixture：
   - cat `test-seed.sql` 检查 FK
   - 不含 → 创建 `er-seed.sql` + 替换 fixture 引用
   - 检查 `setupH2Connection` 实际复用的连接 `lastTestStatus`
   - 追加 mock postgresql 连接（D8/D10 dialect 过滤前置，见 §8）
   - **重新 grep** ER 模块及其依赖的 `services/*` 是否有新引入的 XHR / Tauri HTTP 路径（INV-1 假设的最后一道防线）
3. **Task 0 产品代码 prep**（1-3 行）：加 `data-payload-version` / `data-er-table-id` / `data-er-column-handle` / `data-testid="er-toolbar-*"` 钩子。**若不允许**：跳过此步，POM 退化方案 + Risks.R1 登记
4. spec 1（Inspector，11 tests）→ spec 2（Designer，30 tests）→ spec 3（入口 + 持久化，6 tests）顺序跑
5. 全绿 / 全部 BUG 登完 → housekeeping

### 11.1 Timeout 预算

- 单条 test 默认 `test.setTimeout(30_000)`（30s）
- 涉及 dagre worker 异步的 test（D2 / D7 / I2 / I3 / I9 — Auto layout / Fit view / Zoom）放宽到 60s；用 `test.slow()` 标注
- 涉及刷新页面 + rehydrate 的 E4 / E5 放宽到 60s
- spec 文件级总超时 `test.describe.configure({ timeout: 600_000 })`（10 分钟）；3 个 spec 分别 11 / 30 / 6 tests 串行总耗时不应突破
- `waitForPayloadVersion` 默认 2s（INV-5 阈值）；30 表以上场景按需放宽到 5s（仅 fixture 主动构造大 schema 时）

---

## 12. Housekeeping

- 计划文件归 [`docs/exec-plans/index.md`](../exec-plans/index.md) Active 区；执行结束移 Completed
- BUG `docs/bugs/index.md` 同步更新
- 如执行过程中识别新约束（如 selector 协议升级、ER 节点 testid 命名规则），回写到 [`client/DESIGN.md`](../../client/DESIGN.md) 或本设计 §7
- 产品代码若加了 testid，对应 i18n key 不动，避免双语 commit 冲突
- 本设计登记到 [`docs/product-specs/index.md`](./index.md) §8

---

## 13. 不做什么

- 不引入 `mcp-tool-recorder` / `adapter-client` MCP 双轨 fixture（已在 `agents-batch5` 用过，本计划纯 UI-only 不需要）
- 不修改任何 ER 产品代码逻辑（仅允许 §10 R1 Mitigation 描述的纯 testid 钩子改动）
- 不重新跑已 closed `agents-batch5-er-tabs.spec.ts` 已覆盖的 AI 路由 / MCP 契约 case
- 不写 ER 性能 / 大 schema / 100 表上限 / 网络抖动 / OpenCode 模型差异 case
