# SQL Editor Toolbar Context Design

**日期**：2026-04-30

**状态**：approved

**范围**：重做 Stage SQL 编辑器的执行上下文控件，把 session 上下文开关、连接 / 数据库 / Schema 下拉和分页限制统一放入 SQL toolbar，并补齐 AI 可操作契约。

## 1. 背景

当前 SQL 编辑器的执行上下文隐藏在 `SQL 执行上下文` popover 里，用户需要先点上下文按钮，再区分 session context / tab override / apply override / unpin。这个模型在两个场景里不够直接：

1. 用户写 SQL 前不能一眼确认实际执行的连接、数据库和 Schema。
2. AI 通过 session 级 `set_data_context` 修改上下文后，SQL 编辑器的展示和 tab override 语义容易混在一起。

本设计将执行上下文提升为 SQL toolbar 的一等控件：开关决定是否跟随 session，下拉展示并控制连接 / 数据库 / Schema，分页限制保持在最末尾。

## 2. Design Inputs

- `client/DESIGN.md`
  - SQL 编辑器是工作台工具面，控件必须紧凑、可扫描，避免说明卡片和大段帮助文案。
  - 使用 shadcn/ui 语义控件：`Switch` 表示二元模式，`Select` 表示连接 / 数据库 / Schema / 分页限制。
  - 用户可见文案走 i18n。
  - toolbar 控件使用稳定尺寸和响应式换行，避免遮挡 SQL 编辑区。
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
  - 本设计改变 Stage Query Editor context 和 data source picker 行为，因此适用兼容门禁。
  - 不新增数据库类型、不新增 JDBC 驱动、不改变 SQL splitter / risk analyzer / 后端 SQL execute API。
  - Schema 下拉是否显示继续依赖现有 connection kind / targets 语义；若发现新数据库类型差异，后续实现必须同步更新兼容文档。
- 相关既有设计：
  - `2026-04-21-session-data-context-and-ai-datasource-management-design.md`
  - `2026-04-23-query-editor-object-actions-design.md`
  - `2026-04-30-query-editor-session-context-plan.md`

## 3. Goals

1. 把 SQL 执行上下文从 popover 改成 toolbar 内联控件。
2. 默认跟随当前 session data context，并在 AI 修改 session context 后实时联动展示。
3. 用户关闭 session 跟随后，可手动选择连接 / 数据库 / Schema，选择立即生效到当前 SQL tab。
4. 分页限制保持在 toolbar 最末尾。
5. 为 AI 暴露可读、可执行、参数联动明确的 query editor context action。
6. 下拉刷新失败时用全局消息提示，并保留当前上下文。

## 4. Non-Goals

1. 不改后端 SQL execute API。
2. 不新增数据库类型、连接类型或 target discovery 后端契约。
3. 不把 tab override 反向写入 session context。
4. 不新增独立设置页或说明面板。
5. 不改变 `resolvedContext` 的语义；它仍只表示上一次后端实际执行落点。

## 5. Toolbar UI

SQL 编辑器 toolbar 由两组组成。

左侧执行工具保持现有语义：

```text
运行 | 格式化 | 解释(可选)
```

右侧上下文工具按固定顺序排列：

```text
固定 session 上下文: Switch | 连接: Select | 数据库: Select | Schema: Select | 分页限制: Select
```

约束：

- `分页限制` 永远在最末尾。
- `Schema` 只有当前连接类型 / targets 支持 schema，或当前上下文已有 schema 时显示。
- toolbar 宽度不足时右侧控件允许换行，但顺序不变。
- 控件 label 用短文案：`固定 session 上下文`、`连接`、`数据库`、`Schema`、`分页限制`。
- 原 `SqlContextChip` / `SQL 执行上下文` popover 退场，不再显示 `应用并固定` / `取消固定` 这类按钮式流程。

## 6. State Model

将 query editor 上下文模式明确为：

```ts
type QueryEditorContextMode = {
  useSessionContext: boolean
}
```

### 6.1 `useSessionContext: true`

- SQL 执行上下文来自当前 session data context。
- toolbar 下拉展示 session 的 `connectionId / database / schema`。
- AI 通过 session 级 `set_data_context` 修改 session 后，toolbar 自动联动。
- 当前 tab 不写入新的 override。
- 连接 / 数据库 / Schema 下拉只读或禁用，避免用户误以为会直接改 tab。

### 6.2 `useSessionContext: false`

- SQL 执行上下文来自当前 tab override。
- 用户选择连接 / 数据库 / Schema 后立即写入当前 tab override。
- 不反向更新 session data context。
- SQL 执行优先使用 tab override。

### 6.3 默认与迁移

- 新建 SQL tab 默认 `useSessionContext: true`。
- 旧 tab 迁移：
  - 已有明确 tab override 的旧 tab：`useSessionContext: false`。
  - 没有 override 的旧 tab：`useSessionContext: true`。
- 用户关闭开关后写入显式 tab 级标记，避免刷新或恢复后自动回到 session 模式。

## 7. Context Resolution

执行上下文统一从同一个 resolver 产出：

```ts
type QueryEditorEffectiveContext = {
  useSessionContext: boolean
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextSource: 'session' | 'override' | 'tab'
}
```

解析规则：

1. `useSessionContext: true`：使用最新 session data context。
2. `useSessionContext: false`：使用 tab override。
3. `resolvedContext` 不参与下一次执行上下文推导，只用于展示上一次执行结果落点。
4. `limit` 继续保存在 SQL workbench tab runtime state，与上下文分开。

## 8. Dropdown Data Flow

### 8.1 连接下拉

- 每次展开都调用 `listConnections()` 拉最新连接。
- `useSessionContext: false` 时，选择连接立即写入 tab override。
- 选择连接后清空不匹配的 database / schema，并立即拉取该连接 targets。

### 8.2 数据库下拉

- 每次展开基于当前连接调用 target discovery。
- 选择数据库后立即写入 tab override。
- 选择数据库后清空旧 schema，或切换到后续实现定义的可用默认 schema。

### 8.3 Schema 下拉

- 仅当当前连接支持 schema、targets 返回 schema，或当前上下文已有 schema 时显示。
- 每次展开基于当前连接和当前数据库刷新。
- 选择 schema 后立即写入 tab override。

### 8.4 联动安全

- 禁止出现 “B 连接 + A 数据库 / Schema” 的组合。
- `__empty__` 只作为 Select 内部空值，不进入 payload / store / API。
- targets 刷新失败时不清空当前值，不写入 override。

## 9. Failure Handling

所有下拉刷新失败走全局 toast / message。

文案：

- 连接列表刷新失败：`连接列表刷新失败，请检查服务或网络。`
- 数据库 / Schema 刷新失败：`数据库上下文刷新失败，请检查连接是否可用。`
- 如果错误能定位到连接，提示优先包含连接名；没有连接名时包含连接 ID。

失败策略：

- 保留当前已选值。
- 不写入 tab override。
- 不影响 SQL 编辑器正文。
- 用户可再次展开下拉重试。

## 10. AI Action Contract

保留 `query_editor.set_context` 作为兼容 action，但升级 schema：

```ts
type QueryEditorSetContextParams = {
  useSessionContext?: boolean
  connectionId?: string | null
  database?: string | null
  schema?: string | null
  limit?: 10 | 100 | 1000 | null
}
```

参数规则：

- `useSessionContext` 可独立传。
- `limit` 可独立传。
- 设置连接：必须传 `connectionId`。
- 设置数据库：必须传 `connectionId + database`。
- 设置 Schema：必须传 `connectionId + database + schema`。
- `useSessionContext: true` 不允许同时传 `connectionId / database / schema`。
- 传 `connectionId / database / schema` 时自动视为 `useSessionContext: false`，写入 tab override。
- AI 修改 tab override 不回写 session。
- AI 想修改 session context 仍走 session 级 `set_data_context`；当 SQL tab `useSessionContext: true` 时 toolbar 自动联动。

失败示例：

- 只传 `schema`：失败，提示必须同时传 `connectionId` 和 `database`。
- 传 `database` 但不传 `connectionId`：失败。
- 传 `useSessionContext: true` 同时传 `database`：失败。

## 11. AI Read State

`query_editor.read('state')` 和 `workspace.read('state')` 需要暴露：

```ts
type QueryEditorContextState = {
  useSessionContext: boolean
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextSource: 'session' | 'override' | 'tab'
  contextOverride: unknown
  limit: 10 | 100 | 1000 | null
  availableDatabases?: string[]
  availableSchemas?: string[]
}
```

AI 使用方式：

1. 先 `ui_read(query_editor, state)` 获取当前模式与有效上下文。
2. 如果用户要求跟随当前 session，调用 `set_context({ useSessionContext: true })`。
3. 如果用户要求当前 SQL tab 临时切库，调用 `set_context({ connectionId, database, schema })`，并带齐联动参数。
4. 如果只改分页限制，调用 `set_context({ limit })`。

## 12. Testing

前端测试覆盖：

- toolbar 直接展示 `固定 session 上下文`、连接、数据库、Schema、分页限制，且分页限制在最后。
- 新建 SQL tab 默认 `useSessionContext: true`。
- `useSessionContext: true` 时 session data context 更新后 toolbar 自动联动。
- `useSessionContext: false` 时用户选择连接 / 数据库 / Schema 立即写入 tab override。
- 连接变化后 database / schema 不保留旧连接的值。
- targets 刷新失败时触发全局 toast，且不清空当前上下文。
- `query_editor.set_context` 支持 `useSessionContext` 和 `limit`。
- `query_editor.set_context` 拒绝缺少联动参数的 database / schema 设置。
- `query_editor.read('state')` / `workspace.read('state')` 暴露 `useSessionContext` 和 effective context。
- 旧 tab 迁移规则覆盖有 override / 无 override 两种路径。

## 13. Compatibility Checklist

- Domain Layer: N/A，本设计不新增 domain sealed interface / record。
- Frontend Connection UI: Applicable，SQL toolbar 内新增连接 / 数据库 / Schema 选择入口，但不新增连接类型。
- Application Connection Layer: N/A，不改变后端连接解析或 JDBC URL。
- Persistence And Metadata DB: N/A，不新增数据库表；tab payload 中可能新增轻量模式字段，由现有 Stage tab payload 持久化。
- JDBC Driver And Runtime Packaging: N/A。
- Dynamic SQL Execution Repository: N/A，继续使用现有 `/api/sql/execute`。
- Schema Discovery And Target Resolution: Applicable，复用现有 connection targets 拉取；后续若扩展到按 database 拉 schema，需要同步更新本兼容矩阵。
- SQL Statement Splitting / Risk Analysis: N/A，本设计不改变执行 SQL 文本解析和风险判级。
- Adapter Actions And Ontology: Applicable，`query_editor.set_context` schema 和 read state 需要更新，AI 必须带齐联动参数。
- Runtime Agent Prompt / Tool Docs: Applicable，后续实现计划需要同步更新 UI object reference / runtime prompt 中 query editor 上下文操作说明。

## 14. Implementation Direction

1. 抽出新的 `SqlContextToolbarControls`，替代 `SqlContextChip` popover，并由 `SqlEditorToolbar` 直接承载。
2. 首版复用现有 `connectionId` 粒度 target discovery，不扩展后端 API；若后续需要 database-aware schema discovery，单独设计并更新兼容文档。
3. payload 中使用清晰字段 `useSessionContext` 表示开关状态；旧的 `contextPinMode` 仅作为迁移兼容输入，不作为新状态主字段。
