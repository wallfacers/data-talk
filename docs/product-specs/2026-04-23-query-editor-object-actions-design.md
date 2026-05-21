# Query Editor Object Actions Design

- **日期**：2026-04-23
- **状态**：draft
- **前置**：
  - [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md)
  - [Stage Window SQL Workbench Design](./2026-04-21-stage-window-sql-workbench-design.md)
  - [Stage SQL Workbench Polish Design](./2026-04-22-stage-sql-workbench-polish-design.md)
  - [Session Data Context & AI Data Source Management Design](./2026-04-21-session-data-context-and-ai-datasource-management-design.md)

## 1. 背景与问题

`query_editor` 已经成为 Stage 中唯一 SQL 工作页，但“打开一个 SQL 编辑器”和“修改 SQL 编辑器内容”这两件事仍然分散在多条链路里，各自维护自己的标题分配、scope、payload 和上下文写入规则。

当前至少有 4 条会最终产出 `query_editor` 的入口：

1. 工作台 TabBar 的 `+` → `StageWindow.handleOpenSqlEditor()` → `openOrFocusStageToolTab(global_tool/sql)`
2. `!select` / `!with` 回车 → `openDirectSqlQueryEditorTab()`
3. bang-query 用户气泡的“重跑”按钮 → `openDirectSqlQueryEditorTab()`
4. `ui_exec(workspace, open, { type: 'query_editor' })` → `WorkspaceAdapter.exec('open')`

这些入口今天存在 3 类实质问题：

1. **命名规则不一致**：有的入口按 `workspaceTabs` 计数，有的按 `workspace + session` 计数，有的直接手工写 `title`
2. **对象语义不完整**：`query_editor` 目前更像“一个带 payload 的 tab”，而不是一个有明确 `state + actions + capabilities` 的一等对象
3. **SQL 编辑能力太粗糙**：AI 或外部调用方修改 SQL 时只能整段覆盖，无法像修改文件正文那样做 range-based edits

用户要求已经明确：

- 只要最终打开的是 `query_editor`，无论来自工作台 `+`、AI 消息里的可运行 SQL、`!` 前缀回车，还是 `ui_exec(workspace, open/query_editor)`，都必须走同一套打开语义
- **当前 Stage 实际可见的 `query_editor` 标签名称必须唯一**
- `query_editor` 的页面属性、动作、支持能力要以对象能力模型对外暴露
- SQL 正文要被当作“可编辑文件内容”，支持类似文件工具的精确编辑

## 2. 目标与非目标

### 2.1 目标

1. 把 `query_editor` 的“打开 / 命名 / 聚焦 / 上下文初始化”统一下沉到 `StageStore`
2. 建立 `query_editor` 的对象模型：`state + actions + capabilities`
3. 让 SQL 正文具备“文件内容”语义：支持 `replace_content`、`apply_text_edits` 等编辑动作
4. 保持 4 条入口的用户语义不变，但去掉它们各自的手工建 tab 逻辑
5. 通过 `QueryEditorAdapter` / `WorkspaceAdapter` 对外暴露统一对象能力，供 `ui_read / ui_exec / ui_patch` 使用

### 2.2 非目标

1. 不改后端 schema，不增加新的持久化表
2. 不把所有 Stage tab 都改造成文件对象；本轮只处理 `query_editor`
3. 不改变 markdown 代码块 `sql-execute` 当前“派发给 composer”这条交互语义；只有**最终会打开 `query_editor`** 的链路进入本设计
4. 不重做 `useSqlExecute`、Monaco outline、result panel 等现有 SQL 执行 UI；本轮重点是对象边界和入口统一

## 3. 根因分析

### 3.1 打开语义分散

当前 `query_editor` 的打开语义分散在 3 个位置：

| 位置 | 现状 |
|---|---|
| `openOrFocusStageToolTab()` | 负责部分 SQL tab 打开，但只覆盖 `global_tool` / `resource_tool` 模式 |
| `openDirectSqlQueryEditorTab()` | 手工拼 `StageTab`，自己写标题和 payload |
| `WorkspaceAdapter.exec('open')` | `query_editor` 有 `connection_id` 和无 `connection_id` 时走两套完全不同的逻辑 |

结果是“最终都是 `query_editor`”，但没有共同的真相源。

### 3.2 标题唯一性判断位置错了

TabBar 展示的是“当前 session tabs + workspace tabs”的合并视图，但今天部分命名逻辑只看 `workspaceTabs`，部分命名逻辑看 `workspace + 当前 session`，还有部分完全不经过唯一标题 helper。

因此会出现两个表面上都叫 `SQL 编辑器` 的 tab，同时又因为它们来自不同入口，后续的 `SQL 编辑器2/3` 计数继续漂移。

### 3.3 `query_editor` 仍然只是 payload 约定

今天 `query_editor` 的很多能力仍然依赖外部约定推断：

- 连接上下文混在 `tab.connectionId`、payload、session context 之间
- SQL 正文既存在 store，又被外部当成普通字符串操作
- Adapter 能做什么、不能做什么，更多靠实现细节而不是显式 capability contract

这与 Stage UI Object Protocol 想要表达的“对象可发现、可读、可执行、可编辑”的目标不一致。

## 4. 设计总览

本轮把 `query_editor` 明确建模为：

> 一个带数据库上下文的虚拟 SQL 文件对象。

它有 3 层语义：

1. **Tab 语义**：在 Stage 顶部工具栏里作为 `query_editor` tab 出现，名称唯一，可聚焦、可关闭
2. **对象语义**：对外暴露 `state + actions + capabilities`
3. **文档语义**：SQL 正文作为“文件内容”被读取和编辑，支持 range-based text edits

为此引入 2 个核心收口点：

1. `StageStore` 新增 `query_editor` 专用 action 集合，成为唯一打开和状态写入真相源
2. `QueryEditorAdapter` 改为显式暴露对象 action/capabilities，不再让外部直接依赖 payload 形状

## 5. StageStore 作为唯一真相源

### 5.1 新职责

`StageStore` 新增一组面向 `query_editor` 的专用动作，负责：

1. 计算“当前 Stage 实际可见的 `query_editor` tabs”
2. 为新建 SQL tab 分配唯一标题
3. 创建 / 复用 / 聚焦 `query_editor`
4. 维护 `query_editor` 的上下文、文档内容和轻量运行时状态

### 5.2 可见 tab 的定义

对于任意 `sessionId`，当前 Stage 实际可见的 `query_editor` 集合定义为：

```ts
visibleQueryEditors(sessionId) =
  workspaceTabs.filter(type === 'query_editor')
  + (tabsBySession.get(sessionId) ?? []).filter(type === 'query_editor')
```

**只有这个集合参与标题计数。**

这意味着：

- 其他类型 tab（`file_preview` / `report` / `dashboard`）不参与 SQL 标题计数
- 不在当前 session 可见范围内的 session-scoped tabs 不参与计数

### 5.3 新增的 store action

```ts
type QueryEditorOpenMode = 'always_new' | 'reuse_by_resource_context'

type QueryEditorOpenInput = {
  sessionId: string | null
  scope: 'workspace' | 'session'
  baseTitle: string
  openMode: QueryEditorOpenMode
  entryMode?: 'blank' | 'direct_sql' | 'resource_sql' | 'ui_exec' | 'ai_open'
  initialContent?: string
  autoRun?: boolean
  connectionId?: string | null
  connectionName?: string | null
  database?: string | null
  schema?: string | null
  payload?: Record<string, unknown>
}

type QueryEditorTextRange = {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

type QueryEditorTextEdit = {
  range: QueryEditorTextRange
  text: string
}
```

建议新增的核心 store API：

- `openQueryEditor(input): { tabId: string; created: boolean }`
- `focusQueryEditor(tabId): void`
- `closeQueryEditor(tabId): void`
- `setQueryEditorContext(tabId, context): void`
- `replaceQueryEditorContent(tabId, content, baseVersion?): void`
- `applyQueryEditorTextEdits(tabId, edits, baseVersion?): void`
- `setQueryEditorCursor(tabId, cursor): void`

### 5.4 标题分配规则

`openQueryEditor()` 内部统一按 `visibleQueryEditors(sessionId)` 的标题集合分配名称：

- 第一个：`SQL 编辑器`
- 第二个：`SQL 编辑器2`
- 第三个：`SQL 编辑器3`

不允许任何外部入口再自行调用 `resolveUniqueTabTitle()` 或自己拼 suffix。

### 5.5 焦点规则

当 `openQueryEditor()` 创建的是 workspace-scoped SQL tab 时：

- `activeWorkspaceTabId = tabId`
- 当前 `sessionId` 对应的 `activeTabIdBySession.set(sessionId, null)`

这样顶部工具栏和内容区始终一致显示当前 workspace SQL tab。

## 6. Query Editor 对象模型

### 6.1 对外 state

`query_editor` 对外暴露的标准 state 应包含：

```ts
type QueryEditorState = {
  tabId: string
  title: string
  scope: 'workspace' | 'session'

  content: string
  language: 'sql'
  version: number
  dirty: boolean
  cursor: { line: number; column: number }
  selection?: {
    startLine: number
    startColumn: number
    endLine: number
    endColumn: number
  } | null

  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextOverride: TabContextOverride | null

  entryMode: 'blank' | 'direct_sql' | 'resource_sql' | 'ui_exec' | 'ai_open'
  autoRun: boolean

  executeStatus: 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'
  results: QueryEditorResultSummary[]   // 仅摘要，不含 rows，见下
  activeResultId: string | null
  limit: 10 | 100 | 1000 | null
}

// 对 AI 暴露的结果项只含摘要，行数据永不进入 AI 上下文
// 行数据仍保留在 SqlWorkbenchStore / DataGrid 里，由 UI 直接渲染
type QueryEditorResultSummary = {
  resultId: string
  statementIndex: number
  columns: string[]
  rowCount: number
  durationMs: number
  truncated: boolean
  error?: { code: string; message: string } | null
}
```

> **硬性约束**：`read('state').results` 永远不包含 `rows` / `preview` / `data` 等原始行字段。AI 若需要行数据，必须显式走分析路径 `datatalk.execute_sql`（对齐 Stage UI Object Protocol §6.3 的隔离原则）。

### 6.2 对外 capabilities

`query_editor` 必须显式声明自身能力，而不是让调用方通过 `type === 'query_editor'` 猜测：

```ts
type QueryEditorCapabilities = {
  editableContent: true
  acceptsTextEdits: true
  runnable: true
  formattable: true
  supportsContextBinding: true
  supportsResults: true
}
```

## 7. SQL 作为“文件内容”编辑

### 7.1 设计原则

SQL 正文被视为“虚拟文件正文”，而不是普通字符串字段。

因此 AI 或外部调用方修改 SQL 时，首选文件式编辑动作，而不是全量覆盖。

### 7.2 对 AI 暴露的两条 canonical 写路径（唯一心智）

`query_editor` 在对 AI 的接口层**只保留两条写路径**，一条快速全量覆盖、一条精确区间编辑，互不重叠：

| 场景 | Canonical 接口 | 是否需要 version |
|---|---|---|
| 整段覆盖 SQL（新建 / 首次写入 / 彻底重写） | `ui_patch(query_editor, target, ops=[{op:'replace', path:'/content', value}])` | 否 |
| 按 Monaco range 精确编辑 / 并发安全写入 | `ui_exec(query_editor, target, 'apply_text_edits', { baseVersion, edits })` | 是，冲突时返回 `version_conflict` |

选择这个分工的理由：

1. 对齐 open-db-studio 成熟心智（其 system prompt 明文要求「Use `ui_patch` on `query_editor` to write SQL」）
2. 让 AI 做二选一决策时没有歧义：**要版本冲突保护就走 `apply_text_edits`，否则走 `ui_patch(/content)`**
3. `ui_patch` 走 JSON Patch 语义，天然表达不了 `startLine/startColumn/endLine/endColumn` + `baseVersion`，所以 range 编辑落在 `ui_exec` 是唯一合理归属

### 7.3 不对 AI 暴露的内部 action

以下 action 只保留为前端/Adapter 内部调用点，**不写进 `read('actions')`**，因此不会出现在 AGENTS.md，也不会被 AI 选到：

- `replace_content` —— 已被 §7.2 的 `ui_patch(/content, replace)` 完全覆盖，仅作为 Adapter 内部 helper 供旧调用点平滑迁移
- `insert_text` / `delete_range` —— `apply_text_edits` 的退化情形，由 Adapter 内部实现复用即可，不必膨胀 AI 动作表面积

> 判断规则：**能用「两条 canonical 写 + 专用 action」表达的事，不再开第三条路**。AGENTS.md 里 `query_editor` 的 action 表严格等于 §7.4 列出的六条。

### 7.4 对 AI 暴露的其他内容相关 action

| action | 作用 | 为什么不是 `ui_patch` |
|---|---|---|
| `apply_text_edits` | 见 §7.2 | range + version 无法用 JSON Patch 表达 |
| `set_context` | 一次性切 `connectionId / database / schema` | 三字段联动，分三次写会出现中间态不一致 |
| `run_sql` | 执行当前 SQL，结果写回 Tab state，不进 AI 上下文 | 副作用 + 异步 |
| `format_sql` | 格式化当前 SQL | 副作用（修改 `content`），非纯字段写 |
| `focus` / `close` | 保留 | 纯副作用 |

### 7.5 版本控制与冲突返回

`apply_text_edits` 必须接受 `baseVersion`，若调用方传入的版本落后于当前 store 版本，则返回 §8.4 定义的结构化错误 `code: 'version_conflict'`，并在 `currentState` 中回传 `{ version, content }`，而不是静默覆盖。

这让 AI 能像操作文件那样进行“读 → 改 → 写”闭环：一次 `ui_read(state)` 拿到 `version` → 按 range 计算 edits → `apply_text_edits(baseVersion=...)`；若冲突，AI 拿到 `currentState` 后可自行 rebase 再提交，无需人工介入。

`ui_patch(/content, replace)` 不接受 `baseVersion`，语义就是「我要彻底重写」，因此不走版本冲突检查；这也是两条 canonical 路径分工的延伸。

## 8. 对象 actions 设计

### 8.1 `query_editor` 对 AI 暴露的 actions

`QueryEditorAdapter.read('actions')` **对 AI 可见**的动作严格限定为下列六条（单独 `set_connection / set_database / set_schema` 不再暴露，全部归入 `set_context`，避免 AI 需要记忆同义动词）：

| action | params | 作用 |
|---|---|---|
| `apply_text_edits` | `{ baseVersion: number, edits: [{range, text}] }` | 按 range 精确编辑 SQL，带版本冲突保护 |
| `set_context` | `{ connectionId?: string\|null, database?: string\|null, schema?: string\|null }` | 一次性切换执行上下文；至少传一个字段；字段缺省表示保留 |
| `run_sql` | `{ limit?: 10\|100\|1000 }` | 执行当前 SQL，结果写回 Tab state，不进 AI 上下文 |
| `format_sql` | — | 格式化当前 SQL（会改 `content` 并递增 `version`） |
| `focus` | — | 聚焦当前 tab |
| `close` | — | 关闭当前 tab |

> 整段覆盖 SQL 不出现在这里，因为 canonical 是 `ui_patch(/content, replace)`（§7.2）。

### 8.2 `query_editor` patchCapabilities

`QueryEditorAdapter.patchCapabilities` 显式声明如下，`UIRouter.handlePatch` 据此做白名单校验，其他路径一律拒绝：

```ts
[
  { pathPattern: '/content',      ops: ['replace'] },
  { pathPattern: '/connectionId', ops: ['replace'] },
  { pathPattern: '/database',     ops: ['replace'] },
  { pathPattern: '/schema',       ops: ['replace'] },
]
```

三条上下文字段与 `set_context` action 语义等价（单字段写入时）。保留它们在 `patchCapabilities` 里的理由：AI 只想调一个字段时 `ui_patch` 更直接，避免为单字段必须组装 `set_context` 的完整对象。

### 8.3 `workspace` actions

`WorkspaceAdapter` 保留 `open / close / focus / choose_connection`，但其 `open(query_editor)` 必须只做参数转换，不得再自己分配标题或手工 `openTab()`。

统一转调 `StageStore.openQueryEditor()`。

### 8.4 面向 AI 的统一错误契约

所有 `ui_read / ui_patch / ui_exec / ui_list` 调用的错误返回必须遵循下列 shape，让 AI 能自解、自救，不再退化成对用户复述裸字符串：

```ts
type UIErrorDetail = {
  code: string                     // 机器可识别，例：'invalid_params' / 'version_conflict'
                                   //                / 'unsupported_patch' / 'unknown_action'
                                   //                / 'missing_session' / 'sql_guard_rejected'
  message: string                  // 一行总结，自然语言
  hint?: string                    // 一行“下一步怎么做”，markdown 友好，允许内联 `code`
  availableActions?: string[]      // 当错误与 action 名或 patch 路径相关时必填
  expectedSchema?: unknown         // 当错误是参数缺失/类型错时必填
  currentState?: unknown           // 当错误依赖当前状态时必填（典型：version_conflict 回传 {version, content}）
}
```

回传位置约定：

- `UIRouter` 的 `UIResponse.error` 字段承载 `message`；`UIResponse.data` 同步承载完整 `UIErrorDetail`（对齐当前 `execError` 的 `{error, details}` 扩展）
- Adapter 内部统一通过 `execError(code, message, {hint, availableActions, ...})` / `patchError(code, message, {...})` 构造，禁止散落裸字符串

错误样例（AI 拿到后可直接自救）：

```json
{
  "code": "version_conflict",
  "message": "Editor content has advanced to version 12",
  "hint": "Re-read with `ui_read(mode='state')` to rebase edits, then retry with new baseVersion.",
  "currentState": { "version": 12, "content": "select 1" }
}
```

```json
{
  "code": "unsupported_patch",
  "message": "Patch op replace on path /title is not supported",
  "hint": "Only /content, /connectionId, /database, /schema accept replace on query_editor.",
  "availableActions": ["apply_text_edits", "set_context"]
}
```

```json
{
  "code": "unknown_action",
  "message": "Unknown action 'replace_content' on query_editor",
  "hint": "Use `ui_patch` with path '/content' op 'replace' for full-content rewrite.",
  "availableActions": ["apply_text_edits", "set_context", "run_sql", "format_sql", "focus", "close"]
}
```

**约束**：AGENTS.md 必须告知 AI「见到 `hint` 时按 `hint` 自救，不要原样复述给用户」。

## 9. 四条入口的统一映射

### 9.1 工作台 `+`

- 调用：`openQueryEditor({ scope: 'workspace', openMode: 'always_new', entryMode: 'blank' })`
- 语义：总是新建空白 SQL 编辑器

### 9.2 `!select` / `!with`

- 调用：`openQueryEditor({ scope: 'session', openMode: 'always_new', entryMode: 'direct_sql', autoRun })`
- 语义：每次新建一个 session-scoped SQL 编辑器，并带初始 SQL

### 9.3 bang-query “重跑”

- 与 `!select` / `!with` 完全相同
- 不允许自己再手工造 `StageTab`

### 9.4 `ui_exec(workspace, open, { type: 'query_editor' })`

分两种情况：

1. 带 `connection_id / database / schema`
   - 调用：`openQueryEditor({ scope: 'session', openMode: 'reuse_by_resource_context', ... })`
   - 语义：AI 打开“某个资源上下文下的 SQL 工作页”时，优先复用已有同上下文 tab

2. 不带 `connection_id`
   - 调用：`openQueryEditor({ scope: 'session', openMode: 'always_new', entryMode: 'ui_exec' })`
   - 语义：打开一个新的普通 SQL 编辑器，由后续 action 再写内容/上下文

### 9.5 markdown `sql-execute`

这条链路当前行为是“把 SQL 派发给 composer”，不是“直接打开 `query_editor`”。

本设计**不改变这条交互语义**。如果未来要让 markdown SQL 代码块直接开编辑器，应显式新增新事件或新 action，而不是悄悄复用当前 `sql-execute`。

### 9.6 未来 AI 消息里的“Open in Editor”类按钮

如果后续在 AI tool card、SQL preview card、错误诊断卡片等消息表面新增“在编辑器中打开”类按钮，这些按钮必须直接委托 `openQueryEditor()`。

它们不得：

- 自己手工创建 `StageTab`
- 自己计算 `SQL 编辑器2/3`
- 自己决定 `scope`

## 10. Adapter 改造

### 10.1 `WorkspaceAdapter`

`WorkspaceAdapter` 的 `query_editor` 打开逻辑需要删除以下职责：

- 手工分配 `tabId`
- 手工决定标题
- 手工 `store.openTab()`
- 直接覆盖 `title/payload`

保留职责：

- 将 `ui_exec` 参数翻译成 `openQueryEditor()` 所需输入
- 对非 `query_editor` 类型继续维持现有 workspace/session tab 逻辑

### 10.2 `QueryEditorAdapter`

`QueryEditorAdapter` 需要从“只读包装器”升级为“对象能力出口”：

- `read('state')` 返回标准化 `QueryEditorState`（含 `content / version / dirty / cursor / selection / executeStatus / results` 等，**永不含原始行数据**——结果只回摘要 `{columns, rowCount, durationMs, truncated}`）
- `read('actions')` 严格返回 §8.1 定义的六条；不得包含 `replace_content / insert_text / delete_range` 等内部动作
- `read('full')` 同时带上 `capabilities`（§6.2）
- `patchCapabilities` 按 §8.2 显式声明；`patch()` 的上下文字段写入必须与 `set_context` 共用底层 store action
- `exec()` 统一转调 store action 或已有 SQL 执行/格式化能力；失败分支一律用 §8.4 的 `UIErrorDetail` shape 返回

### 10.3 `openOrFocusStageToolTab` 与 `openDirectSqlQueryEditorTab`

这两个 helper 保留为兼容 facade，但内部不再手工造 `query_editor`。

它们只负责：

- 解析旧入参
- 调 `openQueryEditor()`
- 返回 `{ tabId, created }` 或 `tabId`

### 10.4 `UIRouter` 与错误契约

- `execError` / `patchError` 的返回 shape 统一扩到 §8.4 的 `UIErrorDetail`（`code / message / hint / availableActions? / expectedSchema? / currentState?`），放在 `client/src/services/ui-router/errors.ts`
- `UIRouter.handlePatch`：未命中 `patchCapabilities` 时返回 `code: 'unsupported_patch'` + `availableActions`（列出当前对象 `read('actions')` 中的动作名，给 AI 指一条替代路径）
- `UIRouter.handleExec`：未命中 `read('actions')` 时返回 `code: 'unknown_action'` + `availableActions`；`required` 缺失返回 `code: 'invalid_params'` + `expectedSchema`
- 后端四个 `datatalk.ui.*` CLIENT Action 的 schema **不改**——细粒度校验仍在前端完成，与既有 `校验靠近实现` 原则一致

## 11. AGENTS.md 同步修改清单

落地本设计时，`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 必须同步做下列增量修改（只改对应小节，不做整体重排，避免扰动既有风格）：

**A. `### datatalk.ui.patch` 的 Supported patch paths 表**——从当前的 "None currently" 替换为：

```
| object       | path           | ops     |
|--------------|----------------|---------|
| query_editor | /content       | replace |
| query_editor | /connectionId  | replace |
| query_editor | /database      | replace |
| query_editor | /schema        | replace |
```

**B. `### datatalk.ui.exec` 的 Available actions 表**——`query_editor` 行扩为 §8.1 的六条（`apply_text_edits / set_context / run_sql / format_sql / focus / close`），**不写 `replace_content`**（那是 `ui_patch(/content)` 的职责）。

**C. `### datatalk.ui.read` 的 query_editor state fields**——从当前的 `sql / source / entryMode / connectionId / connectionName / database / schema / lastRun / contextNotice` 扩为：

```
content, version, dirty, cursor, selection,
connectionId, connectionName, database, schema, contextOverride,
entryMode, autoRun,
executeStatus, results (摘要, 无 rows), activeResultId, limit
```

并在字段说明里明确：**`results` 只包含 `{columns, rowCount, durationMs, truncated}` 摘要，不包含原始行数据；AI 需要行数据时走 `datatalk.execute_sql`（分析路径）**。

**D. 新增一小节「Query Editor 编辑规范」**，内容至少覆盖：

1. 写 SQL 前先 `ui_read(query_editor, target, mode='state')` 获得 `version`
2. **整段覆盖用 `ui_patch(/content, replace)`；精确编辑用 `ui_exec(apply_text_edits, {baseVersion, edits})`**——二者唯一分工
3. 切上下文用 `ui_exec(set_context)`（一次性）或 `ui_patch(/connectionId|/database|/schema)`（单字段）
4. 绝不在聊天里要求用户手动复制 SQL——必须写进 `query_editor`
5. 错误响应出现 `hint` 字段时按 `hint` 自救，不要原样复述给用户；典型自救：`version_conflict` → 重读 state 后以新 `baseVersion` 重试

## 12. 测试策略

### 12.1 新增/调整测试

1. `stage-store` 单测
   - 可见 `query_editor` 标题统一计数
   - workspace SQL 打开时清空 session active tab
   - `apply_text_edits` 按 version 生效 / 冲突失败

2. `open-direct-sql-query-editor-tab.test.ts`
   - 变成 facade 测试，验证它正确委托给 store action

3. `WorkspaceAdapter.test.ts`
   - `query_editor` 两条 `open` 分支都改成验证委托结果，而不是检查它手工拼 tab

4. `StageWindow.test.tsx`
   - `+` 打开 SQL 编辑器时名称递增且焦点正确

5. `QueryEditorAdapter.test.ts`
   - `read('state')` 字段集对齐 §6.1；`results` 永不含 rows
   - `read('actions')` 严格等于六条（`apply_text_edits / set_context / run_sql / format_sql / focus / close`），不含 `replace_content / insert_text / delete_range`
   - `read('full')` 同时带 `capabilities`
   - `ui_patch(/content, replace)` 成功路径 + 非白名单路径返回 `code: 'unsupported_patch'`
   - `apply_text_edits` 版本一致成功 / 落后返回 `code: 'version_conflict'` + `currentState.version`
   - `set_context` 单/多字段；缺失全部字段返回 `code: 'invalid_params'` + `expectedSchema`

6. `UIRouter.test.ts`（错误契约回归）
   - 所有错误路径（`unsupported_patch` / `unknown_action` / `invalid_params` / `version_conflict`）返回结构均满足 §8.4 shape
   - `hint` 字段存在且语义可执行（内容校验至少包含 action 名或 path 名）

### 12.2 回归重点

必须覆盖 4 个用户可见/AI 可见回归：

1. `SQL 编辑器`、`SQL 编辑器2`、`SQL 编辑器3` 计数稳定
2. 不同入口打开的 `query_editor` 在顶部工具栏里不会重名
3. AI/对象调用修改 SQL 时，不会因为整段覆盖把用户当前其他编辑静默吞掉
4. AGENTS.md 的 `query_editor` action 列表与 `QueryEditorAdapter.read('actions')` 运行时返回完全一致（可以通过一条轻量 meta 测试断言，避免文档漂移）

## 13. 风险与兼容性

### 13.1 风险

1. `query_editor` 相关状态今天分布在 `StageStore` 和 `useSqlWorkbenchStore`，需要避免重复来源
2. `ui_patch(/content)` 与 `apply_text_edits` 并存阶段，容易出现双语义 —— 通过 §7.2 的明确分工 + AGENTS.md §D 告知来消解
3. `WorkspaceAdapter` 今天对 `query_editor` 的“有 connection_id”和“无 connection_id”分支行为不一致，迁移时要防止 AI 打开的 tab 语义变化
4. 错误契约扩展后，旧调用点散落的裸字符串错误会与新 shape 并存，需要一次性全量替换 `execError / patchError` 的调用点

### 13.2 兼容策略

1. 先引入 canonical store action，再让旧 helper 逐步降级为 facade
2. 保留旧 API 名称作为 shim，一个版本内不直接删
3. 迁移完成后再清理旧的标题分配和手工 `openTab()` 逻辑
4. `replace_content` 作为 Adapter 内部 helper 继续存在但不进 `read('actions')`；若发现 AI 仍尝试调用，由 `UIRouter.handleExec` 返回 `unknown_action` + `hint` 引导到 `ui_patch(/content)`

## 14. 结论

本轮不再把 `query_editor` 视为“若干入口碰巧都产出同一种 tab”，而是正式把它收敛为：

- 一个由 `StageStore` 统一打开和命名的 Stage 对象
- 一个对外声明 `state + actions + capabilities` 的 UI object
- 一个可按“文件内容”方式被 AI 精确编辑的虚拟 SQL 文件

只有这样，工作台 `+`、`!sql`、bang-query 重跑、`ui_exec(workspace, open/query_editor)` 和后续更多 SQL 入口才能长期保持同一语义，而不会继续各自产生标题、scope 和内容编辑规则漂移。
