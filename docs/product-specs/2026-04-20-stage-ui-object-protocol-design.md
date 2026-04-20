# Stage UI Object Protocol —— 让 StageWindow 成为 AI 可操作的多 Tab 工作屏

> 状态：Draft · 2026-04-20 · wallfacers
> 
> 范围：引入受 open-db-studio UI Object 协议启发的前端 `UIRouter` + Adapter 体系；StageWindow 从"单 Artifact 容器"升级为"AI 可操作的多 Tab 工作屏"；新增用户直查通道 `!<sql>`，建立 **展示路径** 与 **分析路径** 两条 SQL 执行语义。

---

## 1. 背景与现状评估

### 1.1 data-talk 当前 AI→SQL 通路

| 环节 | 位置 | 状态 |
|------|------|------|
| 用户输入 | `client/src/features/session/prompt-composer.tsx` | Composer 统一走 `useChannel().sendMessage()`，无旁路 |
| 传输 | `client/src/services/channel/*` + 后端 `ChannelController` | Streamable HTTP + SSE，`action.invoke` / `action_result` 已支持 CLIENT-executor |
| AI 执行 SQL | `server/data-talk-adapter/.../actions/ExecuteSqlAction.java` | `Executor.SERVER`、`datatalk.execute_sql`，`SqlStatementGuard` 仅放行 SELECT/WITH；结果落 Artifact、推 `ontology.updated`；超 256 KB 写 `query_results` 表 + `handle://` 引用 |
| 结果展示 | `client/src/features/ontology/components/artifact-canvas.tsx` + `StageWindow` | 单 Artifact 投影，无多 Tab；SQL 代码块"执行"按钮仅重填 Composer（即再次送 AI） |
| 非 AI 通道 | 后端 `POST /api/query` + `QueryApplicationService` + `client/src/services/api/query.ts#executeQuery` | **后端已就绪，前端 Composer 无任何分支会走它** |
| CLIENT Action 示例 | `PinArtifactAction` + `client/src/features/actions/client-handlers.ts` | 成熟示例：handler 在服务端声明 schema、前端 `registerClientHandler(id, fn)` 真正执行 |

核心缺口：前端无能力让 AI 操作本地 UI 对象；大结果集无论 AI 还是用户发起都必然流经 AI 上下文；`!` 直查心智完全未表达。

### 1.2 open-db-studio 的可借鉴点

| 组件 | 路径（open-db-studio） | 心智 |
|------|------------------------|------|
| UI Object 协议规范 | `src/mcp/ui/types.ts` | 每个 UI 元素由 `type + objectId` 标识；`read/patch/exec` 三动词 + `list` 发现入口 |
| 路由器 | `src/mcp/ui/UIRouter.ts` | 实例注册表 + `target='active'` 解析 + patch capability 校验 + exec action schema 校验 |
| 适配器样例 | `src/mcp/ui/adapters/QueryEditorAdapter.ts` | `connectionId` 作为一等 patch 字段；`run_sql` 用 Tauri event 解耦"意图"与"执行"；`patchCapabilities` 白名单约束 AI 行为 |
| 系统 Prompt | `prompts/chat_assistant.txt` | 将 UI Object 协议 + 四件套 + 各对象 `Actions` 教给 LLM；"Discovery-First"；不准把 SQL 粘给用户复制 |
| 本地命令 | `src/components/Assistant/slashCommands.ts` | SlashCommand 注册表（name / execute / isAvailable）—— `!` 指令天然归入此类 |

**借鉴边界**：open-db-studio 是"前端即后端"（Tauri + Rust 单体），AI 通过 MCP 直接和前端说话。data-talk 多一层 Spring Boot，需要把 `ui_read/patch/exec/list` 以 `Executor.CLIENT` Action 的形式"桥接"到前端 `UIRouter`——这是协议能无损移植的关键改造点。

### 1.3 与既有设计的关系

- **Stage As Computer**（2026-04-17）奠定了 "StageWindow = AI 电脑外壳" 的视觉隐喻，本文把它从"壳"扩成"可操作的多 Tab 系统屏幕"
- **SQL Risk Classification**（2026-04-20）的 AST 判级能力在本文中复用：`datatalk.execute_sql`（分析路径）和 `/api/query`（展示/`!` 路径）共用同一 `SqlStatementGuard`
- **Single Empty Session**、**AI Message Rendering Migration** 等前置迁移不受影响：本文在其基础上演进 StageWindow 子系统

---

## 2. 核心模型

### 2.1 两条 SQL 执行路径（按"结果是否进 AI 上下文"划分）

| 场景 | 发起方 | 调用链 | 结果进 AI 上下文？ |
|------|--------|--------|---------------------|
| 用户 `!select * from users` | 用户 | Composer 拦截 → `POST /api/query` → `bang_query` Tab | ❌ |
| AI "给我看看 users 表"（展示型） | AI | `ui_exec(workspace, open, {type:'query_editor'})` → `ui_patch(/content, sql)` → `ui_exec(query_editor, run_sql)` → 前端调 `POST /api/query` | ❌（只回 `{rowCount, columns, durationMs}` 元数据） |
| AI "分析 2026 订单趋势"（分析型） | AI | `datatalk.execute_sql`（现有 MCP tool） | ✅（preview rows 进 Artifact → AI 下一轮推理可见） |

> 关键发现：**两种用户感知的"执行"行为（看数据 vs 分析数据）对应的是 AI 上下文策略的差异，不是"谁发起"的差异**。
> 
> `!` 是展示路径的用户直通版本；它与 AI 的 `run_sql` 共享同一结果通道（前端 `POST /api/query`），同一 Tab 类型（`bang_query` ≈ `query_editor` 的只读版），同一"不回流 AI"承诺。

### 2.2 三层连接绑定

| 层级 | 位置 | 角色 |
|------|------|------|
| **Global UI** | `useConnectionStore.activeConnectionId` | 用户的"当前选择"；驱动新建会话、新开 Tab 的默认值 |
| **Session** | `session.connectionId`（建会话时从 Global 固化，持久化到 SQLite） | AI 会话上下文；`execute_sql` Action 读此值；AI 在 `ui_exec(workspace, open)` 未指定 `connection_id` 时取此值填默认 |
| **Tab** | 每个 `query_editor` / `bang_query` / `er_canvas` Tab 自持 `connectionId / database / schema` | 执行上下文；`ui_patch(/connectionId, X)` 可切换；**唯一真相源** |

**为什么必须三层**：

1. Tab 工作台级（§2.3 决策）→ 跨会话常驻 → 创建时所属会话可能已删除 → 连接必须由 Tab 自持，数学上的必然
2. AI 分析路径仍要知道查哪个库，Session 保留连接属性用于对话语境
3. open-db-studio 已验证——`QueryEditorAdapter` 把 `connectionId` 作为第一级可 patch 字段、`set_context` 作为显式动作

### 2.3 StageWindow 定位升级

| 维度 | 当前 | 目标 |
|------|------|------|
| 数据模型 | 单 `activeArtifactId` 投影 | `tabs: StageTab[] + activeTabId` |
| Tab 类型 | 隐式（只有 artifact） | 显式开放字符串，由 Adapter 注册表决定 |
| 作用域 | 会话级（随 activeSessionId 切换内容） | **工具 Tab 工作台级 + Artifact Tab 会话级**（混合） |
| 对 AI 可见度 | AI 只知道当前 Artifact | AI 可 `ui_list` / `ui_read` / `ui_patch` / `ui_exec` 所有 Tab（含 `!` Tab）|
| 可扩展性 | Artifact kind 受限 | 任意 Adapter 可注册新 Tab type，**不限于 DB 场景**（业务分析笔记、独立图表、白板等） |

**作用域混合规则**（对应 brainstorm 里的 (c) 选项）：

- `query_editor`、`bang_query`、`er_canvas`、未来 `markdown_note` 等 **工具 Tab** → 工作台级，跨会话常驻；每个 Tab 记录 `originSessionId` 仅用于回溯
- `artifact` Tab（AI 分析产物）→ 会话级，随 activeSessionId 投影；切回历史会话仍能看到旧图表

### 2.4 `StageTab` 数据模型

```ts
// client/src/stores/stage-store.ts（重构后）
interface StageTab {
  tabId: string               // 稳定 ID，作为 UIObject.objectId
  type: string                // 开放字符串；Adapter 注册表决定有效值
  title: string
  connectionId?: string       // 可选：DB 系 Tab 填，非 DB Tab（笔记 / 业务分析画布）可无
  database?: string
  schema?: string
  originSessionId?: string    // 可选：artifact 必填；工具 Tab 可无
  scope: 'session' | 'workspace'  // 生命周期；Adapter 声明，不在此处覆写
  pinned?: boolean
  payload: unknown            // 由 Adapter 解释；类型安全靠 Adapter 自身守卫
  createdAt: number
}

interface StageStoreState {
  tabsBySession: Map<string, StageTab[]>       // artifact Tab
  workspaceTabs: StageTab[]                     // query_editor / bang_query / …
  activeTabIdBySession: Map<string, string | null>
  // 全局 workspace 当前焦点（跨会话保持）
  activeWorkspaceTabId: string | null
  // 显示策略：tabsOf(sid) = workspaceTabs ++ tabsBySession.get(sid)
  // activeTabOf(sid) = 取自 activeTabIdBySession，除非 UI 另切到 workspace Tab
}
```

**扩展性保证**：

- Tab 是否绑定 DB 由 Adapter 自主决定（`connectionId?` optional），预留纯业务分析类型
- 新增一种 Tab type = 新建 Adapter + 在前端注册 + （可选）后端加对应 `datatalk.*` Action，不动核心 `UIRouter`

---

## 3. UI Object 协议移植

### 3.1 协议总览

四件套保留 open-db-studio 原意，**不改变**语义：

| 工具 | 作用 | 入参 | 出参 |
|------|------|------|------|
| `ui_read(object, target?, mode?)` | 读对象状态 / schema / actions | `mode: 'state' \| 'schema' \| 'actions' \| 'full'` | `{ data }` 或 `{ error }` |
| `ui_patch(object, target?, ops, reason?)` | JSON Patch (RFC 6902) 修改状态；服务器侧校验 capability | `ops: JsonPatchOp[]`（支持 `[name=X]` 键值寻址扩展） | `{ status: 'applied' \| 'pending_confirm' \| 'error', confirm_id?, preview? }` |
| `ui_exec(object, target?, action, params?)` | 触发动作（创建 / 删除 / 批量 / 副作用） | `action: string`（在 `read('actions')` 列举过） | `{ success, data?, error? }` |
| `ui_list(filter?)` | 发现入口 | `{ type?, keyword?, connectionId?, database? }` | `UIObjectInfo[]` |

`target` 约定：`'active'`（当前焦点 Tab）或具体 `objectId`（StageTab.tabId）。

### 3.2 三层架构下的桥接

```
┌────────────────────┐  action.invoke(ui_read/patch/exec/list)  ┌───────────────────────┐
│ OpenCode (LLM + MCP)│ ─────────────────────────────────────► │ Spring Boot ChannelSvc │
└────────────────────┘                                         └───────────────────────┘
                                                                         │
                                                         action.invoke（SSE）
                                                                         ▼
                                                              ┌───────────────────────┐
                                                              │ Tauri Client — getClient│
                                                              │ Handler('datatalk.ui.*')│
                                                              └───────────────────────┘
                                                                         │
                                                                  调用 UIRouter.handle()
                                                                         ▼
                                                              ┌───────────────────────┐
                                                              │  Adapter (query_editor │
                                                              │  / bang_query / ...)  │
                                                              └───────────────────────┘
```

**后端新增 4 个 CLIENT Action**（见 §5）：仅声明 schema + `Executor.CLIENT`，真正逻辑在前端 `registerClientHandler` 注入，与 `PinArtifactAction` 完全同构。

### 3.3 前端结构

```
client/src/services/ui-router/
  ├── types.ts           # UIObject / UIRequest / UIResponse / JsonPatchOp / PatchCapability
  ├── UIRouter.ts        # 单例 + resolveTarget + capability/action 校验
  ├── useUIObjectRegistry.ts  # React hook：挂载时 register，卸载时 unregister
  ├── jsonPatch.ts       # 移植 open-db-studio 简化版（支持 add/remove/replace + [name=X]）
  ├── pathResolver.ts    # 路径 pattern 匹配（`/tables/[id=<n>]/<field>`）
  └── errors.ts          # patchError / execError shape

client/src/features/stage/adapters/
  ├── WorkspaceAdapter.ts       # 伪对象：管理 tabs（open / close / focus / list）
  ├── ArtifactTabAdapter.ts     # 包装会话 artifact 为 UIObject
  ├── QueryEditorAdapter.ts     # AI 展示路径主 Adapter
  └── BangQueryAdapter.ts       # 用户 ! 直查 Adapter（QueryEditor 只读+无编辑变体）

client/src/features/actions/ui-handlers.ts   # 4 个 registerClientHandler('datatalk.ui.*', …)
```

### 3.4 关键协议细节

**patch capability 白名单**：每个 Adapter 声明 `patchCapabilities`（路径 pattern + 允许的 ops），`UIRouter.handlePatch` 先校验再转发，杜绝 AI 乱写字段。未声明则 passthrough（兜底兼容）。

**action schema 校验**：`UIRouter.handleExec` 从 `instance.read('actions')` 取定义，对 `action` 名和 `paramsSchema.required` 做预检，错误直接返回 `{error}`，不调 Adapter。

**`target='active'` 解析**：注入 `() => activeStageTabId` provider。Convention A（objectId === tabId）优先；Convention B（`tabId` 字段单独声明）兜底；最后按 type 取首个实例。

**副作用 Action 返回约定**：`run_sql` 等耗时动作立即返回 `{success: true, data: {taskId}}`，结果异步写入 Tab state；AI 后续 `ui_read` 取状态。（与 open-db-studio 的 `emit('run-sql-request')` + 独立执行通道一致。）

---

## 4. Tab 体系与 Adapter 首发集

### 4.1 首发 4 个 Adapter

#### `WorkspaceAdapter`（workspace 伪对象）

**objectId**：固定 `'workspace'`（singleton）

| 能力 | 细节 |
|------|------|
| `read('state')` | `{ tabs: [{tabId, type, title, connectionId?}], activeTabId }` |
| `exec('open', {type, connection_id?, database?, title?, payload?})` | 创建新 Tab；默认 `connection_id = session.connectionId`；返回 `{tabId}` |
| `exec('close', {target})` | 关闭指定 Tab |
| `exec('focus', {target})` | 切换焦点 |
| `read('actions')` | 枚举上述动作 schema |

**特殊约束**：`exec('open', {type})` 支持的 `type` 从全局 Adapter 类型注册表读取——新增 Tab 类型不用改 WorkspaceAdapter 代码。

#### `ArtifactTabAdapter`（现有 artifact 的包装）

- **objectId**：`artifact_<artifactId>_<version>`
- **scope**: `'session'`
- **read('state')**：`{ artifactId, version, kind, supersedesId, pinned, summary: {columns, rowCount, durationMs} }`—— **故意不含 rows**；AI 看到是 Artifact 即可通过现有 `handle://` 或 `execute_sql` 工具重跑拿数据
- **patch**：`/pinned` 可 replace，其它路径拒绝
- **exec**：`export_csv`（副作用；弹 Tauri 保存对话框）、`unpin`
- **对 AI 意义**：AI 可 `ui_list(type='artifact')` 看历史产物、`ui_exec(artifact_xxx, unpin)` 清理 pin，但**不能直接读行数据**——需要行数据时走分析路径

#### `QueryEditorAdapter`（AI 展示路径主力）

严格对齐 open-db-studio 同名 Adapter，只改执行通道：`run_sql` 内部调 `POST /api/query` 并把结果写入 Tab payload。

**patchCapabilities**：

```ts
[
  { pathPattern: '/content',      ops: ['replace'] },
  { pathPattern: '/connectionId', ops: ['replace'] },
  { pathPattern: '/database',     ops: ['replace'] },
  { pathPattern: '/schema',       ops: ['replace'] },
]
```

**Actions**：`run_sql` / `format` / `set_context` / `focus`（对齐 open-db-studio；去掉 `undo`，无 Monaco 编辑器不需要）

**read('state')**：

```ts
{
  content: string,         // SQL 文本（AI 可见）
  connectionId, database, schema,
  lastRun?: {
    columns: string[],
    rowCount: number,
    durationMs: number,
    // rows 字段刻意缺失：结果行不进 AI 上下文
    truncated: boolean     // 实际行数超 Tab 展示上限（10_000）
  }
}
```

#### `BangQueryAdapter`（`!` 直查专用）

`QueryEditorAdapter` 的只读变体：

- SQL 文本**只读**（由用户 `!` 输入初始化；AI 不能 patch `/content`）
- `patchCapabilities`: 仅 `[{ pathPattern: '/pinned', ops: ['replace'] }]`（允许 AI 固定/取消固定本 Tab）
- Actions 仅 `rerun`（重跑同一 SQL）、`focus`、`close`
- Scope: `'workspace'`，跨会话常驻
- `read('state')` 同 QueryEditor（`sql / connectionId / database / schema / lastRun?`），外加 `pinned: boolean`；**不含 rows**

> 将来若想让 AI 把 `!` 的 SQL 复用到分析路径，AI 可读 `/content`，然后自己调 `datatalk.execute_sql(sql=...)`——这是一次**显式的用户意图升级**（AI 决策"要拿行数据"），符合隔离原则。

### 4.2 扩展示例：非 DB Tab（预留）

`markdown_note`、`business_canvas` 等纯分析 Tab 只需：

1. 实现 `UIObject`：`type='markdown_note'`、不填 `connectionId`、payload 为富文本 JSON
2. 声明 `patchCapabilities`：如 `/content`、`/title`
3. 在 `WorkspaceAdapter` 的全局类型注册表登记
4. 可选：后端挂对应 action（如果需要服务端持久化）

协议本身无须改动，三层连接绑定对"无连接 Tab"自动退化——体现可扩展设计的要义。

---

## 5. 后端桥接 —— 4 个 CLIENT-Executor Action

与 `PinArtifactAction` 完全同构：服务端声明 schema、前端 handler 执行。

### 5.1 Action 清单

```java
@DataTalkAction(id = "datatalk.ui.read",  executor = Executor.CLIENT, timeoutMs = 3_000, riskLevel = RiskLevel.L1, category = Category.UI)
@DataTalkAction(id = "datatalk.ui.patch", executor = Executor.CLIENT, timeoutMs = 3_000, riskLevel = RiskLevel.L1, category = Category.UI)
@DataTalkAction(id = "datatalk.ui.exec",  executor = Executor.CLIENT, timeoutMs = 30_000, riskLevel = RiskLevel.L1, category = Category.UI)
@DataTalkAction(id = "datatalk.ui.list",  executor = Executor.CLIENT, timeoutMs = 1_000, riskLevel = RiskLevel.L1, category = Category.UI)
```

（`Category.UI` 若尚未定义，本方案同步新增；`riskLevel = L1` 因 UI 操作不直接触发 DB 变更。）

### 5.2 input/output schema

与 `UIRequest` / `UIResponse`（§3.1）一一对应。schema 只做粗粒度校验（`object` / `target` 必填）；细粒度校验在前端 `UIRouter` 侧做——符合 **校验靠近实现** 原则。

### 5.3 action_result 回传

前端 `ui-handlers.ts`：

```ts
registerClientHandler('datatalk.ui.read', async (input, ctx) => {
  const resp = await uiRouter.handle({ tool: 'ui_read', object: input.object, target: input.target ?? 'active', payload: { mode: input.mode } })
  if (resp.error) throw new Error(resp.error)
  return resp.data
})
// … patch / exec / list 同构
```

错误通过 `throw` 让 `useChannel().buildEventSink` 走既有 `client.actionResult(callId, false, undefined, {code, message})` 路径。

### 5.4 与 execute_sql 的分工

保留 `datatalk.execute_sql`（分析路径）不动。新增 `ui.exec` 的 `run_sql` action 内部**不经过** `execute_sql` Action——它直接前端调 `POST /api/query` → 本地 Tab。两条路径在代码层完全隔离，只在服务端共享 `SqlStatementGuard`。

---

## 6. `!` 直查流程

### 6.1 Composer 拦截

```ts
// client/src/features/session/prompt-composer.tsx（新增分支）
const submitText = async (raw: string) => {
  const t = raw.trim()
  if (!t || isStreaming) return

  // 新增：! 前缀直查
  if (t.startsWith('!')) {
    const sql = t.slice(1).trim()
    if (!sql) return
    await openBangQueryTab({
      sessionId: activeSessionId,
      connectionId: activeSessionConnectionId,  // 从会话读，§2.2
      sql,
    })
    setText('')
    return
  }

  // 既有 AI 分支 …
}
```

`openBangQueryTab` 封装：

1. 生成 `tabId`
2. 调 `POST /api/query` —— 共享 `SqlStatementGuard`
3. 成功：向 StageStore 添加 `bang_query` Tab（`scope: 'workspace'`），payload 存 `{sql, columns, rows, rowCount, durationMs}`
4. 失败：toast 错误，不建 Tab（避免空 Tab 污染工作台）
5. 自动 focus 新 Tab + 弹出 Stage（复用 `useStageStore.open(activeSessionId)`）

### 6.2 Tab 渲染

`BangQueryTab` 组件（新）：

- 顶部：SQL 只读展示（单行折叠，点展开看完整）+ 连接名 + 耗时 + 行数徽章 + "重跑" / "转为 QueryEditor" 按钮
- 主体：复用既有 `DataGrid`（`client/src/features/data-grid/components/data-grid.tsx`）
- 底部：无（操作都在顶栏）

### 6.3 AI 感知边界

- `ui_list()` → AI 能看到 `{objectId: 'bang_query_xxx', type: 'bang_query', title: 'select * from users', connectionId}` —— 对象存在可见
- `ui_read(bang_query_xxx, mode='state')` → 返回 `{sql, connectionId, lastRun: {columns, rowCount, durationMs, truncated}}` —— **无 rows**
- `ui_patch` → capability 列表为空（除 `/pinned`）——拒写 `/content`
- `ui_exec(bang_query_xxx, action)` → 仅 `rerun` / `focus` / `close`

若用户日后说"AI 分析一下刚才 ! 查的结果"，AI 的自然行为是：`ui_read(bang_query_xxx).sql` 拿到 SQL → 自主决定调 `datatalk.execute_sql(sql=...)`（或 `ui_exec(workspace, open, {type:'query_editor', payload:{content: sql}})` → `run_sql`）。这是**一次明确的用户授权**，既不破坏隔离原则，也给升级路径留了门。

### 6.4 错误与边界情形

| 情形 | 处理 |
|------|------|
| 没有活动连接（`activeSessionConnectionId` 为 null） | 阻止，toast 提示"请先选择数据源" |
| `!` 后为空 | 静默无操作（容错空格） |
| SQL 非 SELECT/WITH | `SqlStatementGuard` 拒绝 → 透出服务端错误信息到 toast |
| 结果超 10_000 行 | Tab state 只保留前 10_000 行 + `truncated: true`；UI 提示截断 |
| 结果单行巨大（如 JSON 大字段） | 依赖既有 DataGrid 虚拟滚动；不截断行数 |
| 同一 SQL 已有 Tab | 不去重——用户 `!` 再发就是想重查；允许多 Tab |

---

## 7. AI Prompt 改造

### 7.1 Prompt 注入位置

**TBD 实现细节**：data-talk 当前未在代码中集中管理 OpenCode 系统 prompt（grep 未命中）。P1 阶段调研：

- 通过 OpenCode `AGENT.md` 约定文件放在 session 工作目录（OpenCode 原生机制），DataTalk 在 session 创建时落盘
- 或通过 OpenCode 会话配置 API 直接下发（取决于 OpenCode v1.4.7 能力）

设计文档此处只规定 **prompt 内容结构**，具体注入通道在 P1 执行计划中决策。

### 7.2 Prompt 内容骨架

参考 `prompts/chat_assistant.txt`（open-db-studio）结构，裁剪为 data-talk 首发所需：

```markdown
# DataTalk UI Agent

You can operate the user's "Stage" — a multi-tab workspace — via four tools:
- ui_list(filter?)                          — discover tabs
- ui_read(object, target?, mode?)           — read state / schema / actions
- ui_patch(object, target?, ops, reason?)   — JSON Patch mutation
- ui_exec(object, target?, action, params?) — side-effect actions

## Decision: Display Path vs Analysis Path

- **Display**: user wants to LOOK at data ("show users table", "browse orders")
  → `ui_exec(workspace, open, {type:'query_editor'})` → `ui_patch(/content, sql)` → `ui_exec(run_sql)`
  → You will NOT receive row data. You only see `{rowCount, columns, durationMs}` in `ui_read`. This is by design.
  
- **Analysis**: user wants you to REASON about data ("分析 2026 订单趋势", "哪些用户异常")
  → Use `datatalk.execute_sql(sql=...)` — returns preview rows into your context.
  → Use when you genuinely need to see values; prefer COUNT / GROUP BY summary SQL over SELECT *.

When ambiguous, prefer display path (safer: avoids dumping large data into context).

## Object Types (first wave)

- **workspace** — Tab管理；actions: open / close / focus
- **query_editor** — SQL 编辑器 Tab；state: {content, connectionId, database, schema, lastRun?}; actions: run_sql / format / set_context
- **bang_query** — 用户 ! 直查产生的只读 Tab；state: {sql, connectionId, lastRun}; actions: rerun / focus / close
- **artifact** — 分析产物快照；read-only of summary metadata; rows require execute_sql to re-obtain

## Principles

- Discovery first: `ui_read(mode='actions')` or `mode='schema'` before new object types
- Use `ui_patch` for simple field updates; `ui_exec` for creates / deletes / batch / side-effects; when in doubt, `ui_exec`
- Never paste SQL for the user to manually copy — always write it into `query_editor` via `ui_patch`
```

### 7.3 约束与测试点

- `ui_read` 不返回 rows 是 **协议约束，不是缺陷**——Prompt 明确告知，避免 AI 以为数据缺失而反复重试
- 具体模型表现通过 §8.3 的 AI 行为回归测试验证（用 scripted Q&A 断言 AI 选对路径）

---

## 8. 分阶段落地 + 测试策略

### 8.1 Phase 1 — MVP：`!` 直查跑通 + UIRouter 基座

**范围**：

- 前端 `services/ui-router/` 全量移植（types / UIRouter / jsonPatch / pathResolver / errors）
- `WorkspaceAdapter`（最小：state + open / close / focus + actions 定义）
- `BangQueryAdapter`（read/patch/exec；仅 rerun/focus/close）
- StageStore 重构：workspaceTabs + tabsBySession
- Composer `!` 分支 + `openBangQueryTab` util
- `BangQueryTab` 组件 + StageWindow 多 Tab 渲染（Tab 条 + 内容容器）
- 4 个 CLIENT Action（`datatalk.ui.read/patch/exec/list`）+ 前端 handler
- **暂不改 AI Prompt**，AI 仍走既有 `execute_sql`——先验证 `!` 本身和 UIRouter 管道

**验收**：

- 用户在 Composer 敲 `!select 1` → Stage 自动弹 + 新 Tab + 显示 `{1:1}` + 耗时
- 用户敲 `!select * from some_big_table` → 前端直接拿结果渲染；AI 未触发
- 后端日志无 `execute_sql` 调用；AI message parts 无 `bang_query` 相关 tool-use（验证隔离）
- 关闭 Tab、切会话、重跑、错误路径均正确

**测试**：

- `ui-router/__tests__/UIRouter.test.ts` — 对齐 open-db-studio 的 UIRouter 测试集
- `ui-router/__tests__/jsonPatch.test.ts`、`pathResolver.test.ts`
- `adapters/__tests__/BangQueryAdapter.test.ts`
- 端到端：`prompt-composer.test.tsx` 新增 `!` 拦截用例
- 后端：`UiProxyActionTest`（4 个 Action 的 schema / 注册 / Executor.CLIENT 断言，对齐 `PinArtifactActionTest`）

### 8.2 Phase 2 — AI 展示路径 + QueryEditor

**范围**：

- `QueryEditorAdapter`（完整 patchCapabilities + run_sql 实现，复用 `/api/query`）
- `QueryEditorTab` 组件（带 Monaco-lite 或纯 textarea，MVP 用 textarea）
- AI Prompt 注入通道（`AGENT.md` 落盘或 OpenCode 会话配置）+ §7.2 内容
- `ArtifactTabAdapter`：把现有 artifact 作为只读 UIObject 暴露

**验收**：

- "给我看看 users 表" → AI 调 `ui_exec(workspace, open, {type:'query_editor'})` → `ui_patch(/content)` → `ui_exec(run_sql)`；Stage 显示结果；AI message 里无行数据
- "分析 2026 订单趋势" → AI 调 `execute_sql`；结果作为 artifact，AI 下一轮回复引用数据
- AI `ui_list()` 能列出当前所有 Tab 含历史 artifact 摘要

**测试**：

- `adapters/__tests__/QueryEditorAdapter.test.ts`
- AI 行为回归：scripted session fixture 模拟两条路径下的 LLM 回复，断言 tool-use 序列正确（用 WireMock FakeOpenCodeServer）

### 8.3 Phase 3 — 扩展与增量

**非必需**，按产品优先级推进：

- `ErCanvasAdapter`、`MetricFormAdapter`（若 ER / 指标场景启动）
- 非 DB Tab（`markdown_note` / `business_canvas`）—— 验证协议对业务分析场景的开放性
- AI 行为回归测试套件扩展
- Tab 持久化（localStorage → 最终服务端落 SQLite，与 session 解耦）
- `er_batch` 等批量动作

---

## 9. 风险与对策

| 风险 | 描述 | 对策 |
|------|------|------|
| AI Prompt 遵从度 | AI 未必始终按"展示 vs 分析"做对决策，可能滥调 `execute_sql` | §8.2 回归测试；必要时在 `execute_sql` 前加用户确认（利用既有风险等级机制） |
| OpenCode 版本耦合 | OpenCode 升级可能改变 action 分发协议 | 测试集锁版本；参考既有 `opencode-147-envelope-adapter-plan` 的适配经验 |
| JSON Patch 复杂度 | `[name=X]` 扩展寻址自实现易出错 | 尽量只移植当前 Adapter 实际用到的 patch 能力；参考 open-db-studio 的 `jsonPatch.test.ts` |
| UIRouter 与 Zustand 生命周期 | Adapter 依赖 store，组件卸载时需 unregister 防内存泄漏 | `useUIObjectRegistry` 严格遵循 mount/unmount 对称；集成测试用 render cycle 断言 |
| Tab 类型无限扩展导致状态爆炸 | Tab payload 异构，StageStore 体积失控 | Tab payload 限定最大体积（如单 Tab ≤2MB），超量落 `query_results` 表 + handle 引用（沿用既有机制） |
| `!` 误触发 | 用户 SQL 真以 `!` 开头（罕见，如某些 DB 的 not operator） | 仅当 `!` 后是 `select` / `with`（大小写不敏感）才走直查；否则继续作为普通消息发 AI |

---

## 10. 后续衍生设计

本文不覆盖但已预留扩展点：

1. **Tab 持久化到 SQLite**：workspace Tab 跨应用重启恢复（目前仅内存）
2. **AI 向 `!` Tab 升级的显式授权 UI**：用户可点"允许 AI 读此 Tab"按钮，临时提升 `ui_read` 返回行数据
3. **跨连接查询 Tab**：单 Tab 挂多个 connectionId（虚拟联合查询）
4. **协作 Tab**：业务分析画布、注释批注，多人共编（三期）
5. **Action 风险分级**：与 SQL Risk Classification 融合，`ui_exec` 的某些 action 也可能触发 L2/L3 确认

---

## 11. 实施入口

本文批准后产出执行计划：`docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md`（Phase 1 粒度到每个文件/测试），通过 `/plan` 流程执行。

相关引用：

- open-db-studio UI 层：`/home/wushengzhou/workspace/github/open-db-studio/src/mcp/ui/`
- open-db-studio 系统 Prompt：`/home/wushengzhou/workspace/github/open-db-studio/prompts/chat_assistant.txt`
- data-talk 现有 CLIENT Action：`server/.../actions/PinArtifactAction.java` + `client/src/features/actions/client-handlers.ts`
- data-talk 直查后端：`server/.../controller/QueryController.java` + `service/QueryApplicationService.java`
- data-talk Stage As Computer：[docs/product-specs/2026-04-17-stage-as-computer-design.md](./2026-04-17-stage-as-computer-design.md)
