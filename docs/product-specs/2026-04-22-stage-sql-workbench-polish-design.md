# Stage SQL Workbench Polish Design

- **日期**：2026-04-22
- **状态**：draft
- **前置**：
  - [Stage SQL Workbench Rebuild Design](./2026-04-21-stage-sql-workbench-rebuild-design.md)（本 spec 在其落地结果上继续打磨）
  - [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md)
  - [Session Data Context & AI Data Source Management Design](./2026-04-21-session-data-context-and-ai-datasource-management-design.md)
  - [PostgreSQL SQL Splitter Design](./2026-04-22-postgres-sql-splitter-design.md)（并行工作流，互不阻塞）

## 1. 背景与目标

### 1.1 背景

`2026-04-21-stage-sql-workbench-rebuild` 已经把 Stage 的 SQL 主线迁到 Monaco + 多结果契约，**功能可用**。但人工验证（2026-04-22 截图）暴露两类问题：

1. **视觉与 IDE 级 SQL 工具（open-db-studio、DBeaver、DataGrip）差距明显**：
   - 工具栏只有 Run + Badge，没有 Format / Limit / Context chip / Save
   - Tabs 样式扁平，缺 underline accent 的状态区分
   - Monaco 仅有主题和 `Ctrl+Enter`，缺补全、折叠、多光标、breadcrumb、当前语句高亮
   - 缺底部 status bar（行数 / 耗时 / 光标位置）
2. **Stage 整体结构浪费空间**：
   - 左侧资源栏占 320px 但树节点极少；主要数据源切换已由 Composer / AI action 负责
   - 工作台列跟随左栏，编辑器实际可用宽度被挤到 ~250px（典型 1280px 窗口下）

### 1.2 本轮目标

做一次**纯前端打磨**，在既有多结果契约 + `useSqlWorkbenchStore` 基础上：

1. 去除左侧资源栏整套渲染，工作台铺满主区
2. 在最右侧新增 28px **Activity Rail**（类 VS Code），点击图标展开 280px 面板，包含 **Schema / History / Outline / AI Assist** 4 个面板
3. 工具栏升级到 IDE 级：Run / Cancel / Format / Limit / Context chip / Save / overflow
4. Monaco 打磨到 L1 + L2 + L3（语法 · chrome · 多光标/查找）
5. 底部加全局 Status Bar
6. Tabs 统一为 **underline-only** 扁平风
7. 引入**分层上下文**：tab override + session 继承 + 程序化注入入口，与现有 AI 数据源链路兼容

### 1.3 非目标

见 §10。关键非目标：**零后端 schema 变更**；EXPLAIN / Inspector / Snippets / 可拖拽 resize / History 持久化 / 后端 SQL cancel endpoint 全部不在本轮。

## 2. 总体布局

### 2.1 新结构

```
┌─ Stage ─────────────────────────────────────────────────────────────────────────────┐
│ ● Stage                                                                 [ □ ]  [ × ] │
├─────────────────────────────────────────────────────────────────────────────────┬───┤
│ tab1.sql  │ tab2.sql  │ ● Untitled-3                              [＋]         │ 🗄 │
│ ─────────   ════════════                                                         │ 🕐 │
├─────────────────────────────────────────────────────────────────────────────────┤ 📄 │
│ [mysql-prod ▾] / [public ▾] [继承] │ [▸Run ⌘⏎] [⎯Format] [Limit:100 ▾] [💾][⋯] │ ✨ │
├─────────────────────────────────────────────────────────────────────────────────┤   │
│ mysql-prod › public › Ln 3 · SELECT                                            │   │
├─────────────────────────────────────────────────────────────────────────────────┤   │
│ (Monaco SQL editor — completion / breadcrumb / folding / multi-cursor / find)   │   │
│                                                                                 │   │
├─────────────────────────────────────────────────────────────────────────────────┤   │
│ [▸ result 1 (120r·34ms)] [✚ result 2 (DML)] [⚠ result 3]                        │   │
│   ─────────────                                                                  │   │
├─────────────────────────────────────────────────────────────────────────────────┤   │
│ (result grid / dml summary / error panel)                                       │   │
├─────────────────────────────────────────────────────────────────────────────────┴───┤
│ ✓ 120 rows · 34 ms · mysql-prod / public        SQL · Ln 3, Col 18 · UTF-8 · LF     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 去掉的内容

| 移除点 | 处理 |
|---|---|
| `StageSidebar` 渲染分支 | 从 `StageWindow` 结构中摘除，相关 props 不再下发 |
| `StageResourceBrowser` 在 sidebar 的位置 | 能力迁到右侧 `SchemaPanel`（一次性，本轮完成） |
| `StageToolRow` | 本体废弃；其 AI 入口 / SQL 入口按钮不再保留（真正的入口是 Composer 和 openOrFocusStageToolTab） |
| `sidebarCollapsedBySession` / `sidebarSelectionBySession` / `resourceTreeExpandedBySession` 读取点 | 组件中删除读取，state 字段保留为 dead field（下轮 cleanup 统一删） |

### 2.3 保留并改造

| 组件 | 改造 |
|---|---|
| `StageWindow` | 外层 flex 从 `row(Sidebar, Workbench)` 改为 `row(Workbench, ActivityRail)`；Rail 永远靠右 |
| `StageTabContent` | 根 div 保持 `w-full min-w-0 flex-1`（已修） |
| `SqlWorkbenchTab` | 拆出 header → toolbar + breadcrumb + editor + result tabs + result panel + status bar |

### 2.4 小屏退化

1280px 窗口下 workbench 实际宽度 ≈ 1280 - 左栏(chat/sidebar) - 28 - (展开时 280)。当 workbench 实际宽度 < 900px 时：
- Rail panel 自动折叠，Rail 本体仍显示（28px）
- 用户可手动展开，UI 不阻止；但展开后 workbench 宽度可能塌缩
- 工具栏溢出时 `Limit` / `Save` 自动进 overflow `⋯` 菜单

## 3. 右侧 Activity Rail + 4 面板

### 3.1 Rail 本体

- 宽度 28px，永远显示
- 4 个图标竖排（自上而下）：`🗄 Schema` · `🕐 History` · `📄 Outline` · `✨ AI`
- 被激活图标底部或左侧 2px accent 线（与 tabs 的 underline 语言一致）
- 状态存储：`useStageStore.activeRailPanelBySession: Map<sid, RailPanel | null>`
  - `RailPanel = 'schema' | 'history' | 'outline' | 'ai'`
  - 新 session 默认 `null`（折叠）
- 交互：点当前激活图标 = 折叠；点其他图标 = 切换

### 3.2 Panel Shell

- 宽度 280px 固定（本轮不做可拖拽 resize）
- 一次只展开一个面板（本轮不做竖向堆叠）
- 顶部 36px header：面板标题 + 可选搜索框 + `×` 关闭按钮（= 折叠）
- 通用容器 `rail-panel-shell.tsx`，body 是 slot

### 3.3 🗄 Schema 面板

**数据源**：复用 `connection-store + /api/resources`（与旧资源栏完全一致，换位置即可）。

**树形结构**：`connection → database → schema → table → column`，多数据源可同屏展开多个 connection。

**交互**：

| 操作 | 行为 |
|---|---|
| 单击 connection / database / schema 节点 | 写当前 active tab 的 `override`（`source: 'user_schema_panel'`） |
| 单击 table 节点 | 展开子节点显示列；不改 override |
| 双击 table | 插入 `"schema"."table"` 到当前光标 |
| 双击 column | 插入列名到当前光标 |
| 右键菜单 | `预览前 100 行` · `生成 SELECT *` · `复制全限定名` · `在新 Tab 打开` |
| 搜索框 | 树过滤（client-side fuzzy match on table/column name） |

**相对旧资源栏差异**：不再内嵌 `SQL 编辑器` / `ER 图` 动作条（入口上移到 Stage 顶部 workbench 工具栏与 `openOrFocusStageToolTab`）。

### 3.4 🕐 History 面板

**存储**：`useSqlWorkbenchStore.tabsById[tabId].history: HistoryEntry[]`（per-tab 内存）。

```ts
type HistoryEntry = {
  id: string          // uuid
  at: number          // timestamp
  sql: string
  status: 'ok' | 'error' | 'risk_blocked'
  resultCount?: number
  elapsedMs?: number
  resultKinds?: ResultKind[]   // ['result_set', 'dml_summary', 'error']
  errorSummary?: string
}
```

**容量**：最多 50 条，FIFO。

**交互**：

| 操作 | 行为 |
|---|---|
| 单击一条历史 | SQL **追加**到当前光标位置（不替换），插入的行闪烁选中 2s |
| Hover 一条历史 | 显示 `回放` / `复制 SQL` / `Pin`（Pin 本轮 disabled） |
| 点击 `清空` | 清当前 tab 的 history |

**持久化**：本轮纯内存，关 tab 即丢。跨 tab / 跨 session 持久化见 §10。

### 3.5 📄 Outline 面板

**解析**：纯前端 `utils/parse-sql-outline.ts`，input SQL → `Statement[]`：

```ts
type Statement = {
  line: number                   // 起始行（1-based）
  kind: 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'CREATE' | 'ALTER'
      | 'DROP' | 'TRUNCATE' | 'WITH' | 'EXPLAIN' | 'OTHER'
  summary: string                // 如 "SELECT orders"，取主对象名
  highRiskHint: boolean          // 本地规则命中
}
```

**切分策略**：按 `;` 粗切 + 跳过字符串 / 注释内的 `;`；第一轮不解决 PG dollar-quote（与 `2026-04-22-postgres-sql-splitter-design.md` 共享问题，不同层）。

**本地 highRiskHint 规则**（非权威，仅前端提示）：
- `DROP` / `TRUNCATE` / `ALTER` → true
- 无 `WHERE` 子句的 `DELETE` / `UPDATE` → true

**交互**：点击 → Monaco `revealLineNearTop + setPosition(Ln, 1)`。

**刷新**：`editor.onDidChangeModelContent` → 300ms debounce 重解析。

### 3.6 ✨ AI Assist 面板

**独立 session 模型**：

- 为每个 workbench tab 维护独立 OpenCode session：`sessionId = 'stage-ai-' + tabId`（或后端返回的真实 OpenCode sid 由前端 namespace 绑定）
- 发 `/api/channel` 时用这个独立 session 发消息，**不**污染主 Composer 会话
- 关闭 workbench tab 时清理（调用现有 `DELETE /session/:id` cascade 链路）

**Prompt 注入**：用户消息前置拼接：

```
[上下文] 当前 SQL:
```sql
{editor content}
```
{lastError ? `[最近错误] ${lastError}` : ''}
{lastRun ? `[最近执行] ${lastRun.rows} 行 / ${lastRun.ms}ms` : ''}
```

**快捷动作按钮**（不打字就能触发）：
- `Explain` → 默认 prompt `解释这段 SQL`
- `Optimize` → `分析性能并给出优化建议`
- `Fix error` → 仅 `lastError` 非空时显示

**渲染**：流式 markdown（复用 `PacedMarkdown` + 代码块组件）。

**插入到编辑器**：
- 若响应含 ```sql fenced 块 → 提取
- 显示确认对话框：`插入到光标位置 / 替换选区 / 取消`
- 默认 `插入到光标位置`

**后端兼容性风险**（见 §9）：独立 session 是否与 `SessionService.create` 的空白会话复用规则冲突，需要预先验证。

## 4. 分层上下文

### 4.1 优先级

```
effectiveContext(tab) =
    tab.override          // 最高：工具栏 / Schema panel / AI action / payload 写入
 ?? sessionContext         // 中：Composer !use · 数据源下拉 · OpenCode choose_connection
 ?? workspaceGlobal        // 低：useConnectionStore.activeConnectionId
```

### 4.2 Override 数据结构

```ts
type TabContextOverride = {
  connectionId: string
  connectionName?: string | null
  database?: string | null
  schema?: string | null
  source: 'user_toolbar' | 'user_schema_panel' | 'ai_action' | 'api' | 'open_payload'
  setAt: number
} | null
```

### 4.3 Store actions

`useSqlWorkbenchStore` 新增：

```ts
setTabContext(tabId: string, ctx: Omit<TabContextOverride, 'setAt'>): void
resetTabContext(tabId: string): void   // 回到"跟随 session"
```

### 4.4 `useEffectiveContext(tab)` 合并规则

```ts
const override = useSqlWorkbenchStore((s) => s.tabsById[tabId]?.override ?? null)
const sessionCtx = useSessionDataContext(tab.originSessionId).context
const globalConn = useConnectionStore((s) => s.activeConnectionId)
const resolved = resolveTabDataContext(/* 现有逻辑：session 继承 + global 兜底 */)

const effective = override ?? resolved
const isOverride = override !== null
```

### 4.5 工具栏 Context Chip

**UI**：

```
[mysql-prod ▾] / [public ▾]  [继承 session]                 ← 继承态（灰 badge，无 reset 按钮）
[mysql-prod ▾] / [public ▾]  [Tab 覆盖]  [↺ 重置跟随]       ← override 态（加重 badge + reset）
```

**Dropdown 源**：
- Connection dropdown：`useConnectionStore.connections`
- Database dropdown：依赖已选 connection 的 databases 列表
- Schema dropdown：依赖已选 database 的 schemas 列表；未加载则 lazy fetch `/api/resources`

**选中行为**：
- `setTabContext(tabId, { connectionId, database, schema, source: 'user_toolbar' })`
- `reset` 按钮 → `resetTabContext(tabId)`

### 4.6 程序化入口

**A. 打开 tab 时预设 context**

扩展 `OpenSqlWorkbenchPayload`：

```ts
type OpenSqlWorkbenchPayload = {
  initialSql?: string
  source?: 'manual' | 'resource' | 'direct_sql' | 'ai_generated'
  autoRun?: boolean
  contextOverride?: {
    connectionId: string
    database?: string | null
    schema?: string | null
  }
}
```

Tab 首次挂载时若 `contextOverride` 非空 → 在 `ensureTab` 的初始化里直接 `setTabContext(..., source: 'open_payload')`。

**B. 已存在 tab 的精准注入（本轮延期）**

定义新 Action 协议（本轮仅定义，不落 handler）：

```ts
ClientAction: 'stage.set_tab_data_context'
payload: { tabId: string, connectionId: string, database?: string, schema?: string }
```

接线到 `stage-ui-object-registry` 下轮做；这一轮的 AI 场景可以**全部走 A 路径**（打开新 tab 或找到 tab 后用带 payload 的 `focusTab` 变体）。

**C. AI 场景典型触发**

用户在 Composer 说："给我看 pg-dev.public.orders"，AI 有两种选择：

1. 走 session 继承路径：`opencode.action('workspace.choose_connection', ...)` → `ui_exec(stage, open_sql_workbench, { initialSql: 'SELECT * FROM public.orders LIMIT 100' })`。session context 被改写。
2. 走 tab override 路径（不污染 session）：`ui_exec(stage, open_sql_workbench, { initialSql: '...', contextOverride: { connectionId: 'pg-dev', ... } })`。session context 不变。

两条路径共存，由 AI agent 根据 prompt 判断。

## 5. 工具栏 · Monaco · Status Bar

### 5.1 工具栏（方案 B）

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [▸Run ⌘⏎] [■Cancel] [⎯Format] [Limit:100 ▾]  │  [context chip]  [💾] [⋯]    │
└──────────────────────────────────────────────────────────────────────────────┘
```

**按钮定义**：

| 控件 | 显示条件 | 行为 |
|---|---|---|
| Run | 总是；`canRun=false` 时 disabled | 有选区 → Run Selection（只执行选区 SQL）；无选区 → Run All |
| Cancel | 仅 `executeStatus==='running'` 时显示（替换 Run 位置，保持稳定） | `AbortController.abort()` 前端 fetch；后端 JDBC cancel 本轮不做（见 §10） |
| Format | 总是 | `sql-formatter` npm 包，方言跟随 context |
| Limit | 仅当当前 SQL 识别为 SELECT 类时 enabled | 枚举 `10 / 100 / 1000 / ∞`；值存 `tabState.limit` |
| Context chip | 总是 | 见 §4.5 |
| Save | 总是；dirty 时高亮 | 持久化 SQL 文本到 `tabState.savedSqlText` + LocalStorage draft（per tabId） |
| `⋯` overflow | 总是 | `Save As · Explain · History · 导出 CSV · 清空编辑器` |

**Dirty 判定**：`tabState.sqlText !== tabState.savedSqlText`。`ensureTab` 初始化 `savedSqlText = initialSql`。

**Limit 执行语义**：
- 若 SQL 本身已含 `LIMIT` → Limit 控件降级为显示当前 limit，不修改 SQL
- 若 SQL 无 `LIMIT` 且 `tabState.limit != null && != Infinity` → 执行前在尾部追加 `LIMIT N`（客户端注入，而非后端 API 参数，以与 `/api/sql/execute` 现有契约兼容）
- `∞` (null) → 不注入

### 5.2 Monaco L1 语法

**Completion provider**：

```ts
monaco.languages.registerCompletionItemProvider('sql', {
  triggerCharacters: ['.', ' ', '\n'],
  provideCompletionItems(model, position) {
    // 1. 上下文判断：FROM / JOIN / ON / WHERE / SELECT ...
    // 2. "<trigger>." 形式：resolve trigger 为 table alias / schema / database，提供下一级补全
    // 3. 否则：mix 方言关键字 + 当前 context 下的 tables + columns
  }
})
```

**数据源**：
- 方言关键字：维护在 `client/src/features/stage/sql-dialects/` 下的小字典文件（MySQL / Postgres / H2 各一个 JSON）
- 表 / 列：复用 `connection-store` 的 resources cache；首次在 Schema 面板展开该 connection 时触发一次性预热（`preloadResources(connectionId)`）

**Hover provider**：光标悬停表名/列名时，显示类型 / 注释（若 metadata 可用）。

### 5.3 Monaco L2 Chrome

**Breadcrumb** 组件 `sql-editor-breadcrumb.tsx`：

```tsx
<div className="breadcrumb">
  <span>{connection}</span> › <span>{database}</span> › <span>{schema}</span>
  <span className="sep">·</span>
  <span>Ln {cursor.line}</span>
  <span className="sep">·</span>
  <span>{currentStatement.kind}</span>  {/* 光标所在语句类型 */}
</div>
```

挂在 toolbar 和 editor 之间，高度 24px，字号 11px。

**Gutter decorations**：语句起点（`;` 后第一个非空字符所在行）加小 bullet `•`，颜色 `--muted-foreground`。

**Folding**：
- 使用 Monaco 自带 folding
- 额外注册 SQL 专用 folding provider：识别 `(` `)` `CASE ... END` `BEGIN ... END` 配对

**当前语句软高亮**：
- 监听 `onDidChangeCursorPosition`
- 计算光标所在 statement 的起止行
- 用 `deltaDecorations` 加 `{ background: 'var(--muted) / 30', isWholeLine: true }`

**Format on save**：配置开关 `editor.formatOnSave: boolean`（本轮默认 `false`，存在 `useThemeStore` 偏好位或新 `useEditorPreferencesStore`）。

### 5.4 Monaco L3 效率

| 特性 | 启用方式 |
|---|---|
| Multi-cursor | Monaco 默认启用，检查 options 未禁用 |
| Find / Replace | Monaco 默认启用，`Ctrl+F` / `Ctrl+H` 自带 widget |
| Bracket matching | `bracketPairColorization.enabled = true` |
| Auto closing | `autoClosingBrackets: 'always'`, `autoClosingQuotes: 'always'` |
| Auto indent | `autoIndent: 'full'` |

### 5.5 L4 Status Bar

组件 `sql-workbench-status-bar.tsx`，固定在 `SqlWorkbenchTab` 最底部（result panel 下方），高度 24px。

```
[ left segment                    ]                              [ right segment           ]
 ✓ 120 rows · 34 ms · mysql-prod / public                        SQL · Ln 3, Col 18 · UTF-8 · LF
```

**左段状态映射**：

| workbench 状态 | 左段 |
|---|---|
| idle | `（无最近执行）` 灰字 |
| running | `⟳ Running…  2.3s  [Esc cancel]`（setInterval 500ms 刷新计时） |
| success + 活跃 result_set | `✓ {rows} rows · {ms} ms · {connectionName} / {schema}` |
| success + 活跃 dml_summary | `✓ {kind} affected={n} · {ms} ms` |
| error | `⚠ {errorMessage 的首行，截断 80 字符}` 红字 |
| risk_blocked | `⚠ 高风险操作已阻断：{riskReason 前 60 字}` 红字 |

**右段**：永远 `SQL · Ln {cursor.line}, Col {cursor.column} · UTF-8 · LF`。

**cursor 更新**：`SqlMonacoEditor` 通过 `onDidChangeCursorPosition` → 回调到 `SqlWorkbenchTab` → `setCursor(tabId, line, col)` → `useSqlWorkbenchStore.tabsById[tabId].cursor`。

### 5.6 快捷键

| 快捷键 | 作用 |
|---|---|
| ⌘/Ctrl + Enter | Run（有选区时 Run Selection） |
| ⌘/Ctrl + Shift + Enter | Run All（忽略选区） |
| Esc | Cancel（仅 running 时） |
| ⌘/Ctrl + S | Save（formatOnSave 若启用则先 format 再 save） |
| ⌘/Ctrl + Shift + F | Format |
| ⌘/Ctrl + / | Toggle line comment（Monaco 自带） |
| ⌘/Ctrl + D | Add cursor to next occurrence（Monaco 自带） |
| ⌘/Ctrl + F / H | Find / Replace（Monaco 自带） |

## 6. Tab 风格（方案 D）

统一为 **underline-only 扁平风**：

- 非 active：文本 `text-muted-foreground`，无边框，hover 时 `bg-muted/50`
- Active：文本 `text-foreground`，底部 2px accent 线 (`bg-foreground/80`)；错误 tab 用 `bg-destructive`
- 外层编辑器 TabBar 与下方 result tabs **使用同一视觉语言**
- 关闭按钮：hover 时显示，`×` 图标
- dirty 指示：tab title 左侧 `●` 圆点（已保存则圆点隐藏）
- 超出宽度时横向滚动（不截断文本，除非 title 过长用 max-width + truncate）

影响组件：
- `stage-tab-bar.tsx`（现有，改样式）
- `sql-result-tabs.tsx`（现有，改样式）

## 7. 数据模型 · 文件变更

### 7.1 Store 改造

**`client/src/stores/stage-store.ts`**：
- 新增 state：`activeRailPanelBySession: Map<string, RailPanel | null>`
- 新增 actions：`setActiveRailPanel(sid, panel)` · `toggleRailPanel(sid, panel)`
- 废弃读取（字段保留）：`sidebarCollapsedBySession` · `sidebarSelectionBySession` · `resourceTreeExpandedBySession`

**`client/src/features/stage/stores/sql-workbench-store.ts`**：
- 每个 tab state 新增字段：
  - `override: TabContextOverride`
  - `history: HistoryEntry[]`
  - `savedSqlText: string`
  - `limit: 10 | 100 | 1000 | null`（null = ∞）
  - `cursor: { line: number; column: number }`
- 新增 actions：
  - `setTabContext(tabId, ctx)` / `resetTabContext(tabId)`
  - `appendHistoryEntry(tabId, entry)` / `clearHistory(tabId)`
  - `markSaved(tabId)`
  - `setLimit(tabId, limit)`
  - `setCursor(tabId, line, col)`

**（可选）AI Assist store**：
若复用主 channel store 的 per-session 状态（namespace 以 `sessionId = 'stage-ai-' + tabId`），可省略单独 store；否则新建 `stage-ai-assist-store.ts` 做 per-tab messages + streaming。**默认先复用**，验证不冲突再决定。

### 7.2 新建文件

```
client/src/features/stage/
├── components/activity-rail/
│   ├── stage-activity-rail.tsx
│   ├── rail-panel-shell.tsx
│   ├── schema-panel.tsx
│   ├── history-panel.tsx
│   ├── outline-panel.tsx
│   └── ai-assist-panel.tsx
├── components/sql-editor-toolbar.tsx
├── components/sql-editor-breadcrumb.tsx
├── components/sql-workbench-status-bar.tsx
├── components/sql-context-chip.tsx
├── components/sql-limit-select.tsx
├── utils/parse-sql-outline.ts
└── sql-dialects/
    ├── mysql-keywords.json
    ├── postgres-keywords.json
    └── h2-keywords.json
```

### 7.3 改动文件

```
client/src/features/stage/components/stage-window.tsx
  — 移除 StageSidebar 渲染分支
  — 结构：row(workbenchSection, StageActivityRail)
  — 不再下发 sidebarCollapsed / resourceExpanded / sidebarSelection 相关 props

client/src/features/stage/components/sql-workbench-tab.tsx
  — header 替换为 sql-editor-toolbar
  — 工具栏下方挂 sql-editor-breadcrumb
  — 底部挂 sql-workbench-status-bar
  — effectiveContext 改用 override-first 解析（§4.4）
  — execute 完成后 appendHistoryEntry
  — 接入 cursor / limit 回写 store
  — 挂载 imperative ref 给 AI 面板 / Schema panel 双击调用 insertAtCursor

client/src/features/stage/components/sql-monaco-editor.tsx
  — 注册 completion / hover providers（§5.2）
  — 注册 folding provider / current-statement decoration
  — 启用 bracketPairColorization + autoClosing
  — 暴露 onCursorChange(line, col) props
  — 暴露 insertAtCursor(text) imperative handle

client/src/features/stage/components/stage-tab-bar.tsx
  — 切换到 underline-only 样式（§6）

client/src/features/stage/components/sql-result-tabs.tsx
  — 与 stage-tab-bar 视觉统一

client/src/stores/stage-store.ts
  — 新增 rail state + actions

client/src/features/stage/stores/sql-workbench-store.ts
  — 新增字段 + actions（§7.1）
```

### 7.4 废弃为 shim

以下文件保留为导出空组件或 `return null`，**不**物理删除（保证 UI Object Registry / import 路径稳定；下轮 cleanup 统一清理）：

```
client/src/features/stage/components/stage-sidebar.tsx
client/src/features/stage/components/stage-resource-browser.tsx
client/src/features/stage/components/stage-tool-row.tsx
client/src/features/stage/components/sql-editor-header.tsx
```

### 7.5 后端改动

**本轮零后端改动**：

- 无 schema / Flyway migration
- 无新 endpoint（EXPLAIN、SQL cancel、saved queries 全部延期）
- `stage.set_tab_data_context` Action handler 本轮不落地（AI 用 `open_sql_workbench.contextOverride` payload 即可）

### 7.6 新增 npm 依赖

- `sql-formatter`（MIT）— Format 按钮 / formatOnSave

不引 `monaco-sql-languages`：补全 / 方言维持手工字典以控成本。

## 8. 测试矩阵

### 8.1 Vitest 单元

```
stores/sql-workbench-store.test.ts            — 扩展：setTabContext / resetTabContext / history 增删上限 50 / dirty 判定 / limit
utils/parse-sql-outline.test.ts               — 新：多语句切分 · 字符串/注释内的 ; · highRiskHint 命中
components/sql-editor-toolbar.test.tsx        — 新：各按钮 disabled / visible 态 · limit 选择 · context chip dropdown · overflow 菜单
components/sql-context-chip.test.tsx          — 新：继承 vs override badge 切换 · reset 按钮出现条件
components/sql-workbench-status-bar.test.tsx  — 新：running 计时 · success result_set · success dml · error · risk_blocked
components/sql-editor-breadcrumb.test.tsx     — 新：connection/database/schema/line/kind 组装
components/activity-rail/stage-activity-rail.test.tsx — 新：icon 点击 toggle · 激活高亮 · 同一图标二次点击折叠
components/activity-rail/schema-panel.test.tsx        — 新：单击 override · 双击 insert · 右键菜单项
components/activity-rail/history-panel.test.tsx       — 新：追加条目 / 回填 SQL / 清空
components/activity-rail/outline-panel.test.tsx       — 新：多语句解析 + 点击跳光标（mock Monaco api）
components/activity-rail/ai-assist-panel.test.tsx     — 新：快捷动作按钮 / 独立 session 发送（mock /api/channel）/ 插入到编辑器确认对话框
components/sql-workbench-tab.test.tsx         — 扩展：override-first 生效 · contextOverride payload 落 override · history append · cursor 回写 · limit 注入
components/stage-window.test.tsx              — 扩展：不再渲染 StageSidebar / StageResourceBrowser / StageToolRow · Rail 渲染 · 激活面板切换
components/stage-tab-bar.test.tsx             — 扩展：underline 样式快照 · dirty 指示
components/sql-result-tabs.test.tsx           — 扩展：underline 样式
```

### 8.2 手工 e2e 烟测

- [ ] 切连接三路：工具栏 chip dropdown / Schema 面板单击 / Composer `!use` → 三种路径都能更新 effective context 且 badge 态正确
- [ ] `open_sql_workbench` 带 `contextOverride` payload 打开 tab，session context 不被污染
- [ ] Run / Cancel / Format / Limit 四件套完整链路
- [ ] Monaco：补全（表名 / 列名）· breadcrumb 实时 · 折叠 · 多光标 · Find / Replace
- [ ] Status bar：idle / running 计时 / success / error / risk_blocked 五态切换
- [ ] Rail：4 面板切换 · 同图标折叠 · session 级记忆
- [ ] AI Assist：独立 session 发送 · 流式渲染 · 插入到编辑器（sql fenced 块提取正确）· 关 tab 清理 session
- [ ] 小屏（1280px 以下）：工具栏 overflow 表现 · Rail panel 展开后编辑器不塌缩
- [ ] 废弃 shim：`StageSidebar` 等组件被引用处不报错

### 8.3 性能

- Monaco 补全在 1000+ 表 schema 下响应 < 100ms（要求 resources cache 常驻内存，不每次 fetch）
- Outline 解析 10000 行 SQL 不卡（debounce + 纯同步扫描）
- Rail 面板切换 < 16ms（单帧）

## 9. 主要风险与对策

### R1. Monaco completion 数据源的预热时机

**问题**：当前 `connection-store` 的 resources 是按树节点展开懒加载；但 SQL 补全要求用户输入表名时就能联想，不能等用户去 Schema 面板点展开。

**对策**：
- 首次在 Schema 面板展开某 connection → 触发 `preloadResources(connectionId)` 主动把该连接下所有 database / schema / table / column metadata 拉全，缓存到 store
- 当 `effectiveContext.connectionId` 切换且该 connection 还未预热 → 也触发预热
- 大库（> 5000 tables）兜底：`preloadResources` 支持 `limit` 参数，超出阈值仅拉 top-N（按 rowcount 或字母序）
- **本轮先不做大库兜底**，观察真实 metadata 量级后在下轮加（tech-debt 登记）

### R2. AI Assist 独立 session 的后端兼容性

**问题**：前端直接用构造的 `sessionId = 'stage-ai-' + tabId` 发 `/api/channel` 可能违反：
- `SessionService.create` 的空白会话复用规则（已加 `synchronized` + `reusedEmpty`）
- session 必须先被后端创建（客户端不能凭空造 sid）

**对策**：
- 实际实现时不用前端拼 sid，而是：`POST /api/sessions`（允许多个，与主会话同一 flow）创建 session，后端返回真 OpenCode sid；前端在 `sql-workbench-store.tabsById[tabId].aiSessionId` 记录映射
- 关 tab 时调用 `DELETE /api/sessions/:id`（现有级联）
- 主 Composer 的 session 列表 UI 需要过滤掉这些 AI Assist session（判据：session 元数据加 `scope: 'stage-ai'` 标记，或 title 前缀 `_stage-ai_`）
- **降级**：若 session 列表过滤改造成本高 → 改为"AI Assist 面板直接复用主 session"，把 AI Assist 对话写进主聊天历史。用户可见，但不隔离。落地时二选一。

### R3. 小屏 Rail 展开塌缩

**问题**：1280px 窗口 + 左栏 320px + Rail 展开 280px → workbench 实际宽度可能 < 700px，编辑器再次塌缩。

**对策**：
- 在 `StageWindow` 外层容器加 `ResizeObserver`，宽度 < 900px 时 rail 自动折叠（仅 icon）
- 用户点开 rail icon 不强制阻止，但给 tooltip `"屏幕较窄，展开将挤占编辑器"`
- 本轮不做用户偏好记忆"总是折叠"，下轮加

### R4. `sql-formatter` 方言覆盖

**问题**：`sql-formatter` 支持方言有限（`sql / mysql / postgresql / sqlite / bigquery / ...`），我们的 context 可能是 `h2`。

**对策**：
- Context 方言映射表：`h2 → sql`（通用模式）；`mysql → mysql`；`postgres → postgresql`
- 映射未命中 → fallback `sql`
- Format 后若 SQL 被破坏（罕见）→ catch error 且不替换内容，toast `格式化失败，请检查 SQL 语法`

### R5. 废弃 shim 的 dead code

**问题**：`stage-sidebar.tsx` 等文件保留为 `return null` 后，TypeScript 不会报错但 lint 可能警告 unused imports / props。

**对策**：
- Shim 内只保留最小 export（`export function StageSidebar() { return null }`）
- Props 类型保留但 body 不用 → 在 shim 文件头部加 `// @deprecated — 见 docs/exec-plans/<cleanup plan>`
- 下一轮单独的 "stage UI cleanup" plan 统一删

## 10. 非目标与延期项

| 项 | 理由 | 去向 |
|---|---|---|
| 可拖拽 resize Rail 宽度 | 固定 280px 先把味道做对 | tech-debt P2 |
| 多面板竖向堆叠（Schema + History 同屏） | 80% 场景一次一个足够 | 需求出现再议 |
| Snippets / Pin / Saved Queries | 需后端 saved-queries 表 + CRUD | 新 spec 主题 |
| History 跨 tab / 跨 session 持久化 | 本轮仅内存 per-tab | tech-debt P2 |
| 后端 SQL cancel endpoint | 本轮 Cancel 只 abort 前端 fetch | tech-debt P1 |
| EXPLAIN / Query Plan 面板 | Rail 第 5 个槽位暂不上 | 新 spec 主题 |
| Inspector 面板 | Hover / 右键已能看列类型 | 新 spec 主题 |
| 列排序 / 列筛选 / 列宽拖拽 on result grid | 本轮只做视觉对齐 + 导出 CSV（overflow 入口） | tech-debt P2 |
| `Format on save` 默认开 | 避免意外改用户 SQL | 偏好中心下轮 |
| `stage-sidebar.tsx` 等物理删除 | 避免其他集成点误爆 | 独立 cleanup plan |
| PG dollar-quote splitter | 已有独立 spec 跟进 | `2026-04-22-postgres-sql-splitter-design.md` |
| `stage.set_tab_data_context` Action 后端 handler | 前端本轮只用 open payload | 接 Composer Data Source Picker 主线 |
| 后端 SELECT-type 识别（for Limit 自动启用） | 本轮纯前端判断首个非注释关键字 | tech-debt P2 |

## 11. 自检

- [x] Placeholder 扫描：无 TODO / TBD
- [x] 内部一致性：§4 override-first 与 §5.1 context chip 表现、§3.3 Schema panel 单击行为三处一致
- [x] 范围可控：全前端 + 一个新 npm 依赖，后端零变更
- [x] 歧义检查：
  - AI Assist 独立 vs 共享 session 在 R2 明确"先独立，降级共享"
  - Limit 控件对非 SELECT SQL disable；执行前客户端注入
  - 废弃 shim 保留文件但 `return null`，与之前 Stage SQL Workbench Rebuild 的 shim 策略一致
- [x] 与已有 spec 链路：复用 `resolveTabDataContext` · `useSessionDataContext` · `connection-store` · `/api/channel`，不破坏现有协议
