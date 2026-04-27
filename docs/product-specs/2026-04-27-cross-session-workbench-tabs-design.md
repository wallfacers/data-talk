# Cross-Session Workbench Tabs —— Tab 持久化、内容索引与 `ui_find`

> 状态：Draft · 2026-04-27 · wallfacers
>
> 范围：把 StageWindow 的 Tab 集合从「按 session 切片的内存态」升级为「跨 session 持久化、可全文检索、AI 可精准定位的工作台对象」。引入 SQLite 存储 + FTS5 索引 + 新 `ui_find` action（合并替换 `ui_list`），同时落地 sidebar「Tabs」group 与系统提示注入。

---

## 1. 背景与动机

### 1.1 现状

`client/src/stores/stage-store.ts` 是纯内存态：

- `workspaceTabs[]` + `tabsBySession: Map<sid, StageTab[]>` —— 关闭客户端即失
- `useSqlWorkbenchStore` 维护每个 query_editor 的 SQL 文本、版本、edits —— 同样仅内存
- `ui_list` 仅按 title 子串过滤，无法在 Tab 正文里找内容
- AI 跨会话操作：当前 session 的 `tabsBySession` + 全局 `workspaceTabs` 混合，但缺乏「跨所有 session 检索」入口

### 1.2 §3.11 的诉求（产品总设计）

来自 [docs/product-specs/index.md §3.11](./index.md#311-工作台与跨-session-tab-协作)：

| 功能 | 目标 |
|---|---|
| 工作台跨 session 共享 | 报表 / ER / SQL 编辑器作为长生命周期工作对象，多 session 接力修改 |
| Tab 列表持久化 | 用户手动 / AI 自动打开的 Tab 默认全部持久化，重启可恢复 |
| Tab 标题与内容索引 | 在标题 + 正文上建索引，用户搜，AI 也搜 |
| AI 跨会话定位 Tab 与内容检索 | `ui_find` 对标 Claude Code bash 的 `find + grep + cat`，三段独立可组合 |
| 报表 / Dashboard 跨 session 协作 | 持久化工作对象 + 版本与变更日志 |

### 1.3 与既有契约的关系

- **不动** [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md)：`ui_read` / `ui_patch` / `ui_exec` 全部保留 `Executor.CLIENT`、协议 schema 不变
- **替换**：`ui_list`（client） → `ui_find`（server）—— hard-cut，DataTalk 是本地 MCP 唯一消费者，无外部破坏面
- **延伸**：把当时埋下的「工具 Tab 工作台级常驻」约束彻底落实为持久化 + 可检索

### 1.4 路线图坐标

[2026-04-25 Next Implementation Roadmap](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md) 的 **Task 6（下一活跃头）**。Task 7（智能运维）可并行设计；Task 8（可视化扩展）依赖本 spec 落地。

---

## 2. 决策摘要

| 编号 | 决策 |
|---|---|
| Q1 | **所有 persistent Tab 都持久化**；按 scope 区分加载策略——workspace 启动即载，session-scope 仅在 session active 时按需载；session 删除 → session-scope Tab 跟删（FK CASCADE） |
| Q2 | **混合写入策略**：metadata 立即写、payload debounce 1s、关键 action 强制 flush；**统一口子**：用户与 AI 必须穿同一 mutation API |
| Q3 | **SQLite FTS5（trigram）+ 正则 post-filter**；output_mode / headLimit / contextLines / caseInsensitive / multiline；**Java 21 虚拟线程 fan-out 并发**；多 tab read；AI parallel tool-use |
| Q4 | **archived flag + 90 天 lazy auto-archive**；hydration 仅元数据，payload lazy 加载；AI prompt 注入「最近 10 条 active + 当前 active Tab 摘要」（≤ 300 token） |
| Q5 | sidebar 新增 **`Tabs` group 同 sidebar 内追加**（与 Sessions group 同居）；inline 搜索 input；归档项收在 `[⋯]` 菜单 |
| Q6 | Tab type 注册表：`persistent: boolean` + `scope: 'workspace' \| 'session'`；`file_preview` 设为 `persistent: false`（纯内存）；`artifact_preview` 持久化 + session-scope（FK CASCADE）；`query_editor` / 未来 `er_designer` / `report_designer` 持久化 + workspace-scope |
| 架构 | **Approach Beta**：前端 SoT 在线状态、后端 SoT 持久状态；通过显式 mutation API + 持久化协调器同步；不动 UI Object Protocol CLIENT executor 契约 |
| 协议 | `ui_list` **hard-cut 替换**为 `ui_find`；`ui_find` 是 `Executor.SERVER`，覆盖 list + grep + cat 三段语义 |

---

## 3. 范围、非目标、不变量

### 3.1 范围

1. **持久化**：`persistent: true` 的 Tab type（`query_editor` / `artifact_preview` / 未来 `er_designer` / `report_designer`）写入 SQLite
2. **内容索引**：FTS5 trigram + 正则 post-filter；`extractContent(payload)` 由 type 注册表决定
3. **`ui_find` action**：替换 `ui_list`，三段独立可组合（filter / query / read）+ 四种 output mode（metadata / matches / tabs_only / count）
4. **Sidebar UI**：`<NavTabs />` group 落在 `<NavSessions />` 下方
5. **AI 提示注入**：`{{STAGE_TAB_DIGEST}}` 占位符由后端 `AgentPromptBuilder` 替换为实时摘要

### 3.2 非目标（day-1 不做）

- ❌ 多端同步 / 协作编辑（仅本地桌面 SoT）
- ❌ 完整 keystroke 级历史快照（仅当前 + lastTouchedAt + payloadVersion）
- ❌ 语义检索（embedding / vector）—— phase-2 扩展位
- ❌ `ui_find` 改 Tab（永远只读，mutation 走 `ui_patch`）
- ❌ ER / report / dashboard 等新 Tab type 的实现 —— 本 spec 只把它们注册进 type registry 占位
- ❌ 拖拽排序 sidebar Tab 行（按 `lastTouchedAt` 倒序固定）
- ❌ 命令面板（Cmd+P）跨 Tab 跳转（留给后续全局搜索 spec）
- ❌ Tab 数量分页 hydration（day-1 用 `LIMIT 1000` 兜底）

### 3.3 核心不变量

| 不变量 | 说明 |
|---|---|
| **统一口子（Single Mutation Path）** | 用户事件（Monaco / 工具栏 / context chip）和 AI 事件（`ui_patch` / `ui_exec`）必须穿 `useStageStore` 暴露的 mutation 方法（content 通过 `useSqlWorkbenchStore` delegate）。**ESLint custom rule + vitest 静态扫描** 强制；任何 `useStageStore.setState` / `useSqlWorkbenchStore.setState` 直接外部调用都是违规 |
| **持久化路径单一** | 所有持久化写入必经 `StagePersistenceCoordinator`；不允许业务代码直接 fetch `/api/stage/tabs/*` |
| **payloadVersion 单调递增** | 任何写都自增；用作乐观并发与 AI 缓存校验 |
| **`ui_find` 只读** | 任何 mutation 必须走 `ui_patch` / `ui_exec`；`ui_find` 实现层禁用所有 SQL `INSERT/UPDATE/DELETE` |
| **Force-flush 后再回应 AI** | `ui_patch` / `ui_exec` mutation handler 在返回 action_result 之前必须 `await coordinator.flush(targetTabId)`，确保 AI 自己接下来 `ui_find` 看得到自己的写 |

---

## 4. 持久化模型（SQLite + Type Registry）

### 4.1 Schema —— migration `V12__stage_tabs.sql`

```sql
-- 主表：Tab 元数据（启动时 eager 拉 workspace；session-scope 在 session active 时拉）
CREATE TABLE stage_tabs (
  id                 TEXT PRIMARY KEY,
  type               TEXT NOT NULL,
  scope              TEXT NOT NULL CHECK (scope IN ('workspace','session')),
  title              TEXT NOT NULL,
  connection_id      TEXT,                                  -- 软关联，不做 FK（连接删除不级联 Tab）
  database_name      TEXT,
  schema_name        TEXT,
  origin_session_id  TEXT,
  payload_version    INTEGER NOT NULL DEFAULT 1,
  pinned             INTEGER NOT NULL DEFAULT 0,
  archived           INTEGER NOT NULL DEFAULT 0,
  archived_at        INTEGER,
  created_at         INTEGER NOT NULL,
  last_touched_at    INTEGER NOT NULL,

  FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE CASCADE,
  CHECK (scope = 'workspace' OR origin_session_id IS NOT NULL)
);

CREATE INDEX idx_stage_tabs_active
  ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX idx_stage_tabs_type        ON stage_tabs(type, archived);
CREATE INDEX idx_stage_tabs_session     ON stage_tabs(origin_session_id) WHERE origin_session_id IS NOT NULL;
CREATE INDEX idx_stage_tabs_connection  ON stage_tabs(connection_id)     WHERE connection_id IS NOT NULL;

-- payload 单独成表：sidebar listing / hydration 不用拖大 JSON
CREATE TABLE stage_tab_payload (
  tab_id          TEXT PRIMARY KEY,
  payload_json    TEXT NOT NULL,
  content_text    TEXT NOT NULL,
  content_version INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL,
  FOREIGN KEY (tab_id) REFERENCES stage_tabs(id) ON DELETE CASCADE
);

-- FTS5 虚拟表：title + content trigram 索引
CREATE VIRTUAL TABLE stage_tab_index USING fts5(
  title,
  content,
  type      UNINDEXED,
  archived  UNINDEXED,
  tokenize  = 'trigram'
);

-- 同步触发器：以 stage_tabs.rowid 作 FTS5 rowid
CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
  INSERT INTO stage_tab_index(rowid, title, content, type, archived)
    VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
END;
CREATE TRIGGER stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
  UPDATE stage_tab_index SET title = NEW.title, archived = NEW.archived
    WHERE rowid = NEW.rowid;
END;
CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
  DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
END;
CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
  UPDATE stage_tab_index SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;
CREATE TRIGGER stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
  UPDATE stage_tab_index SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;
```

#### 关键设计点

| 决策 | 理由 |
|---|---|
| `stage_tabs.rowid = stage_tab_index.rowid` | FTS5 contentless 表习惯用 rowid 关联；省一张映射表，触发器干净 |
| CHECK 约束 `scope=session ⇒ origin_session_id IS NOT NULL` | DB 层面拒绝违规组合 |
| `connection_id` 不做 FK | 连接删除是用户配置变更，但 Tab 想保留（例如「之前连旧库的 SQL」）；UI 层识别失效后渲染为「Connection unavailable」并提示 set_context |
| trigram tokenizer | 原生支持中英文混合分词；中文 SQL 字段名（如 `用户表`）也可命中；零外部依赖 |
| payload 与 metadata 分表 | sidebar listing / `output.mode=metadata` / `count` 不拉 JSON；FTS5 索引也独立维护 |
| 触发器以 stage_tabs.rowid 关联 FTS5 | INSERT 顺序：先 stage_tabs（rowid 稳定）后 payload（content 补 UPDATE）；DELETE 双重保险（payload 删 + tabs 删都触发清理） |

### 4.2 Tab Type Registry（前端 canonical）

```ts
// client/src/features/stage/registry/tab-type-registry.ts
import type { LucideIcon } from 'lucide-react'

export interface TabTypeDescriptor {
  type: string
  persistent: boolean
  scope?: 'workspace' | 'session'   // persistent=true 时必填
  icon: LucideIcon
  labelKey: string                   // i18n key
  /** 从 payload 抽取可索引文本喂给 FTS5；每次写都会调用，必须确定性 */
  extractContent: (payload: unknown) => string
  /** payload 从 SQLite 加载回来后，注入对应工作 store */
  rehydrate?: (tab: StageTab, payload: unknown) => void
}

export const TAB_TYPE_REGISTRY: Record<string, TabTypeDescriptor> = {
  query_editor: {
    type: 'query_editor',
    persistent: true,
    scope: 'workspace',
    icon: FileEditIcon,
    labelKey: 'tabType.queryEditor',
    extractContent: (p) => (p as { sqlText?: string })?.sqlText ?? '',
    rehydrate: (tab, p) => {
      const payload = p as { sqlText?: string; version?: number }
      useSqlWorkbenchStore.getState().ensureTab(tab.tabId, {
        sqlText: payload.sqlText ?? '',
        source: 'user',
        initialVersion: payload.version,
      })
    },
  },
  artifact_preview: {
    type: 'artifact_preview',
    persistent: true,
    scope: 'session',
    icon: FilePreviewIcon,
    labelKey: 'tabType.artifactPreview',
    extractContent: (p) => (p as { artifactTitle?: string })?.artifactTitle ?? '',
  },
  file_preview: {
    type: 'file_preview',
    persistent: false,                 // ephemeral，纯内存
    icon: FileTextIcon,
    labelKey: 'tabType.filePreview',
    extractContent: () => '',
  },
  workspace: {
    type: 'workspace',
    persistent: false,                 // singleton 管理对象，不入库
    icon: LayoutIcon,
    labelKey: 'tabType.workspace',
    extractContent: () => '',
  },
}
```

- **后端不感知注册表语义**：仅存 frontend POST 来的 `type` 字符串 + `scope`；新增 type = 加一行 + 实现 Adapter；零后端改动
- **`extractContent` 是 day-1 索引能力的关键**：query_editor 抽 SQL 文本；artifact_preview 抽 artifact 标题 + 列名摘要；er_designer / report_designer 未来抽字段 / 表名 / 组件 schema 序列化文本

### 4.3 生命周期与归档

| 操作 | 触发 |
|---|---|
| 新建 | mutation API → POST `/api/stage/tabs`（仅 `persistent: true`）→ INSERT stage_tabs + stage_tab_payload（同事务）→ 触发器同步 FTS5 |
| 编辑 | debounce 1s（payload）/ 立即（metadata）→ PUT `/api/stage/tabs/:id` → UPDATE → 触发器同步 FTS5；`payload_version` + `last_touched_at` 同步递增 |
| 关闭 | DELETE `/api/stage/tabs/:id` → CASCADE 删 payload + FTS5；UI 上 Tab 消失 |
| 归档 | UPDATE `archived=1, archived_at=now`；默认 `ui_find` / sidebar 过滤掉；可手动展示「已归档」 |
| Session 删除 | FK CASCADE 自动清 session-scope tabs + payload + FTS5 |
| Auto-archive | **lazy on hydration**：每次启动时 `UPDATE stage_tabs SET archived=1, archived_at=NOW() WHERE archived=0 AND last_touched_at < NOW() - 90d`；不需要 Spring `@Scheduled` cron |

---

## 5. Mutation Pipeline

### 5.1 单一 Mutation API

| 类别 | 方法（在 `useStageStore` 上） | 用户路径 | AI 路径 |
|---|---|---|---|
| **生命周期** | `openTab(spec)` / `closeTab(id)` / `focusTab(id)` / `archiveTab(id, archived)` | sidebar / 工具栏 / 关闭按钮 | `ui_exec(workspace, open/close/focus)` |
| **内容** | `replaceQueryEditorContent(id, content)` / `applyQueryEditorTextEdits(id, edits)` | Monaco onChange → `useSqlWorkbenchStore` | `ui_patch(/content)` / `ui_exec(apply_text_edits)` 都最终 delegate 到这两个 |
| **元数据** | `setQueryEditorContext(id, ctx)` / `setTabTitle(id, title)` / `setTabPinned(id, pinned)` | context chip / 工具栏 | `ui_patch(/connectionId\|/database\|/schema)` / `ui_exec(set_context)` |

#### 收口 enforcement

- **ESLint custom rule** `no-direct-stage-store-mutation`：禁止 `useStageStore.setState(` / `useSqlWorkbenchStore.setState(` 在 store 实现文件之外出现
- **vitest 静态扫描** `forbidden-direct-mutation.test.ts`：用 ts-morph 扫整个 `client/src`，断言只有 store 内部使用 `setState`
- 两层都 fail = build / test fail = PR 不能合

### 5.2 StagePersistenceCoordinator

新文件 `client/src/features/stage/persistence/stage-persistence-coordinator.ts`：

```ts
type CoordinatorPhase = 'idle' | 'hydrating' | 'live' | 'degraded'

class StagePersistenceCoordinator {
  private phase: CoordinatorPhase = 'idle'
  private metaTimers = new Map<string, ReturnType<typeof setTimeout>>()
  private contentTimers = new Map<string, ReturnType<typeof setTimeout>>()
  private pendingDuringHydration: Array<() => void> = []
  private hydrationCache = new Map<string, Promise<void>>()

  async start(): Promise<void> {
    this.phase = 'hydrating'
    await this.hydrateWorkspaceMeta()                                  // GET /api/stage/tabs?scope=workspace
    this.subscribeStores()                                              // 启动订阅
    this.phase = 'live'
    this.flushQueued()
    void this.lazyAutoArchive()                                         // 不阻塞首屏
  }

  /** 对单 tab 立即把 metadata + content 都同步落盘并等返回 */
  async flush(tabId: string): Promise<void> { /* 取消 debounce + sync PUT */ }

  /** 加载 payload；幂等；同 tabId 并发只发一次 fetch */
  async ensureHydrated(tabId: string): Promise<void> { /* GET /api/stage/tabs/:id/payload + rehydrate */ }

  /** 全量 flush（app quit / window close） */
  async flushAll(): Promise<void> { /* */ }
  flushAllSync(): void { /* navigator.sendBeacon 兜底 */ }
}
```

订阅契约：

```ts
// 元数据：StageStore tab 列表 → 立即写
useStageStore.subscribe(
  (s) => collectPersistentTabMeta(s),
  (next, prev) => coordinator.diffAndPersistMeta(next, prev),       // 0ms
  { equalityFn: shallow }
)
// 内容：useSqlWorkbenchStore 文本 → debounce 1s 写
useSqlWorkbenchStore.subscribe(
  (s) => s.tabs,
  (next, prev) => coordinator.scheduleContentWrite(next, prev),    // debounce 1000ms
  { equalityFn: shallow }
)
```

### 5.3 Force-flush 时机

| 触发点 | 行为 |
|---|---|
| `ui_patch` handler 返回前 | `await coordinator.flush(targetTabId)` |
| `ui_exec(set_context\|apply_text_edits)` 返回前 | `await coordinator.flush(targetTabId)` |
| `ui_exec(query_editor, run_sql)` 执行前 | `await coordinator.flush(targetTabId)`（确保跑的是最新内容） |
| Tab close | `await coordinator.flush(tabId)`；然后 DELETE |
| App quit / window close | `flushAllSync()` + `navigator.sendBeacon` 兜底 |

handler 包装示例：

```ts
registerClientHandler('datatalk.ui.patch', async (input) => {
  const target = resolveTarget(input)
  await coordinator.ensureHydrated(target)         // ← 先把 payload 拉回内存
  const res = await uiRouter.handle(...)
  if (res.success) await coordinator.flush(target)
  return res
})
```

`ui_read` / `ui_exec` 同样在入口 ensureHydrated。AI 完全不感知 lazy load。

### 5.4 后端 HTTP 契约

| 端点 | 用途 | 调用方 |
|---|---|---|
| `PUT /api/stage/tabs/{id}` | upsert metadata +/或 payload；body 含 `payloadVersion`（乐观并发，HTTP `If-Match` 头） | StagePersistenceCoordinator |
| `DELETE /api/stage/tabs/{id}` | 硬删（CASCADE → payload + FTS5） | coordinator |
| `PATCH /api/stage/tabs/{id}/archive` | 切归档位 | sidebar 右键 |
| `GET /api/stage/tabs?scope=workspace&archived=false` | 冷启动 hydration 拉元数据 | coordinator.start() |
| `GET /api/stage/tabs?scope=session&originSessionId=X&archived=false` | 切到 session X 时拉 session-scope tabs | session 切换钩子 |
| `GET /api/stage/tabs/{id}/payload` | lazy 加载单 Tab payload | ensureHydrated |
| `POST /api/stage/find` | `ui_find` 业务逻辑 | sidebar 搜索 + `UiFindAction`（同 service） |

后端模块布局：

```
server/data-talk-domain/         StageTab record + StageTabScope enum
server/data-talk-application/    StageTabService（CRUD + 乐观并发 + lazy auto-archive）
                                 StageFindService（ui_find 业务逻辑，含虚拟线程并发）
                                 AgentPromptBuilder（{{STAGE_TAB_DIGEST}} 注入）
server/data-talk-infrastructure/ StageTabJdbcRepository
                                 StageTabIndexer（FTS5 query 端封装）
                                 db/migration/V12__stage_tabs.sql
server/data-talk-adapter/        controller/StageTabController（HTTP 端点）
                                 controller/StageFindController（POST /api/stage/find）
                                 actions/UiFindAction（Executor.SERVER）
                                 删 actions/UiListAction.java
```

`StageFindService` 是 `UiFindAction`（AI 通道）和 `StageFindController`（用户 sidebar 通道）的共享业务层 —— 一份逻辑，两个入口。

### 5.5 Hydration 序列

```
App boot
  └─ AuthGate ready
       └─ StagePersistenceCoordinator.start()
            ├─ phase = hydrating
            ├─ GET /api/stage/tabs?scope=workspace&archived=false
            ├─ for each tab: useStageStore.dispatch(__hydrateMeta(tab))
            │        payload 标 unloaded sentinel
            ├─ subscribeStores()
            ├─ phase = live → flushQueued()
            └─ async lazyAutoArchive()
sidebar Tabs group 立即渲染（基于 metadata）

Session 切到 A
  └─ GET /api/stage/tabs?scope=session&originSessionId=A
       └─ useStageStore.dispatch(__hydrateSessionMeta(A, tabs))

User focus tab X (or AI ui_read/ui_patch on X)
  └─ coordinator.ensureHydrated(X)
       ├─ if cached: return
       ├─ GET /api/stage/tabs/X/payload
       └─ TAB_TYPE_REGISTRY[X.type].rehydrate(tab, payload)
              query_editor → useSqlWorkbenchStore.ensureTab(X, {sqlText, version})
```

**冷启动性能预算**：1000 tabs 元数据 SELECT < 50ms（带索引）；首屏渲染 < 100ms；payload lazy 加载 < 30ms/tab。

### 5.6 一致性保证

| 场景 | 保证 |
|---|---|
| 用户敲 SQL 中途崩溃 | 最近 ≤ 1s 的内容可能丢；payload_version 跟内存一致；重启恢复到上一次 debounce flush |
| AI `ui_patch` → 立即 `ui_find` | force-flush 保证 SQLite + FTS5 已同步；AI 100% 看到自己的写 |
| 用户 session A 改 Tab X，AI session B 同时 `ui_patch` Tab X | payload_version 乐观并发：HTTP 409 + currentState；AI 重读再 patch（与现有 `apply_text_edits` 一致） |
| Session 删除 | FK CASCADE 自动清；workspace-scope 不动 |
| Connection 删除 | 不级联；UI 渲染失效 connection 时显示「Connection unavailable」+ 提示 set_context |

---

## 6. `ui_find` Action 契约

### 6.1 注册元数据

```java
@DataTalkAction(
    id = "datatalk.ui.find",
    executor = Executor.SERVER,
    description = "action.ui_find.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.UI }
)
```

MCP 暴露名：`datatalk_ui_find`（替换 `datatalk_ui_list`）。

### 6.2 Input Schema

```jsonc
{
  "type": "object",
  "properties": {
    "filter": {
      "type": "object",
      "description": "Metadata filters (AND-combined). All fields optional.",
      "properties": {
        "type":               { "type": "string" },
        "connectionId":       { "type": "string" },
        "objectId":           { "type": "string" },
        "originSessionId":    { "type": "string" },
        "lastTouchedAfter":   { "type": "integer" },
        "lastTouchedBefore":  { "type": "integer" },
        "includeArchived":    { "type": "boolean", "default": false },
        "pinned":             { "type": "boolean" }
      }
    },
    "query": {
      "type": "object",
      "description": "Content match (omit for metadata-only listing).",
      "properties": {
        "mode":             { "type": "string", "enum": ["substring", "regex", "fts"] },
        "pattern":          { "type": "string" },
        "caseInsensitive":  { "type": "boolean", "default": true },
        "multiline":        { "type": "boolean", "default": false }
      },
      "required": ["mode", "pattern"]
    },
    "read": {
      "type": "object",
      "description": "Read content from named tabs (optionally combined with filter+query).",
      "properties": {
        "tabIds":       { "type": "array", "items": { "type": "string" } },
        "range": {
          "oneOf": [
            { "type": "string", "enum": ["full"] },
            {
              "type": "object",
              "properties": {
                "lineStart": { "type": "integer", "minimum": 1 },
                "lineEnd":   { "type": "integer", "minimum": 1 }
              },
              "required": ["lineStart", "lineEnd"]
            }
          ],
          "default": "full"
        },
        "contextLines": { "type": "integer", "minimum": 0, "default": 0 }
      }
    },
    "output": {
      "type": "object",
      "properties": {
        "mode":      { "type": "string", "enum": ["metadata", "matches", "tabs_only", "count"], "default": "metadata" },
        "headLimit": { "type": "integer", "minimum": 1, "default": 100 },
        "maxTabs":   { "type": "integer", "minimum": 1, "default": 50 }
      }
    }
  }
}
```

#### 三段独立可组合

| 组合 | 类比 | 行为 |
|---|---|---|
| `filter` 单独 | `ls` / `find -name` | 列元数据；`output.mode=metadata` 默认 |
| `filter + query` | `grep -l` / `grep -n` | 在 filter 命中的 tab 上做内容搜索 |
| `read.tabIds` 单独 | `cat` / `sed -n 'A,Bp'` | 直接读指定 tab 的指定范围 |
| `filter + query + read` | `grep -A N` | 命中 + 自动取 contextLines 上下文 |

### 6.3 Output Schema

#### `mode: 'metadata'`（替换 ui_list 等价行为）

```jsonc
{
  "items": [
    {
      "objectId": "query_editor_a1b2",
      "type": "query_editor",
      "scope": "workspace",
      "title": "Sales Aggregate",
      "connectionId": "conn-prod",
      "database": "sales",
      "schema": "public",
      "originSessionId": "ses-x",
      "pinned": false,
      "archived": false,
      "payloadVersion": 17,
      "lastTouchedAt": 1714200000000
    }
  ],
  "totalMatched": 142,
  "truncated": true
}
```

#### `mode: 'matches'`

```jsonc
{
  "items": [
    {
      "tab": { /* metadata shape */ },
      "matches": [
        {
          "lineNumber": 14,
          "byteOffset": 312,
          "line": "WHERE u.email LIKE '%@example.com'",
          "columnStart": 9,
          "columnEnd": 16,
          "before": ["FROM users u", "  JOIN orders o ON ..."],
          "after":  [")", "ORDER BY o.created_at"]
        }
      ],
      "matchScore": 8.42                              // bm25 分（仅 fts mode）
    }
  ],
  "totalMatched": 7,
  "truncated": false
}
```

#### `mode: 'tabs_only'`

```jsonc
{ "tabIds": ["query_editor_a1b2", "query_editor_c3d4"], "totalMatched": 12, "truncated": true }
```

#### `mode: 'count'`

```jsonc
{ "totalMatched": 47, "tabsMatched": 12 }
```

#### `read` 结合任意 mode（顶层加 `reads`）

```jsonc
{
  // 上面其中一种 mode 的结果 +
  "reads": [
    {
      "tabId": "query_editor_a1b2",
      "type": "query_editor",
      "payloadVersion": 17,
      "range": "full" | { "lineStart": 1, "lineEnd": 80 },
      "content": "SELECT u.id, u.email\nFROM users u\nWHERE ...",
      "totalLines": 80
    }
  ]
}
```

### 6.4 实现细节（StageFindService）

1. **元数据过滤** → `SELECT FROM stage_tabs WHERE ... LIMIT maxTabs` 走 `idx_stage_tabs_active` / `idx_stage_tabs_type`
2. **content 匹配**：
   - `mode=fts`：`SELECT rowid, bm25(stage_tab_index) FROM stage_tab_index WHERE stage_tab_index MATCH ? AND archived = ?`
   - `mode=substring`：FTS5 trigram 粗筛 → JOIN payload `WHERE LOWER(content_text) LIKE LOWER(?)`
   - `mode=regex`：FTS5 粗筛 → fan-out 到虚拟线程池并行扫描每个候选 payload；正则用 `Pattern.compile()` cache，单 pattern 5s 兜底，外层 timeoutMs=5_000 兜底
3. **多 tab read**（`read.tabIds.size() > 1`）：虚拟线程并发 `SELECT payload_json FROM stage_tab_payload WHERE tab_id IN (...)`；range 切分在内存
4. **headLimit 截断**：TopK heap by bm25 score
5. **Safety 上限（硬编码）**：单次响应总 byte ≤ 256KB；正则 pattern ≤ 200 chars；contextLines ≤ 20；tabIds ≤ 200

### 6.5 错误模型

| code | 触发 | AI 处理建议 |
|---|---|---|
| `invalid_pattern` | 正则编译失败 / 过长 | 返回 `{error: {code, message}}`，AI 降级 substring |
| `tab_not_found` | `read.tabIds` 含不存在 id | 跳过该 id；不阻塞其它 |
| `archived_tab_skipped` | filter 没设 includeArchived 但 read.tabIds 命中已归档 | warning 字段提示；不阻塞 |
| `timeout` | 5s 超时 | 返回部分结果 + `truncated: true` + warning |
| `payload_too_large` | 单 tab payload > 1MB | 截断到 1MB + warning |

---

## 7. Sidebar UI 与 AI Prompt 注入

### 7.1 `<NavTabs />` Group 结构

落在 `client/src/features/workspace/components/app-sidebar.tsx` 的 `<NavSessions />` 下方：

```
┌─ AppSidebar ─────────────────────┐
│ [DatabaseIcon] Connection        │
│ [+ 新会话]                        │
│                                  │
│ ◢ Sessions                       │  ← 既有，不动
│   • Session A / B                │
│                                  │
│ ◢ Tabs                  [⋯]      │  ← 新增 group（[⋯] 含「显示已归档」开关）
│   ┌─────────────────────┐        │
│   │ 🔍 搜索 Tab...       │        │  ← inline search input（debounce 200ms）
│   └─────────────────────┘        │
│   📝 Sales Aggregate    SQL      │
│   📝 Order Trend Q1     SQL      │
│   📊 Revenue Dashboard  ER       │  ← 未来 type
│   📐 Customer ER        DESIGN   │
└─────────────────────────────────┘
```

#### Tab 行结构

```tsx
<TabRow>
  <TypeIcon />                     {/* 注册表 icon */}
  <Title />                        {/* 1 行截断；hover tooltip 含 connectionId / database */}
  <TypeBadge variant="ghost">      {/* 'SQL' / 'ER' / 'DASHBOARD'，无障碍辅助 */}
</TabRow>
```

#### 交互

- **点击行** → `useStageStore.focusTab(id)` + `openStage()`；如果 Tab 是 session-scope 且不在当前 session，先切到 originSessionId 再 focus
- **右键菜单**：重命名 / 归档 / 删除 / 在新 stage 中打开（预留）
- **键盘**：sidebar group 内 ↑↓ 导航 + Enter 打开
- **拖拽 / 命令面板** day-1 不做

#### 视觉契约（client/DESIGN.md tokens）

| 元素 | token |
|---|---|
| group 容器 | `bg.subtle` + `border.subtle` |
| 行 idle | `text.muted`（标题）+ `text.soft`（badge） |
| 行 hover | `interaction.hover` |
| 行 active（focused tab） | `interaction.selected` + `text.strong` + 左侧 2px `accent.primary` indicator |
| 行 archived | opacity 0.6 + `text.soft` |
| 搜索输入 idle | `bg.canvas` + `border.default` |
| 搜索输入 focus | `interaction.focusRing` |
| 搜索命中 substring 高亮 | `accent.primarySurface` 背景 + `accent.primary` 字 |
| 切换 / focus 动效 | `motion.normal (180ms)` + `easing.standard`（仅 state confirm） |

### 7.2 Sidebar 搜索行为

```ts
// 用户输入「email」时
{
  filter: { includeArchived: showArchived },
  query: { mode: 'fts', pattern: 'email', caseInsensitive: true },
  output: { mode: 'metadata', headLimit: 50 }
}
```

- 空输入 → `query` 不传，按 `lastTouchedAt` 倒序列 active tab
- 命中态 → Tab 行标题 highlight 命中字符
- 「显示已归档」toggle → `filter.includeArchived = true`

性能预算：sidebar 搜索端到端 < 80ms。

### 7.3 AI 系统提示注入

**机制**：后端 `AgentPromptBuilder` 在生成 OpenCode session config 时，把 AGENTS.md 模板里的 `{{STAGE_TAB_DIGEST}}` 占位符替换为实时数据。OpenCode 每个 turn 都会 reload prompt → 自动刷新。

**注入内容**（≤ 300 token）：

```md
## Open Tabs Snapshot

Active tab: query_editor_a1b2 ("Sales Aggregate", connection=conn-prod, database=sales)

Recently-touched tabs (top 10 by lastTouchedAt, archived excluded):
1. query_editor_a1b2  Sales Aggregate            (sales · public)
2. query_editor_c3d4  Order Trend Q1             (sales · public)
3. query_editor_e5f6  User Cohort Analysis       (analytics · public)
4. artifact_preview_a7 Revenue chart Q1          (sales · public)
... [up to 10]

Total persisted tabs: 47 active, 8 archived.
Use `datatalk_ui_find` to locate tabs not listed above; the snapshot caps at 10 entries to save tokens.
```

**生成 SQL**：

```sql
SELECT id, type, title, connection_id, database_name, schema_name, last_touched_at
FROM stage_tabs WHERE archived = 0
ORDER BY last_touched_at DESC LIMIT 10;
-- + active tab（来自 frontend session state，由 channel 传给 AgentPromptBuilder）
-- + totals: COUNT(*) FILTER archived=0 / archived=1
```

**约束**：

- 单 tab 行 ≤ 80 字符（标题截断）
- 总注入 ≤ 1500 chars（≈ 300 tokens）
- 所有动态字段 escape：`\n` / 反引号 / `<!--` / `{{` / 三冒号块开头都清理（防 prompt injection 通过 title 注入指令）

### 7.4 AGENTS.md 模板补丁（`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`）

| 章节 | 改动 |
|---|---|
| §"Core Rules" 第 14 行 | `datatalk_ui_list` → `datatalk_ui_find` |
| §"Registered Actions § UI Actions" | 删 `datatalk_ui_list`；新增 `datatalk_ui_find`（描述：Discover, search, and read tabs across all sessions） |
| §"Exact UI Contract" | 删 ui_list 段；新增 ui_find 完整契约段（filter / query / read / output 四段、bm25 排序、headLimit / maxTabs 默认、payloadVersion 用法） |
| §"UI Navigation Rules" | 8 处 `datatalk_ui_list` 全替换为 `datatalk_ui_find` 对应组合（绝大多数变成 `ui_find` + `filter.type=query_editor` + `output.mode=metadata`） |
| §"Recommended Workflows" | 4 处 workflow 同上替换；额外补「Locate text inside an existing tab」新 workflow（用 `ui_find` 的 `query+read` 组合） |
| 文件末尾 | 新增 §"Tab Persistence and Search"（详见下方）+ `{{STAGE_TAB_DIGEST}}` 占位符段 |

新增 §"Tab Persistence and Search"：

```md
## Tab Persistence and Search

Tabs persist across sessions and across app restarts. The same tab id always identifies the same logical work object.

`datatalk_ui_find` covers three composable verbs from the Unix shell:

- **list (find -name)**: pass `filter` only. Returns metadata for tabs matching type, connection, etc.
- **search (grep)**: pass `filter + query`. Returns tabs whose content matches a pattern. Use `query.mode=fts` for word-level search (default), `regex` for structural patterns, `substring` for literal exact matches.
- **read (cat)**: pass `read.tabIds`. Returns content (full or by line range), optionally with `contextLines` around hits.

Combine them: `filter + query + output.mode=tabs_only` is `grep -l`; `filter + query + read` is `grep -A` with auto-read of hits.

Output budget rules:
- Default `output.headLimit=100` matches and `output.maxTabs=50` tabs explored.
- For "is there a tab with X" questions, use `output.mode=count` (cheapest) or `tabs_only`.
- Only request `mode=matches` when you actually need to see the matching lines.
- Avoid full payload reads of many tabs at once; the assistant context budget is finite.

Concurrency:
- The server fans out across tabs in parallel. You can also issue multiple parallel `datatalk_ui_find` calls for different patterns; results don't share state.
- After mutating a tab via `datatalk_ui_patch` or `datatalk_ui_exec apply_text_edits`, the change is immediately visible to subsequent `datatalk_ui_find` calls. No retry loop is needed.
```

### 7.5 i18n

| key | 用途 |
|---|---|
| `sidebar.tabs.title` | "Tabs" / "工作台" |
| `sidebar.tabs.search.placeholder` | "Search tabs..." / "搜索 Tab..." |
| `sidebar.tabs.archive.toggle` | "Show archived (N)" / "显示已归档 (N)" |
| `sidebar.tabs.empty` | "No tabs yet. Open one from chat or click + ." |
| `sidebar.tabs.contextMenu.{rename,archive,delete,openInNewStage}` | 右键菜单 |
| `tabType.{queryEditor,artifactPreview,filePreview,workspace}` | type badge label |
| `action.ui_find.description` | 后端 i18n（同样新增 zh-CN 副本） |

---

## 8. Migration、Error Handling、Risks

### 8.1 Migration 与冷启动兼容

- 单文件 `V12__stage_tabs.sql`；老用户首次启动空 stage_tabs 表
- **没有数据迁移**：现状 StageStore 是纯内存态；新版本启动后空白起步，用户继续打开 Tab 时自动持久化
- `StagePersistenceCoordinator.start()` 必须在 `useStageStore` 实例化后、首次 subscribe 触发前完成 hydration —— `phase: 'hydrating'` 期间 PUT 排队，`'live'` 后 flush 排队
- React 层面：`<StageProvider>` 包裹 app root，`hydrating` 期间显示骨架；`live` 后 fire 实际 UI
- 后端 `/api/stage/*` 5xx → coordinator 进入 `degraded` mode，本地继续工作但不再 PUT；UI 顶部显示「持久化降级中」；恢复后自动 `flushAll()`

### 8.2 Error Handling 边界

| 场景 | 行为 |
|---|---|
| 用户开 Tab + 立即关客户端（< 1s 内） | metadata 已写；payload 可能丢；重启后 Tab 在但 content 空 |
| `ui_patch` 命中 unhydrated tab | handler 自动 ensureHydrated；AI 透明 |
| `ui_patch` 与用户同时编辑 | If-Match 409 → AI 重 read + retry（`apply_text_edits` 已有机制） |
| Session 删除含 session-scope tabs | FK CASCADE 自动清；coordinator 同步本地内存 |
| Connection 删除 → tab 仍引用 | tab 不级联；UI 显示「Connection unavailable」 |
| stage_tabs 表损坏 / VFS 错误 | 后端 5xx；coordinator degraded；用户继续工作但持久化暂停 |
| FTS5 索引落后 / 损坏 | 提供 dev-only `POST /api/stage/index/rebuild`；day-1 不做 UI 入口 |
| 单 Tab payload > 1MB | PUT 拒绝 413；coordinator 标 `oversized`；Monaco 内仍可编辑 |
| Tab 数 > 10000 | `LIMIT 1000` hydration 兜底；UI 提示「请清理或归档」 |

### 8.3 Risks & Mitigations

| 风险 | 影响 | 对策 |
|---|---|---|
| 「统一口子」执行不彻底，新代码绕路写 store | AI / 用户 view 不一致；最难调试 bug | ESLint custom rule + vitest 静态扫描双门禁；code review 显式 check |
| Tab 数随时间无限累积 | 首屏慢、搜索慢 | 90 天 lazy auto-archive；硬上限 10000 触发 UI 警告 |
| Trigram 召回率低于用户预期（中文「邮箱」找不到「邮件地址」） | 用户失望 | day-1 明确是字面 / 子串级搜索，语义检索是 phase-2；AGENTS.md + UI tooltip 解释 |
| AI 滥用 `mode=matches` + 大 contextLines 导致 token 爆炸 | OpenCode 上下文饱和 | output.headLimit / maxTabs 默认；contextLines ≤ 20；AGENTS.md 显式建议「先 count / tabs_only，再 matches」 |
| Prompt injection through tab title | AI 被劫持 | digest 生成时 escape；title 截断 80 字符 |
| force-flush 失败（网络抖动）AI 看不到自己的写 | AI 行为漂移 | flush 失败 → handler 返回 `flush_failed` 警告，AI 看到后会重试或主动 ui_find；不静默吞错 |
| sidebar 渲染 1000 行掉帧 | 大量 Tab 时 UI 卡顿 | virtualization 排除（roadmap）；用 LRU 默认渲染 top 50 + 「加载更多」按钮 |
| 用户在已删 connection 的 Tab 上点 run_sql | 困惑的错误 | handler 校验 connection 存在；不存在则 friendly error + 引导 set_context |

---

## 9. Testing Strategy

### 9.1 后端（JUnit 5 + AssertJ + WireMock + MockMvc）

| 测试 | 覆盖 |
|---|---|
| `StageTabRepositoryTest` | CRUD、FK CASCADE、CHECK 约束、payload_version 单调 |
| `StageFindServiceTest` | 每 query mode、每 output mode、headLimit / maxTabs 截断、contextLines、archived 过滤、正则 ReDoS 兜底、payload_too_large、虚拟线程 fan-out |
| `StageTabIndexerIT` | FTS5 trigger 同步、trigram 中英文混合命中 |
| `UiFindActionTest` | schema、Executor.SERVER 注册、handler→service 契约 |
| `StageTabControllerIT` | HTTP 端点（含 If-Match 乐观并发） |
| `AgentPromptContractTest` | `{{STAGE_TAB_DIGEST}}` 替换正确；datatalk_ui_find 已注册；datatalk_ui_list 已退场 |
| `AgentPromptBuilderTest` | digest 生成 + escaping（title 含反引号 / 换行 / `:::` 都被转义） |

### 9.2 前端（vitest + Testing Library）

| 测试 | 覆盖 |
|---|---|
| `stage-store.test.ts` | mutation API 全套；订阅触发 coordinator 一次（spy） |
| `stage-persistence-coordinator.test.ts` | debounce 1s 合并写、metadata 立即写、force-flush 取消 pending、ensureHydrated 幂等并发、phase=hydrating 排队、`live` 后 flush、degraded 模式 |
| `tab-type-registry.test.ts` | 每种 type 的 extractContent / rehydrate |
| `nav-tabs.test.tsx` | sidebar group 渲染、点击 focus、归档隐藏 / 显示、键盘导航、search 命中高亮、empty state |
| `ui-handlers.test.ts` | ui_patch / ui_exec / ui_read 入口都调 ensureHydrated；ui_patch 后调 flush |
| `forbidden-direct-mutation.test.ts` | **收口测试**：ts-morph 静态扫整个 `client/src`，断言 `useStageStore.setState` / `useSqlWorkbenchStore.setState` 仅在 store 实现文件出现 |
| `UIRouter.test.ts` | 删 list 相关 case；保 read/patch/exec |
| `stage-find-api.test.ts` | sidebar 搜索 debounce 200ms、抖动合并、空查询、archived 切换 |

### 9.3 AI 行为回归（FakeOpenCodeServer + WireMock）

| 测试 | 覆盖 |
|---|---|
| `StageTabSearchScenarioIT` | 「打开 sales 库的 SQL 编辑器」→ AI tool sequence 正确；「找含 users.email 的 SQL」→ AI 调 `ui_find query.mode=fts`；「改字段」→ AI 先 `ui_find` 锁定 → `ui_read mode=full` → `ui_exec apply_text_edits` → 后续 `ui_find` 立即看到改动（force-flush 验证） |
| `StageTabPromptDigestIT` | 注入 `{{STAGE_TAB_DIGEST}}` 后 AI 在「我之前看的那个表」类指代下不调 ui_find 直接命中近期 tab id |

### 9.4 ESLint Custom Rule

新文件 `client/eslint-rules/no-direct-stage-store-mutation.js`：

- 禁止 `useStageStore.setState(` 与 `useSqlWorkbenchStore.setState(` 在 store 实现文件以外出现
- 配置 `client/.eslintrc.cjs`：仅在 `client/src/stores/stage-store.ts` 与 `client/src/features/stage/stores/sql-workbench-store.ts` 内允许
- 同名规则在 vitest forbidden-direct-mutation 形成双重门禁

---

## 10. 实施 Phase（执行 Agent 参考）

day-1 是单次落地 spec；执行计划可切 4 个 batch：

1. **Batch S（schema + repo）**：V12 migration + StageTab repo + service + UiFindAction（仅 metadata mode）+ HTTP controller；后端单测通
2. **Batch P（persistence pipeline）**：StagePersistenceCoordinator + ensureHydrated + force-flush + StageStore mutation API 收口；前端单测通；AGENTS.md 静态部分替换 ui_list → ui_find
3. **Batch F（full ui_find）**：query/read/output 完整契约 + FTS5 trigram + 虚拟线程 fan-out + 正则；后端单测 + AI 行为回归通
4. **Batch U（UI surface）**：NavTabs sidebar group + 搜索 input + 归档菜单 + i18n + `{{STAGE_TAB_DIGEST}}` 注入

依赖：B-S 是其它三个的前置；B-P / B-F / B-U 内部相对独立可并行（前端 UI 可 mock 后端 endpoint 起步）。

---

## 11. 后续衍生（不在 day-1）

1. **语义检索**：本地 embedding 模型 + vector index 加进 `query.mode='semantic'`
2. **跨 Tab 全局搜索 / 命令面板**：Cmd+P 唤起，跨 session 列出最近 + 收藏
3. **Tab 分组 / 标签 / 项目**：用户主动组织（与 archive 不同维度）
4. **协作 Tab**：多人共编（依赖 day-1 的 payloadVersion 乐观并发）
5. **Tab 类型退役迁移**：未来若废弃 `query_editor` 类型，需要 spec 声明数据迁移路径（保留旧数据 / 转为冻结 archive）
6. **Tab 历史快照**：保留每次 `payload_version` 变更的内容，做版本对比 / 回滚
7. **AI 主动 Tab 整理建议**：「这两个 SQL 编辑器内容近似，建议合并」

---

## 12. 引用

- 总设计 §3.11：[docs/product-specs/index.md §3.11](./index.md#311-工作台与跨-session-tab-协作)
- 路线图 Task 6：[docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md §Task 6](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md)
- Stage UI Object Protocol：[2026-04-20-stage-ui-object-protocol-design.md](./2026-04-20-stage-ui-object-protocol-design.md)
- Client Design Contract：[client/DESIGN.md](../../client/DESIGN.md)
- 当前 StageStore：`client/src/stores/stage-store.ts`
- 当前 AGENTS.md 模板：`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- 当前 UI Object 协议前端：`client/src/services/ui-router/`
- 当前 UI handlers：`client/src/features/actions/ui-handlers.ts`

---

## 13. 实施入口

本 spec 批准后产出执行计划：`docs/exec-plans/2026-04-27-cross-session-workbench-tabs-plan.md`，由 `superpowers:writing-plans` skill 生成。计划结构按 §10 Batch S/P/F/U，含每文件 / 每测试粒度。
