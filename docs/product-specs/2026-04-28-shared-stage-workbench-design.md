# Shared Stage Workbench —— 工作台脱离 session、IDEA 风格库/工作集分离 与 多 session 并发写

> 状态：Draft · 2026-04-28 · wallfacers
>
> 范围：在 [Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-design.md) 的持久化 + `ui_find` 基础上，把 Stage（工作台）整体从「按 session 切片的 UI 状态」升级为「全局共享 + IDEA 风格 库/工作集分离 + 多 session 并发写安全」。同时把 `ui_xxx` 协议升级为 `expectedText` 指纹 + `error.markdown` 友好反馈，并同步重写 `AGENTS.md` 的 UI 协议章节。

---

## 1. 背景与动机

### 1.1 现状

| 维度 | 现状 |
|---|---|
| Stage 视图状态 | `useStageStore` 内 9 个 session-keyed 字段（实际命名 `openBySession: Map<sid, boolean>` / `maximizedBySession: Map<sid, boolean>` / `autoOpenedSessions: Set<sid>` / `sidebarCollapsedBySession: Map` / `sidebarSelectionBySession: Map` / `resourceTreeExpandedBySession: Map` / `activeRailPanelBySession: Map` / `tabsBySession: Map` / `activeTabIdBySession: Map`） |
| Tab scope | `StageTabScope.{WORKSPACE, SESSION}` 二态；`session` scope 通过 FK CASCADE 跟随 session 删除；`workspace` scope 跨 session 但 UI 仍按 session 切片显示 |
| 顶部 chrome | StageWindow 顶部 tab 栏一段式；左 rail 不存在；`Tabs` group 在 sidebar |
| 编辑并发 | `apply_text_edits` 有 `baseVersion`；`replace /content` 无并发保护；冲突反馈是结构化 `currentState`，无 markdown |
| AI 协议 | AGENTS.md 仍叙述 workspace / session 双轨语义，未提示并发写规则 |

### 1.2 痛点

1. **Stage 视图被 session 切片**：用户在 session A 打开 stage 并展开 schema，切到 session B 又得重做一遍——和"工作台是工具，不是会话附属"的心智冲突。
2. **`session` scope 让多 session 协作受限**：artifact_preview tab 只能在原会话看到；multi-agent 接力修改无法自然发生。
3. **顶 tab 栏一段式**："关闭" = "丢失"；想要"暂时不看，但保留上下文"做不到。
4. **`replace /content` 无 baseVersion**：两个 session 同时全量覆盖 SQL 编辑器内容，后者静默吞掉前者的工作。
5. **冲突反馈是结构化字段**：AI 看到 `currentState.version=14` 但不知道差在哪几行、谁改的、下一步该怎么走，往往机械重试或放弃。
6. **AGENTS.md 缺并发规则**：没有"先 ui_read 再编辑"、"看到 markdown 错误必须重读"等明确指令。

### 1.3 与既有设计的关系

- **承接** [Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-design.md)：持久化 + FTS5 + `ui_find` 基础不动；本 spec 在其上做 UI 形态与并发协议升级
- **不动** [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md)：`ui_read` / `ui_patch` / `ui_exec` 仍 `Executor.CLIENT`；只对 schema 做向前兼容的字段强化（强制 `baseVersion` / `expectedText`）
- **承接** [Datatalk Client Design System](./2026-04-23-datatalk-client-design-system-design.md) / `client/DESIGN.md`：所有新增组件的五态映射严格遵守 semantic token 契约
- **覆盖产品总设计 §3.11** 的剩余诉求：把"工作台跨 session 共享"从"持久化跨 session"扩成"视图全局化 + 并发写安全 + IDE 心智"

---

## 2. 决策摘要

| 编号 | 决策 |
|---|---|
| D1 | **布局**：sidebar 删除 `<NavTabs />`；StageWindow 内置三段式 = 左 rail（库）+ 顶 tab 栏（工作集）+ 内容区；A1 严格 IDEA 模型 |
| D2 | **Tab scope**：彻底取消 `scope` 字段；`originSessionId` 降级为非功能性"来源标签"，session 删除 → `ON DELETE SET NULL`；任何 session 可读可改任何 tab |
| D3 | **Stage 视图状态**：9 个 `*BySession` Map/Set 全部改单值；toggle 按钮仍需 `activeSessionId`，但与连接配置完全解耦 |
| D4 | **Edit 原语**：`apply_text_edits` 每条 edit 增 required `expectedText`；`replace /content` 增 required `baseVersion`；保留 char-precise 范围 |
| D5 | **失败反馈**：`error.code`（机器读，5 种封口枚举） + `error.markdown`（AI 友好长文）双轨；后端 `EditConflictMarkdownFormatter` 渲染 |
| D6 | **库 vs 工作集**：左 rail = 库（含未打开），顶 tab 栏 = 工作集；关闭顶 tab = `detach`，DB 不动；`archive` = 库默认隐藏；`trash` = DELETE |
| D7 | **Migration**：`V13__stage_tabs_workspace_only.sql` 单向迁移，drop `scope` 列，FK 改 `ON DELETE SET NULL`，重建索引 |
| D8 | **AGENTS.md**：UI 协议章节大改写，新增 `## Concurrency Contract` 与 `## Library vs Workset` 两段；`STAGE_TAB_DIGEST` 渲染加 `originSession` / `version` / `lastTouched` 字段（**不**含 `inWorkset`——digest 在 bootstrap 时由服务端一次性渲染、`OpenCodeBootstrapWriter.setInstructionsSupplier` 提供，服务端无客户端 `openTabIds` 视角；`inWorkset` 仅由 `ui_read state.inWorkset` 实时提供） |
| D9 | **协议增量**：`ui_exec workspace.detach` / `archive` / `trash` 三新动词；`close` deprecated alias；`workspace.focus` 自动 ensure-in-workset |
| D10 | **实施分阶**：Approach β = Backend Protocol & Migration（P1）→ Frontend Layout Migration（P2）→ State Globalization & Polish（P3）→ Cleanup（P4，可选） |

---

## 3. 范围、非目标、不变量

### 3.1 范围

1. Stage 视图状态全局化（9 个映射改单值）
2. Tab scope 取消，`originSessionId` 软标签化
3. StageWindow IDEA 风格三段式（左 rail / 顶 tab 栏 / 内容区）
4. sidebar 拆 NavTabs，左 rail 接管所有 tab 浏览/搜索/归档
5. `apply_text_edits` 强制 `expectedText`；`replace /content` 强制 `baseVersion`
6. `error.markdown` 统一返回结构 + 5 种 code
7. `workspace.detach` / `archive` / `trash` 三动词；`close` deprecated
8. AGENTS.md 重写 UI 协议章节
9. `STAGE_TAB_DIGEST` 渲染升级（仅服务端可知字段：`originSession` / `version` / `lastTouched`）
10. `V13` migration（FK SET NULL、drop scope，含 FTS trigger 重建）
11. `ui-objects-reference.md` 同步重写

### 3.2 非目标（day-1 不做）

- ❌ 多端协作 / 实时光标共享
- ❌ 工作集 `openTabIds` 持久化（重启不恢复）
- ❌ 拖拽排序左 rail / 顶 tab 栏
- ❌ 命令面板 / 全局快捷键 / Cmd+P 跨 tab 跳转
- ❌ ER / Report / Dashboard tab 类型实现（仍是 EmptyState 卡 placeholder）
- ❌ 左 rail 行的内容预览 / 缩略图（仅 title / type / origin label）
- ❌ session ↔ tab 多对多关联（`originSessionId` 是单一来源标签，不是关系表）
- ❌ AI 调用 `workspace.trash` 二次确认弹窗（Phase 1 仅靠 prompt 约束）
- ❌ AGENTS.md 多语言版本

### 3.3 核心不变量

| 不变量 | 说明 |
|---|---|
| **统一 mutation 路径** | 用户事件（Monaco / 工具栏）和 AI 事件（`ui_patch` / `ui_exec`）必须穿 `useStageStore` + `useSqlWorkbenchStore` 暴露的 mutation 方法（承接 cross-session-workbench-tabs §3.3） |
| **持久化路径单一** | 所有持久化写入必经 `StagePersistenceCoordinator`；不允许业务代码直接 fetch `/api/stage/tabs/*` |
| **payloadVersion 单调递增** | 任何写都自增；用作乐观并发与 `expectedText` 校验的快照基准 |
| **`ui_find` 只读** | 任何 mutation 必须走 `ui_patch` / `ui_exec`；`ui_find` 实现层禁用所有 SQL `INSERT/UPDATE/DELETE` |
| **Force-flush 后再回应 AI** | `ui_patch` / `ui_exec` mutation handler 在返回 action_result 之前必须 `await coordinator.flush(targetTabId)`；AI 立刻 `ui_find`/`ui_read` 看得到自己的写 |
| **Per-tab 单写序列化** | 前端 per-tab mutex + 后端协调器入队；同 tab 双写绝不交叉，冲突一定能被 `version_conflict` 拦截 |
| **整批 edits 原子性** | 一批 edits 中任一条 `expectedText` 失败 → 全批回滚，`payloadVersion` 不递增 |
| **A1 关 ≠ 删** | 顶栏 X = `detach`（库内仍存在）；只有 `archive` / `trash` 改 DB |

---

## 4. 数据模型

### 4.1 前端 `StageTab`（去 scope）

```ts
// client/src/stores/stage-store.ts
interface StageTab {
  tabId: string
  type: string                       // 'query_editor' | 'artifact_preview' | 'er_designer' | 'report_designer' | …
  title: string
  connectionId?: string
  connectionName?: string
  database?: string
  schema?: string
  originSessionId?: string | null    // 软标签：来源 session id；session 删除后置 null
  pinned?: boolean
  archived?: boolean
  archivedAt?: number | null
  payload: unknown
  payloadVersion?: number
  lastTouchedAt?: number
  createdAt: number
}
```

**变更**：

- 删除 `scope: 'session' | 'workspace'`
- `originSessionId` 类型由 `string` 改 `string | null`
- **不**新增 `originSessionTitle` 字段——来源 session 标题永远即时 join，避免 session 改名后 store 持有陈旧标题。各消费方各自查：
  - 左 rail 行渲染：用 `useSession(originSessionId)` 钩子查 react-query 缓存（已有 sessions list query 自动 invalidation）
  - `ui_find` 后端响应：`StageFindService` SQL `LEFT JOIN sessions s ON s.id = t.origin_session_id` 即时返回 `originSessionTitle` 字段（仅响应字段，不写入 store）
  - `STAGE_TAB_DIGEST` 渲染：`AgentPromptBuilder` 在 `renderDigest()` 内一次批量查 `IN (?, ?, ...)` 即时 join

### 4.2 `useStageStore` 切片（去 `*BySession`）

```ts
interface StageStoreState {
  // 全局 stage 视图状态
  open: boolean
  maximized: boolean
  autoOpened: boolean
  sidebarCollapsed: boolean
  sidebarSelection: SidebarSelection | null
  resourceTreeExpanded: string[]
  activeRailPanel: RailPanel | null
  revealOrigin: RevealOrigin | null

  // 工作台 tab 库 + 工作集（A1 模型）
  tabs: StageTab[]                    // 全部 active tab（archived 在内）
  openTabIds: Set<string>             // 顶 tab 栏工作集
  openTabIdsOrdered: string[]         // 工作集插入顺序，渲染顶栏用
  activeTabId: string | null

  // 左 rail UI 偏好
  leftRailWidth: number               // localStorage 'stage.leftRail.width'
  leftRailCollapsed: boolean          // localStorage 'stage.leftRail.collapsed'

  // 全局 stage 动作（去 sid 参数）
  openStage: () => void
  closeStage: () => void   // 副作用：autoOpened = false（恢复"下次 artifact 可再自动开一次"机会）
  toggleStage: () => void
  toggleMaximized: () => void
  syncCollapsed: (collapsed: boolean) => void
  toggleSidebarCollapsed: () => void
  setSidebarSelection: (sel: SidebarSelection | null) => void
  toggleResourceExpanded: (nodeId: string) => void
  setResourceExpanded: (ids: string[]) => void
  setActiveRailPanel: (panel: RailPanel | null) => void
  toggleRailPanel: (panel: RailPanel) => void
  notifyArtifactArrived: () => void
  setRevealOrigin: (origin: RevealOrigin | null) => void

  // Tab 库操作
  ensureOpenInWorkset: (tabId: string) => void
  detachFromWorkset: (tabId: string) => void
  focusTab: (tabId: string) => void   // = ensureOpenInWorkset + setActive
  setLeftRailWidth: (px: number) => void
  toggleLeftRailCollapsed: () => void

  // Tab CRUD
  openTab: (tab: StageTab) => void
  archiveTab: (tabId: string, archived: boolean) => void
  trashTab: (tabId: string) => Promise<void>
  // …其他保留与协议层对齐
}
```

**删除**：所有 `*BySession` Map/Set；`focusWorkspaceTabForSession`；`clear(sid)`；`clearAllSessionState`；`closeTab`（语义裂解为 `detach` / `archive` / `trash`）。

### 4.3 后端 `StageTab` Java record（去 scope）

```java
// data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTab.java
public record StageTab(
    String id,
    String type,                        // scope 字段移除
    String title,
    String connectionId,
    String databaseName,
    String schemaName,
    String originSessionId,             // soft label, may be null after session delete
    int payloadVersion,
    boolean pinned,
    boolean archived,
    Long archivedAt,
    long createdAt,
    long lastTouchedAt
) {
    public StageTab {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (payloadVersion < 1) {
            throw new IllegalArgumentException("payloadVersion must be >= 1, got " + payloadVersion);
        }
    }
}
```

**删除**：`StageTabScope` enum 整个文件；构造校验里的 `scope == SESSION → originSessionId required` 约束。

### 4.4 `V13__stage_tabs_workspace_only.sql`

**关键约束（修订）**：V12 的 `stage_tab_index`（FTS5 虚表）通过 `rowid` 与 `stage_tabs.rowid` 隐式对齐，且有 6 个 trigger 把 `stage_tabs` / `stage_tab_payload` 的变更同步到 FTS。直接 `DROP TABLE stage_tabs` 会让旧 trigger 全部失效（DROP 表时 trigger 自动随之消失），同时 FTS 索引里的 rowid 会与新表 rowid 错位（SQLite 重建表后 rowid 不保证延续）。本迁移必须按下序处理：

1. **先 drop V12 的 6 个 trigger**（避免重建表期间触发旧 trigger 写脏 FTS）
2. 重建 `stage_tabs` 新表 + 数据迁移（保留 `id`，**不**保留 `rowid`）
3. 重建 6 个 trigger（去 scope 列引用，其余结构不变）
4. **重建 FTS rowid 映射**：`DELETE FROM stage_tab_index;` 后 `INSERT ... SELECT` 用新表 rowid 重新填充（含 `stage_tabs.title` + `stage_tab_payload.content_text`）

```sql
-- V13__stage_tabs_workspace_only.sql

PRAGMA foreign_keys = OFF;

-- 0) 备份表（day-1 兜底；CREATE TABLE … AS SELECT 是合法 SQLite 语法）
CREATE TABLE IF NOT EXISTS stage_tabs_backup_v13_pre AS SELECT * FROM stage_tabs;

-- 1) 删旧 trigger（DROP TABLE 会顺带清；为可读性显式列出）
DROP TRIGGER IF EXISTS stage_tabs_ai;
DROP TRIGGER IF EXISTS stage_tabs_au;
DROP TRIGGER IF EXISTS stage_tabs_ad;
DROP TRIGGER IF EXISTS stage_tab_payload_aiu;
DROP TRIGGER IF EXISTS stage_tab_payload_au;
DROP TRIGGER IF EXISTS stage_tab_payload_ad;

-- 2) 重建 stage_tabs（去 scope、去 CHECK、改 FK 到 SET NULL）
CREATE TABLE stage_tabs_new (
  id                 TEXT PRIMARY KEY,
  type               TEXT NOT NULL,
  title              TEXT NOT NULL,
  connection_id      TEXT,
  database_name      TEXT,
  schema_name        TEXT,
  origin_session_id  TEXT,
  payload_version    INTEGER NOT NULL DEFAULT 1,
  pinned             INTEGER NOT NULL DEFAULT 0,
  archived           INTEGER NOT NULL DEFAULT 0,
  archived_at        INTEGER,
  created_at         INTEGER NOT NULL,
  last_touched_at    INTEGER NOT NULL,
  FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE SET NULL
);

INSERT INTO stage_tabs_new (id, type, title, connection_id, database_name, schema_name,
  origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at)
  SELECT id, type, title, connection_id, database_name, schema_name,
         origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at
  FROM stage_tabs;

DROP TABLE stage_tabs;
ALTER TABLE stage_tabs_new RENAME TO stage_tabs;

-- 3) 重建索引
CREATE INDEX idx_stage_tabs_active ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX idx_stage_tabs_type   ON stage_tabs(type, archived);
CREATE INDEX idx_stage_tabs_origin ON stage_tabs(origin_session_id);

-- 4) 重建 FTS 映射（旧 rowid 不复用）
DELETE FROM stage_tab_index;
INSERT INTO stage_tab_index(rowid, title, content, type, archived)
  SELECT t.rowid,
         t.title,
         COALESCE(p.content_text, ''),
         t.type,
         t.archived
  FROM stage_tabs t
  LEFT JOIN stage_tab_payload p ON p.tab_id = t.id;

-- 5) 重建 6 个 trigger（去 scope 列依赖；其他逻辑保持）
CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
  INSERT INTO stage_tab_index(rowid, title, content, type, archived)
    VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
END;

CREATE TRIGGER stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
  UPDATE stage_tab_index
    SET title = NEW.title, archived = NEW.archived
    WHERE rowid = NEW.rowid;
END;

CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
  DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
END;

CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_ad AFTER DELETE ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = ''
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = OLD.tab_id);
END;

PRAGMA foreign_keys = ON;
```

**`stage_tab_payload` 表本身**：schema 不变，FK 仍为 `stage_tabs(id) ON DELETE CASCADE`；不需要重建，但因 `stage_tabs` 重建后 `id` 一致，payload 行自动延续。

**`StageTabsMigrationIT`（已存在）必须扩展**：在 V12 跑完后插入若干 row（含 trigger 触发的 FTS 写入）→ 跑 V13 → 断言：

- `stage_tab_index` 行数 = `stage_tabs` 行数
- 对每个 tab，`MATCH` FTS 搜索其 title / content 仍能命中
- 旧 V12 trigger 完全消失（`SELECT name FROM sqlite_master WHERE type='trigger'` 仅含新建 6 个）
- `scope` 列从 `PRAGMA table_info(stage_tabs)` 中消失

### 4.5 Hydration 行为 + 客户端持久化接口契约

**API 契约变化**（`client/src/features/stage/persistence/stage-tab-api.ts`）：

```ts
// 旧接口（V12）—— 完全废弃
listWorkspaceTabs(): Promise<ListResponse>           // 删除
listSessionTabs(originSessionId: string): Promise<…> // 删除

// 新接口（V13）—— 单一入口
listAll(opts?: { archived?: boolean; originSessionId?: string }): Promise<ListResponse>
//   - archived 缺省 false（仅活跃）；archived=true 返回归档分组用
//   - originSessionId 可选，按来源标签筛选；保留以支持 ui_find filter 透传

// UpsertRequest 字段同步收敛
interface UpsertRequest {
  id: string
  type: string
  // scope: 字段移除
  title: string
  connectionId?: string | null
  database?: string | null
  schema?: string | null
  originSessionId?: string | null
  pinned?: boolean
  archived?: boolean
  createdAt: number
  lastTouchedAt: number
  payload?: unknown
  contentText?: string
  ifMatch?: number
}
```

**`StagePersistenceCoordinator` 改造**：

```ts
// 旧（仅拉 workspace scope）
async start(): Promise<void> {
  this.phase = 'hydrating'
  const meta = await this.api.listWorkspaceTabs()
  this.onHydrated?.(meta.items)
  …
}

// 新（拉所有非归档）
async start(): Promise<void> {
  this.phase = 'hydrating'
  const meta = await this.api.listAll({ archived: false })
  this.onHydrated?.(meta.items)
  …
}
```

**`stage-persistence-bootstrap.ts` 改造**：

```ts
// 旧 hook 名 __hydrateWorkspaceTabs / __hydrateSessionTabs 二合一为单一入口
coordinator.onHydrated = (items) => {
  useStageStore.getState().__hydrateAll(items.map(toStageTab))
}

// __hydrateSessionTabs 删除；__hydrateWorkspaceTabs 保留 alias 一个 release 后删
```

**`useStageStore.__hydrateAll`**（替换 `__hydrateWorkspaceTabs` / `__hydrateSessionTabs`）：

```ts
__hydrateAll: (items) => set((s) => {
  const incomingMap = new Map(items.map((t) => [t.tabId, t]))
  const merged = s.tabs.map((existing) => {
    const hydrated = incomingMap.get(existing.tabId)
    return hydrated ? { ...existing, ...hydrated } : existing
  })
  for (const item of items) {
    if (!merged.some((t) => t.tabId === item.tabId)) merged.push(item)
  }
  return { tabs: merged }
}),
```

**`persistedTabSummaries`**（`stage-persistence-bootstrap.ts` 内）：

```ts
// 旧：分别遍历 state.workspaceTabs + state.tabsBySession
// 新：仅遍历 state.tabs
function persistedTabSummaries(state: { tabs: StageTab[] }): TabSummary[] {
  return state.tabs.filter((t) => isPersistent(t.type)).map(toSummary)
}
```

**`resolveTabSnapshot`**（`stage-persistence-bootstrap.ts` 内）：删除 `scope: (getTabTypeDescriptor(tab.type).scope ?? tab.scope) as ...` 这个分支；`UpsertRequest` 不再要求 `scope` 字段。

**hydration 行为时序**：

```
应用启动：
  1. coordinator.start() → api.listAll({ archived: false }) → onHydrated(items)
     → useStageStore.__hydrateAll(items)
  2. tabs[] 注入；openTabIds = ∅；openTabIdsOrdered = []；activeTabId = null
  3. UI 渲染：左 rail 列出全部活跃 tabs，顶 tab 栏空，右 pane 显示 EmptyState
  4. 用户/AI 第一次 ui_exec(workspace, focus, target=tabId) 或左 rail 行点击 → ensureOpenInWorkset
```

**不**做"恢复上次关闭时的 openTabIds"——避免 20 个 tab 撑爆 Monaco 内存；如未来产品反馈强烈，可加"最近 N 个自动重开"开关到 Phase 4。

session 切换 → **零副作用**（既然 stage 全局，切 session 不动 stage）；删除 `stage-persistence-bootstrap` 里所有按 active session 拉 session-scope tabs 的代码（`listSessionTabs` 调用方应已无残留）。

---

## 5. 协议层（`ui_xxx` + Edit 原语）

### 5.1 `ui_find`

| 字段 | 变化 |
|---|---|
| `filter.scope` | **删除** |
| `filter.originSessionId` | 保留；语义改为"按来源标签筛选" |
| `output.items[].originSessionId` | 保留 |
| `output.items[].originSessionTitle` | **新增**（display only） |

### 5.2 `ui_read`

| 字段 | 变化 |
|---|---|
| `target='active'` 解析 | 从"当前 session 的 active tab id"改为"全局 `activeTabId`" |
| `mode='state'` 输出 | 新增 `inWorkset: boolean` |
| 其他 | 不变 |

### 5.3 `ui_patch`（强化 `/content`）

```json
{
  "object": "query_editor",
  "target": "query_editor_abc123",
  "ops": [
    { "op": "replace", "path": "/content", "value": "<full sql>", "baseVersion": 12 }
  ]
}
```

- `/content` 的 `replace` op：增 **required** `baseVersion: number`
- `/connectionId` `/database` `/schema` 三条不要求 `baseVersion`（context 字段不参与并发冲突）
- 服务端 / 前端 store 校验失败 → 返回 `error.code='version_conflict'` + `error.markdown` + `currentState.version`

**实现要点（前端 store 必须新增校验逻辑，不只是拼参数）**：

当前 `useSqlWorkbenchStore.replaceSqlText(tabId, content)` 是**无条件覆盖**（仅返回 `{ version }`，不接收 `baseVersion`，不做检查）；`applyTextEdits` 已有 `baseVersion` 检查。本 spec 要求 `replaceSqlText` 与 `applyTextEdits` 对齐：

```ts
// 新签名（替换原 replaceSqlText）
replaceSqlText(
  tabId: string,
  content: string,
  baseVersion: number,
): {
  status: 'applied' | 'version_conflict'
  version: number
  currentState?: { version: number }
}
```

校验顺序：

1. 取当前 tab 的 `version`，与 `baseVersion` 比较；不等 → **不写入** `sqlText`，返回 `{ status: 'version_conflict', currentState: { version: <current> } }`，由 `ui_patch` 处理器进一步包成 `error.code='version_conflict'` + `error.markdown` 返回 AI
2. 相等 → 覆盖 `sqlText`、`version++`、`lastTouchedAt = now`；返回 `{ status: 'applied', version: <new> }`

`useStageStore.replaceQueryEditorContent` 同步把签名升级为 `(tabId, content, baseVersion)` 直接转发到 store；前端 `ui_patch` 处理器读 `op.baseVersion` 拼入。Monaco keystroke 路径继续用 `applyTextEdits` 或现有的 store-internal mutation（不走 `replaceSqlText`），不受影响。

### 5.4 `ui_exec apply_text_edits`（强化每条 edit）

```json
{
  "object": "query_editor",
  "target": "query_editor_abc123",
  "action": "apply_text_edits",
  "params": {
    "baseVersion": 12,
    "edits": [
      {
        "range": { "startLine": 7, "startColumn": 1, "endLine": 9, "endColumn": 1 },
        "expectedText": "WHERE created_at > '2026-01-01';\n",
        "text": "WHERE u.created_at > '2026-01-01';\n"
      }
    ]
  }
}
```

校验三阶（任一失败整批回滚）：

1. `tab.payloadVersion === params.baseVersion`，否 → `version_conflict`
2. 对每条 edit：取 `getRangeText(startLine..endLine)`，normalize（`\r\n` → `\n`）后字面比较 `expectedText`，否 → `expected_text_mismatch`
3. `range.endLine ≤ tab.lineCount`，否 → `out_of_range_lines`

成功 → 应用、`payloadVersion++`、`lastTouchedAt = now`、persist + flush 后回 action_result。

多条 edits 在同一批：从后往前应用（line 序号才不会因前一条变化），所有 `expectedText` 都基于 `baseVersion` 那一刻的快照。

### 5.5 `ui_exec workspace.*` 三新动词

| Action | 必填参数 | 可选参数 | 行为 |
|---|---|---|---|
| `workspace.detach` | `target: tabId` | — | 仅从 `openTabIds` / `openTabIdsOrdered` 移除；DB 完全不动；同 tab 仍可在左 rail 库见到 |
| `workspace.archive` | `target: tabId` | `archived: boolean = true` | `archived=true`：DB `archived=true` + `archivedAt=now` + 级联 `detach`；左 rail 默认视图隐藏，归档分组可见。`archived=false`：DB `archived=false` + `archivedAt=null` —— **解归档**入口 |
| `workspace.trash` | `target: tabId` | — | DB hard DELETE；自动级联 `detach`；fts / payload 表通过 FK CASCADE 清理 |
| `workspace.close` | `target: tabId` | — | **deprecated alias** = `archive(target, archived=true)`；3 个发版周期后删除 |
| `workspace.focus` | `target: tabId` | — | 行为升级：`ensureOpenInWorkset(target)` + `setActive(target)`，自动把库里的 tab 拉进工作集。**对 archived tab 显式拒绝**：返回 `error.code='tab_archived'` + markdown 提示先 `archive(target, archived=false)` 再 focus；**不**隐式解归档（避免 AI 在调试某个错误时无意识激活历史 tab） |

`archive` 之所以选用「带 `archived` 布尔参数」而不是 `archive` / `unarchive` 双动词：

- 与 `useStageStore.archiveTab(tabId, archived: boolean)` 内部 API 形状一致，AI 与 store 调用同构
- 与现有 `PATCH /api/stage/tabs/{id}/archive` 端点一致（已是 `{ archived: boolean }` body）
- 减少协议表面积；`archived` 缺省 true 让最常见用法 `workspace.archive(target)` 维持简洁

### 5.6 失败响应统一结构

```json
{
  "error": {
    "code": "version_conflict" | "expected_text_mismatch" | "out_of_range_lines"
            | "tab_not_found" | "tab_archived",
    "message": "<one-line machine summary>",
    "currentState": { "version": 14, "tabId": "query_editor_abc123" },
    "markdown": "## Edit failed: content drifted on tab `query_editor_abc123`\n…",
    "details": { "editIndex": 0, "expected": "...", "actual": "..." }
  }
}
```

### 5.7 `error.markdown` 模板（固定 sections）

```
## <一行人类标题>
Tab: `<tabId>` (<title>)
Reason: <code>

**Expected (your edit#N):**
```sql
<expected lines>
```

**Current (now):**
```sql
<actual lines>
```

<contextual hint, e.g., "another session edited this tab 12s ago, version is now 14, your baseVersion=12">

**Suggested next step:** call `datatalk_ui_read({ object: "query_editor", target: "<tabId>", mode: "state" })` and re-plan the edit against the latest content.
```

代码围栏语言：按 `tab.type` → 语言映射（`query_editor` → sql；未来 `markdown_note` → markdown；不识别的 type 一律 text）。

**字符预算**（修订）：

- **总硬上限 3000 字符**——足够承载多 edit 批次失败的 expected/current 对照
- **模板框架**（标题 / Tab / Reason / Hint / Suggested next step）固定开销 ≈ 700 字符，预算外
- **预算内 ≈ 2300 字符** 留给 Expected + Current 两个 code block；Expected 与 Current 各占 50%（≈ 1150 字符各）
- 单个 code block 超过预算 → truncate 中间行（首 8 + 末 8 行 + `… <N> lines elided. Read with datatalk_ui_read for full content. …`）
- 多 edit 批次（如 5 条 edit 中第 3 条失败）：仅渲染**失败那条** edit 的 expected/current；前两条已应用且未冲突，后续未执行；`details.editIndex` 字段告知 AI 第 3 条（0-based 即 `details.editIndex=2`）失败
- 渲染器以字符为单位 truncate（不按 token），简单稳定可测

### 5.8 后端实现要点

- 新建 `data-talk-application/src/main/java/com/datatalk/application/stage/EditConflictMarkdownFormatter.java`
- 提供 5 个静态构造函数对应 5 个 code；签名见段 6.1
- 模板里的"contextual hint"由 `lastTouchedAt` 与当前时间做 `humanizeDelta(deltaMs)` 得到（`"12s ago" / "3m ago" / "an hour ago"`）

---

## 6. 并发与一致性

### 6.1 Per-tab 序列化

**前端层**（`useSqlWorkbenchStore`）：

```ts
const tabWriteLocks = new Map<string, Promise<void>>()

async function withTabWriteLock<T>(tabId: string, fn: () => Promise<T>): Promise<T> {
  const prev = tabWriteLocks.get(tabId) ?? Promise.resolve()
  let release!: () => void
  const next = new Promise<void>((r) => (release = r))
  tabWriteLocks.set(tabId, prev.then(() => next))
  await prev
  try {
    return await Promise.race([fn(), createTimeout(5000, tabId)])
  } finally {
    release()
    if (tabWriteLocks.get(tabId) === next) tabWriteLocks.delete(tabId)
  }
}
```

- 任何 `ui_patch` / `ui_exec apply_text_edits` 处理器入口先取锁
- Monaco keystroke 直接走 store mutation，**不**取锁（用户输入永远优先）
- **`payloadVersion` 自增时机：每次 Monaco onChange 即时 +1**（不 debounce）。理由：用户每按一个字符都让 AI 的所有缓存 baseVersion 立即失效，强制 AI 在用户打字时进入"等用户停手再 ui_read 重做"的礼让节奏；如果 debounce，AI 的 edit 会和用户输入交叉，可能产生未被 expectedText 抓住的语义破坏（用户连打 5 个字符 → AI 在第 3 字符时刻 edit，按 baseVersion 还能过；但用户下一秒补的 2 字符就被 AI overwrite 一部分）。
- 持久化（content debounce 1s）和 version 自增解耦：onChange 立即 +1 + 进 dirty 标记；持久化 coordinator 仍按现有 1s debounce 攒一次 PUT。AI 的 `baseVersion` 只看内存 version，不看持久化 version
- 5s 超时强 release，避免死锁

**后端层**（`StagePersistenceCoordinator`）：按 `tabId` 入 channel；同 tab 的 mutation request 必然顺序 flush；force-flush 后才回 action_result。

### 6.2 双 session 并发时间线（验收用）

```
t0  Session-A: ui_read(query_editor, target=qe_abc) → version=12, content=<v12>
t0  Session-B: ui_read(query_editor, target=qe_abc) → version=12, content=<v12>

t1  Session-A: ui_exec(apply_text_edits, baseVersion=12, edits=[E1])
    → frontend lock acquire → version match → expectedText match → apply
    → version=13, persist → flush ack → action_result OK

t2  Session-B: ui_exec(apply_text_edits, baseVersion=12, edits=[E2])
    → frontend lock acquire → version=13 ≠ 12 → version_conflict
    → action_result error.markdown = "...A 改过了，version=13..."

t3  Session-B AI 读 markdown → ui_read(target=qe_abc, mode=state)
    → 看到 version=13 + 新内容 → 重新算 edits → ui_exec(apply_text_edits, baseVersion=13, edits=[E2'])
    → frontend lock acquire → version match → expectedText match → apply
    → version=14, persist → flush ack → action_result OK
```

**关键不变量**：第二个 session 的 baseVersion 永远不会"碰运气过"，错误 markdown 引导它走进重读重算回路；AGENTS.md 明确禁止"机械重试同一 baseVersion"。

### 6.3 边界场景

| 场景 | 处理 |
|---|---|
| AI 在不同 session 同时 `archive` 同 tab | 第二次收 idempotent OK，不报错 |
| Session-A `trash`，Session-B `apply_text_edits` 同 tab | trash 走 hard delete；后到 apply_text_edits 走 `tab_not_found` + markdown："该 tab 已被另一会话删除" |
| 用户 Monaco 手敲 + AI 同时 `apply_text_edits` | Monaco keystroke 不走 ui_exec 通道，直接 store mutation 自增 version → AI 的 `baseVersion` 失效，触发 markdown error；AGENTS.md 写明"用户也可能在 Monaco 改" |
| 多条 edits 中第 3 条 `expectedText` 失败 | 整批回滚；markdown 精确指出"edit#3"，前两条不应用 |
| `\r\n` vs `\n` | normalize 后比较；spec 测试覆盖 |
| 操作已归档 tab | `tab_archived` + markdown："The tab is archived. Call `workspace.archive(target, archived=false)` first if you need to edit." |

### 6.4 `EditConflictMarkdownFormatter` 签名

```java
package com.datatalk.application.stage;

public class EditConflictMarkdownFormatter {

    public static String versionConflict(StageTab tab, int requestedBase, int actualVersion,
                                         long lastTouchedDeltaMs) { … }

    public static String expectedTextMismatch(StageTab tab, int editIndex,
                                              String expected, String actual,
                                              int actualVersion, long lastTouchedDeltaMs) { … }

    public static String outOfRangeLines(StageTab tab, EditRange range, int actualLineCount) { … }

    public static String tabNotFound(String tabId) { … }

    public static String tabArchived(StageTab tab) { … }

    static String humanizeDelta(long deltaMs) { … }
    static String truncateCodeBlock(String text, int headLines, int tailLines) { … }
}
```

---

## 7. 前端布局与视觉契约

### 7.1 整体调整

| 区域 | 现状 | 目标 |
|---|---|---|
| `<AppSidebar />` | `Sessions` group + `Tabs` group | 仅 `Sessions` group |
| `<SplitView />` chat 列 | 不变 | 不变 |
| `<StageWindow />` | 顶 tab 栏 + 内容区 | **左 rail（库）+ 顶 tab 栏（工作集）+ 内容区** 三段 |
| StageWindow `sessionId` prop | 必填 | **删除**（state 全局化后无意义） |

### 7.2 StageWindow 内部结构

```
StageWindow
├── 顶部 chrome bar（标题 + max + close）            -- 不变
└── 内部水平分屏
    ├── Left Rail（默认 240px / 折叠 36px / 拖拽 180-320px）
    │   ├── StageRailSearch（搜索框，复用 useStageFind）
    │   ├── StageRailGroup "Active"（lastTouchedAt desc，pinned 置顶）
    │   ├── StageRailGroup "Archived"（默认折叠）
    │   └── 行内菜单：Open / Pin / Archive / Trash
    ├── 内部分隔条（可拖拽 + 折叠按钮 + localStorage 持久化）
    └── Right Pane
        ├── StageTabBar（顶 tab 栏，渲染源 = openTabs）
        │   └── "+" 按钮（新增："新工作位"小菜单）
        └── StageTabContent（active tab 渲染） / StageWorkbenchEmptyState（openTabIds=∅）
```

### 7.3 关键交互

| 操作 | 副作用 |
|---|---|
| 左 rail 行点击（活跃分组） | `ensureOpenInWorkset(tabId)` + `setActive(tabId)` |
| 左 rail 行点击（归档分组） | 先弹一个内联确认 chip "解归档并打开？"，确认后 `archiveTab(tabId, false)` + `ensureOpenInWorkset(tabId)` + `setActive(tabId)`；与 AI 路径（必须显式 `archive(target, archived=false)`）行为一致，但用户在 UI 上有明确意图，所以隐式 wrap 在一次点击内完成 |
| 左 rail 行 hover → 行内 kebab Open | 同上 |
| 左 rail 行内菜单 Pin | `setTabPinned(tabId, true/false)`（持久化） |
| 左 rail 行内菜单 Archive | `archiveTab(tabId, true)`（持久化）+ 自动 detach |
| 左 rail 行内菜单 Trash | 二级确认 dialog → `trashTab(tabId)`（hard DELETE） |
| 顶 tab 栏 X | `detachFromWorkset(tabId)`，DB 不动 |
| 顶 tab 栏 "+" | 弹"新工作位"菜单（SQL 编辑器 / 占位 ER / 占位 Report / 占位 Dashboard），点击后 `openTab` + `ensureOpenInWorkset` |
| 顶 tab 栏切换 active | `setActive(tabId)` |
| 左 rail 折叠按钮 | `toggleLeftRailCollapsed()` + localStorage 持久化 |
| 左 rail 分隔条拖拽 | `setLeftRailWidth(px)` + localStorage 持久化（debounce 200ms） |

### 7.4 视觉契约（client/DESIGN.md token 映射）

**所有交互控件五态必须显式映射，禁止自造灰**。

#### 左 rail 容器

| 状态 | Token |
|---|---|
| 容器底色 | `bg.subtle` |
| 容器右边线 | `border.subtle` |
| 折叠态 | 容器宽 36px，行仅图标 + tooltip |

#### StageRailSearch（搜索框）

| 状态 | Token |
|---|---|
| idle 表面 | `bg.panel` |
| idle 边线 | `border.default` |
| idle placeholder | `text.muted` |
| hover 边线 | `border.default`（不变）+ 背景 `interaction.hover` 覆盖 |
| focus 边线 | `border.strong` |
| focus 光环 | `interaction.focusRing`（外圈 2px） |
| disabled | `interaction.disabled` + 图标灰显 + 占位文案变 |

#### StageRailRow（行）

| 状态 | Token |
|---|---|
| idle 文本（不在 workset） | `text.muted`（type 标签）+ `text.muted`（title）+ font-weight 400 |
| idle 文本（在 workset 但非 active） | `text.muted`（type 标签）+ `text.base`（title）+ font-weight 500 + 右侧 6px 圆点 `accent.primary`（`opacity 0.6`，区分但不抢焦） |
| idle 图标 | `text.muted` |
| hover 背景 | `interaction.hover` |
| focused（键盘焦点 + active = 当前 active tab） | `interaction.selected` 背景 + `text.strong` 文本 + `accent.primary` 左侧 2px 提示条 + 圆点升级为实色 |
| keyboard focused（非 active） | `border.strong` 1px 内描边 + `interaction.focusRing` 外圈，前文 token 不变 |
| pinned | 左侧 pin 图标 `accent.primary` |
| archived | 整行 opacity 0.6 + `text.soft`；归档分组内 archive 图标 `text.muted` |
| dangerous menu item（Trash） | `status.danger` 文本 + `status.dangerSurface` hover |

`inWorkset` 视觉区分参考 IDEA Project 面板"已打开文件"加粗标识：用字重 + 右侧小圆点双信号（颜色 + 几何），满足"状态不能仅靠颜色"约束。

#### StageRailRowMenu（kebab）

**trigger 按钮（行尾三点）**：

| 状态 | Token |
|---|---|
| idle | 透明背景 + 图标 `text.muted`，仅在 row hover/focus 时显形（`opacity 0 → 1`，180ms） |
| hover（在 trigger 上） | `interaction.hover` 背景 + 图标 `text.base` |
| focus（键盘） | `interaction.focusRing` 外圈 + 图标 `text.base` |
| active（菜单展开中） | `interaction.selected` 背景 + 图标 `accent.primary` |
| disabled | 不会出现 |

**菜单项（Open / Pin / Unpin / Archive / Unarchive）**：

| 状态 | Token |
|---|---|
| 菜单容器 | `bg.elevated` 表面 + `border.default` + 阴影 sm + 8px padding |
| idle 文本 | `text.base` + 图标 `text.muted` |
| hover 背景 | `interaction.hover` + 图标 `text.base` |
| focus（键盘） | `interaction.selected` 背景 + `text.strong` 文本 + 图标 `text.strong` |
| active 状态（如 Pin 已 pinned 显示 "Unpin"） | 仅文案变化，token 同 idle |
| disabled | `interaction.disabled`（仅在 cross-state 不一致时才出现，正常情况 5 项都可点） |

**Trash 项（dangerous）**：

| 状态 | Token |
|---|---|
| idle 文本 | `status.danger` + 图标 `status.danger` |
| hover 背景 | `status.dangerSurface` |
| focus | `interaction.focusRing` 外圈 + `status.danger` 文本 |
| confirm dialog | `bg.elevated` + danger button（`status.danger` 背景 + `text.inverse`） + cancel button（`bg.panel` + `border.default`） |

#### 顶 tab 栏（容器与 tab 项）

| 状态 | Token |
|---|---|
| 容器底色 | `bg.canvas` |
| 容器底边线 | `border.subtle` |
| idle tab 文本 | `tabIdle: text.muted` |
| active tab 文本 | `tabActive: accent.primary` |
| active tab underline | `accent.primary` 2px |
| hover tab 背景 | `interaction.hover` |
| keyboard focus tab | 现有 token + `interaction.focusRing` 外圈 |
| close X idle | `text.muted` |
| close X hover（在 tab 上） | `text.base` |
| close X focus | `interaction.focusRing` 外圈 + `text.base` |

#### 顶 tab 栏 "+" 按钮（新增工作位）

| 状态 | Token |
|---|---|
| idle 图标 | `text.muted` |
| idle 背景 | 透明（继承容器 `bg.canvas`） |
| hover 背景 | `interaction.hover` |
| hover 图标 | `text.base` |
| focus | `interaction.focusRing` 外圈 + 图标 `text.base` |
| active（菜单展开中） | `interaction.selected` 背景 + 图标 `accent.primary` |
| disabled | 不会出现（永远可用） |

#### "新工作位"菜单（DropdownMenu）

| 状态 | Token |
|---|---|
| 菜单容器 | `bg.elevated` 表面 + `border.default` + 阴影 sm |
| 菜单项 idle 文本 | `text.base` + 图标 `text.muted` |
| 菜单项 hover 背景 | `interaction.hover` + 图标 `text.base` |
| 菜单项 focus（键盘） | `interaction.selected` 背景 + `text.strong` 文本 |
| 菜单项 disabled（占位 ER / Report / Dashboard） | `interaction.disabled` 文本 + 右侧 "Pending" 小标签（`bg.subtle` + `text.soft`） |
| 分隔线 | `border.subtle` |

#### StageWorkbenchEmptyState

保留现有四张卡片不动；视觉契约 review 时只需对照已有卡片的五态实现是否合规（idle / hover / focus / disabled / 占位 pending state）。

#### 左 rail 空态视图

当 `tabs.filter(t => !t.archived).length === 0`：

| 元素 | Token |
|---|---|
| 容器对齐 | center horizontal & vertical 内边距 24px |
| 图标 | `text.muted` 24px |
| 主文本（"No workbench tabs yet"） | `text.muted` 14px medium |
| 副文本（"Open one from chat or click +"，关联 `stage.leftRail.empty` i18n） | `text.soft` 12px regular |
| CTA "+" 按钮（链接到顶栏 "+" 按钮） | 同顶栏 "+" 按钮五态，提示 tooltip 文本 `stage.leftRail.cta.openNew` |

### 7.5 i18n 键调整

```
sidebar.tabs.title           →  stage.leftRail.title
sidebar.tabs.empty           →  stage.leftRail.empty
sidebar.tabs.archive.toggle  →  stage.leftRail.archive.toggle
sidebar.tabs.search.placeholder → stage.leftRail.search.placeholder
（… 其他 sidebar.tabs.* 全部迁移到 stage.leftRail.*）
```

zh-CN / en 同步。

### 7.6 测试

- `stage-rail.test.tsx`（新）覆盖搜索框五态、行五态、kebab 三态、archive / pin / trash 行为、空态
- `stage-window.test.tsx` 增"左 rail + 顶栏 + 右 pane"三段断言
- `app-sidebar.test.tsx`（如有）验证 NavTabs 不再渲染

---

## 8. AGENTS.md 改写（C1）

文件：`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

### 8.1 章节级处置一览

| 现有段落 | 处置 |
|---|---|
| `## Core Rules` | 保留 + 增 1 条多 session 共享提示 |
| `## Intent Routing Gate` | 不动 |
| `## Context Model` | 不动 |
| `## Registered Actions / *`（Session Data Context / Connection Management / Schema, Query, Artifacts / Query Diagnostics） | 不动 |
| `### Diagnostics Workflow Rules` | 不动 |
| `### UI Actions` 段开头 | 改写：把 "Tabs are workspace-wide and shared across all sessions" 提为段首第一句；删除 workspace / session scope 双轨叙述 |
| `datatalk_ui_find` 描述 | 强调"library covers all open and idle tabs (not just those currently in the top tab bar)" |
| `datatalk_ui_read` 描述 | 增 `state.inWorkset` 字段 |
| `datatalk_ui_patch` 描述 | 增 "`/content` requires `baseVersion`" |
| `datatalk_ui_exec` 描述 | 增 `apply_text_edits` 强制 `expectedText`；列出 `detach` / `archive(archived?: boolean = true)` / `trash`；明示 `archive(archived=false)` 是解归档入口；`close` 标 deprecated alias = `archive(archived=true)` |
| `## Exact UI Contract` | 大改：apply_text_edits schema 段补 `expectedText` required；workspace 动作列表更新 |
| `## UI Navigation Rules` | 改写：移除 workspace / session scope；强调 library vs workset |
| `## Query Editor Rules` | 增 1 条 `expectedText` 必填 + "do not retry the same edit on `expected_text_mismatch`" |
| `## Recommended Workflows / Edit SQL in a Query Editor` | 重写第 3 步含 `expectedText` 取法；新增"on conflict: re-read, re-plan." |
| `## Tab Persistence and Search` | 改写"persist across sessions" → "shared across all sessions and persisted across app restarts" |
| `{{STAGE_TAB_DIGEST}}` 占位 | 不动；renderer 加字段（见 §8.4） |

### 8.2 新增章节 `## Concurrency Contract`

```markdown
## Concurrency Contract

Workbench tabs (`query_editor`, `artifact_preview`, future `er_designer` /
`report_designer`) are workspace-wide objects shared across all chat sessions.
Any session — including a parallel agent — may have edited a tab since your
last read. Treat every patch and text edit as optimistic and conflict-aware.

### Required guard fields

- `datatalk_ui_patch` with `path=/content` requires `baseVersion: number`.
- `datatalk_ui_exec apply_text_edits` requires `params.baseVersion: number`.
- Each entry in `params.edits` requires `expectedText: string` — the exact
  text currently occupying `range`. The server compares it after a
  line-ending normalization (`\r\n` → `\n`).

### Conflict response shape

```json
{
  "error": {
    "code": "version_conflict" | "expected_text_mismatch" | "out_of_range_lines"
            | "tab_not_found" | "tab_archived",
    "message": "<one-line machine summary>",
    "currentState": { "version": <int>, "tabId": "<id>" },
    "markdown": "<human-and-LLM-readable explanation>",
    "details": { "editIndex": <int?>, "expected": "...", "actual": "..." }
  }
}
```

### What you MUST do on conflict

1. Stop. Do not retry with the same `baseVersion` or `expectedText`.
2. Read `error.markdown` — it includes the current content for the affected
   range and a hint about who likely changed it.
3. Call `datatalk_ui_read` on the same `target` with `mode='state'` to get
   the new `version` and `content`.
4. Re-plan your edit against the new content. Your new range / expectedText
   must match the freshly read snapshot exactly.
5. Submit a single fresh `apply_text_edits` with the new `baseVersion`.

### Multi-edit batches

`apply_text_edits` accepts multiple edits in `params.edits`. The server
applies them in **reverse line order** against the snapshot at `baseVersion`,
as a single transactional unit. Either all edits apply (success) or none do
(failure with a single `error.markdown`).

If `error.code='expected_text_mismatch'`, `error.details.editIndex` is the
**0-based index** of the failing edit in the request array (i.e. `editIndex=0`
is the first edit, `editIndex=2` is the third). Earlier edits in the same
batch were **not** applied — the rollback is whole-batch, not partial. Plan
your retry as a fresh single-batch `apply_text_edits` against the new
`baseVersion`.

### What you MUST NOT do

- Do not loop the same edit hoping the conflict clears.
- Do not assume `version_conflict` means your edit is wrong — it usually
  means another session reached the tab first.
- Do not trash or archive a tab to "force a clean slate" unless the user
  explicitly asked you to.

### Multi-session etiquette

- Always pass an explicit `target` tab id when more than one tab of the
  matching type exists. Relying on `target='active'` while another session
  may have shifted focus is a source of silent cross-talk.
- After mutating, the change is visible to subsequent `datatalk_ui_find`
  calls in any session before your next tool call returns.
```

### 8.3 新增章节 `## Library vs Workset`

```markdown
## Library vs Workset

The workbench has two coexisting tab views:

- **Library** — the durable set of all non-archived tabs. Surfaced by
  `datatalk_ui_find` and the left rail. Includes tabs that are not
  currently open in the top tab bar.
- **Workset** — the subset currently open in the top tab bar. Tracked
  per app instance (not persisted server-side) and reflected by
  `datatalk_ui_read state.inWorkset`.

When the user says "current SQL editor", they mean a tab in the workset
(usually the active one). When they say "the SQL I wrote yesterday",
they mean a tab in the library that may not be in the workset.

To bring a library tab into the workset, call
`datatalk_ui_exec(object=workspace, action=focus, params.target=<tabId>)`
— it both ensures the tab is open and makes it active.

To remove a tab from the workset without deleting it, call
`datatalk_ui_exec(object=workspace, action=detach, params.target=<tabId>)`.
The tab remains in the library and can be re-opened later.

To archive a tab (hide from the default library view, keep history), use
`action=archive` with `params.target=<tabId>`. The default toggle is
`params.archived=true`; pass `params.archived=false` to **un-archive** a
previously archived tab (e.g., when an edit fails with `tab_archived`).

To permanently delete, use `action=trash` — only when the user explicitly
asks. Both `archive(archived=true)` and `trash` cascade-detach from the
workset.

The legacy `action=close` is now an alias for `archive(archived=true)`.
Prefer the new verbs for clarity.

> Deprecated since v0.X (the release where this spec ships); will be
> removed in v0.X+3. AGENTS.md should retain this alias paragraph until
> the removal release; tests in `AgentPromptContractTest` assert the
> deprecation marker remains so we don't drop it accidentally.
```

### 8.4 `STAGE_TAB_DIGEST` 渲染升级

**架构约束**：`STAGE_TAB_DIGEST` 由 `AgentPromptBuilder` 在 `OpenCodeBootstrapWriter.setInstructionsSupplier` 一次性渲染（bootstrap 时），从此不刷新。它**只能注入服务端可知的字段**：tab id / type / title / 连接元数据 / `originSession*` / `payloadVersion` / `lastTouchedAt`。

**`inWorkset` 不进 digest**——它是客户端 per-app-instance 的运行时状态，服务端拿不到也拿不准。AI 想知道某 tab 是否在工作集，必须通过 `datatalk_ui_read({ object, target, mode: 'state' })` 读 `state.inWorkset`（每次实时返回）；这一点要在 AGENTS.md `## Library vs Workset` 段写明。

升级后的 digest 模板（每条新增 `fromSession` / `version` / `lastTouched`，**无** `inWorkset`）：

```
- query_editor `qe_abc123` — "users 月增分析"
  conn=conn_x db=analytics schema=public
  fromSession="2026-04-27 周报分析"
  lastTouched=12s ago • version=14
```

字段顺序固定（避免 prompt 抖动让 AI cache miss）：`type \`tabId\` "title"` → 连接信息 → 来源 session → lastTouched / version。

如果 `originSessionId` 为 null（来源 session 已删除）：`fromSession="(deleted)"`。

如果 `lastTouchedAt` 缺失或异常：`lastTouched=unknown`。

**实施要点**：

- `AgentPromptBuilder.renderDigest()` 改为额外查 `sessions` 表 join `originSessionId`（小代价，重复查询会被同 transaction cache 掉），批量查询用 `IN (?, ?, ...)` 一次完成
- digest 渲染字符上限沿用 `AgentPromptBuilder.MAX_RENDERED_CHARS = 1500`（与现有实现一致；digest 是 bootstrap 一次注入，非每次错误反馈，体量本来就小）；与 §5.7 错误模板的 3000 字符上限是两个独立预算，互不影响
- `humanizeDelta(deltaMs)` 实现共享 `EditConflictMarkdownFormatter` 的同一 helper

---

## 9. 实施分阶（Approach β）

### 9.1 Phase 1 — Backend Protocol & Migration

**目标**：协议层多 session 安全 + AGENTS.md 同步；前端布局保持当前 SplitView，仅适配新字段。

**任务（拓扑序，详见 §9.4 表）**

并行批次 A（独立可并行）：1.1（V13 migration）、1.5（formatter）、1.10（AGENTS.md）、1.11（digest renderer）、1.18（ui-objects-reference 重写）。其余按依赖串行。

**协议升级原子组（必须同 commit / 同 PR / 同发版）**：1.6（`/content` baseVersion required schema）+ 1.7（`apply_text_edits expectedText` required schema）+ 1.13（前端 apply_text_edits 调用方补 `expectedText`）+ 1.14（前端 `replaceSqlText` 升签名 + ui_patch 拼 `baseVersion`）。

**理由**：1.6/1.7 是后端 schema 校验（请求缺字段直接拒绝）；1.13/1.14 是前端补字段。如果 1.6/1.7 先发版而 1.13/1.14 没跟上，所有现有 ui_patch / ui_exec 调用都会被服务端拒绝（schema 强制 required），AI 即时降级。强制同 commit 落地避免该窗口。Phase 1 PR 拆分时这 4 个任务的代码必须放在同一个 PR、同一个 commit、同一个 build；CI 也要验证"前端能正常发请求 + 后端不再拒"的端到端契约。

**任务清单的 owner 标注**：1.6/1.7/1.13/1.14 应由同一个开发者（或同一个 sub-agent batch）拿走，避免协调成本。

**完成标志**：

- 协议 schema 校验拒绝缺 `baseVersion` / `expectedText` 的请求
- 双 session 并发 `apply_text_edits` 同 tab，后到者收 markdown error 完整且包含 expected vs current diff
- `mvn verify` + `npm run test` 全绿
- 用户 UX 零变化（NavTabs 仍在 sidebar，stage 仍单 tab 栏）

### 9.2 Phase 2 — Frontend Layout Migration

**目标**：A1 形态落地；sidebar NavTabs 拆除。

**完成标志**：

- sidebar 仅含 Sessions group
- StageWindow 内部三段式渲染正确
- 左 rail 关闭按钮不删 tab（A1 验证）
- 关 tab 后从左 rail 重新点回 → 状态完整恢复
- 视觉契约 review 通过（vitest snapshot + 手工对照 client/DESIGN.md token）

### 9.3 Phase 3 — State Globalization & Polish

**目标**：清掉所有 `*BySession` 映射、状态全局化收尾。

**完成标志**：

- `useStageStore` 内不再有任何 `*BySession` 字段
- session 切换 → stage 视图状态完全不变（验收 §11 V1）
- 全套测试矩阵绿

### 9.4 任务清单

| Phase | # | 任务 | 模块 |
|---|---|---|---|
| P1 | 1.1 | 写 migration 文件 `server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql`（含 backup 表 / drop 6 trigger / rebuild stage_tabs / rebuild FTS rowid / recreate 6 trigger，详见 §4.4） | infra |
| P1 | 1.2 | `StageTab` Java record 去 scope；`StageTabScope` enum 删除 | domain |
| P1 | 1.3 | `StageTabRepository` / `StageTabJdbcRepository` 去 scope；hydration 单次 loadAll | infra |
| P1 | 1.4 | `StageFindService` / `StageFindQuery` 删 scope filter；返回 originSessionTitle | application |
| P1 | 1.5 | `EditConflictMarkdownFormatter` + 5 种 code 单测 | application |
| P1 | 1.6 | `UiPatchAction` schema：`/content` `replace` 增 required `baseVersion` | adapter |
| P1 | 1.7 | `UiExecAction` schema：`apply_text_edits` 每条 edit 增 required `expectedText` | adapter |
| P1 | 1.8 | `UiExecAction` schema：新增 `detach` / `archive` / `trash`；`close` deprecate | adapter |
| P1 | 1.9 | `StageTabConcurrencyIT` 集成测：双虚拟线程并发写 → 后到者必拿 conflict + markdown | adapter |
| P1 | 1.10 | AGENTS.md 改写（Concurrency Contract / Library vs Workset / Action 列表） | adapter resources |
| P1 | 1.11 | `AgentPromptBuilder` `STAGE_TAB_DIGEST` 渲染升级 | application |
| P1 | 1.12 | `AgentPromptContractTest` 增 5 个 token 断言；`AgentPromptDigestTest` 新建 | adapter test |
| P1 | 1.13 | 前端 ui_exec apply_text_edits 调用方在适配器层补 `expectedText` 携带 | client |
| P1 | 1.14 | 前端 `useSqlWorkbenchStore.replaceSqlText` 升签名为 `(tabId, content, baseVersion)` 并新增 version 校验逻辑（不只是拼参数）；`useStageStore.replaceQueryEditorContent` 同步升签名；`ui_patch` 处理器读 `op.baseVersion` 拼入 + 把 `version_conflict` result 转换为 `error.markdown` | client |
| P1 | 1.15 | `stage-tab-api`: `listWorkspaceTabs` / `listSessionTabs` 删除并替换为 `listAll({ archived?, originSessionId? })`；`UpsertRequest.scope` 字段删除 | client |
| P1 | 1.16 | `StagePersistenceCoordinator.start()` 改用 `listAll({ archived: false })`；`stage-persistence-bootstrap`: `__hydrateWorkspaceTabs` / `__hydrateSessionTabs` 合并为 `__hydrateAll`；`persistedTabSummaries` / `resolveTabSnapshot` 去除 scope / `tabsBySession` 分支 | client |
| P1 | 1.17 | `StageTabsMigrationIT` 扩展：插入若干 row（trigger 触发 FTS 写入）→ 跑 V13 → 断言：(a) `stage_tab_index` 行数 = `stage_tabs` 行数；(b) FTS 仍能 `MATCH` 命中迁移前的 title / content；(c) 旧 trigger 完全消失（`SELECT name FROM sqlite_master WHERE type='trigger'` 只剩新 6 个）；(d) `scope` 列从 `PRAGMA table_info(stage_tabs)` 消失；(e) **migration 后向 `stage_tab_payload` INSERT 一条新 payload**（`tab_id` 引用新 `stage_tabs.id`），断言 trigger 正常更新 `stage_tab_index.content`、且 FK 约束生效（违法 `tab_id` 触发 SQLITE_CONSTRAINT）；(f) **session 删除 → CASCADE 不再发生**，`origin_session_id` 改 NULL，对应 stage_tab 仍存活 | infra IT |
| P1 | 1.18 | **`docs/references/ui-objects-reference.md` 大改写**：删除 `WORKSPACE_SCOPE_TYPES` 表 / `scope` 列；`workspace.close` → 标 deprecated；新增 `detach` / `archive` / `trash` 行；`workspace.focus` 行为升级；`apply_text_edits` 增 `expectedText` required；`/content` patch 增 `baseVersion` required；`query_editor.state` 字段表新增 `inWorkset: boolean` | docs |
| P1 | 1.19 | 全套回归（`mvn verify` + `npm run test` + `npx tsc --noEmit`） | all |
| P2 | 2.1 | 新建 `features/stage/components/left-rail/` 组件 | client |
| P2 | 2.2 | 复用 `useStageFind`；搜索框五态 token 映射 | client |
| P2 | 2.3 | StageWindow 水平分屏：左 rail + 隔条 + 右 pane | client |
| P2 | 2.4 | 删除 `<NavTabs />` 全套——具体文件：`client/src/features/workspace/components/nav-tabs.tsx`、`nav-tabs-row.tsx`、`nav-tabs-search.tsx` 三个文件 + 对应 `__tests__/nav-tabs.test.tsx`；`client/src/features/workspace/components/app-sidebar.tsx` 移除 `<NavTabs />` 引用与 import | client |
| P2 | 2.5 | 顶 tab 栏渲染源改 `openTabs`；新增 "+" 按钮 + 弹层 | client |
| P2 | 2.6 | 左 rail 行内菜单 Open / Pin / Archive / Trash | client |
| P2 | 2.7 | 视觉契约 review：左 rail 五态 / 行五态 / 顶栏三态 / kebab 三态 | client |
| P2 | 2.8 | i18n 键迁移 `sidebar.tabs.*` → `stage.leftRail.*`，zh-CN / en 同步 | client |
| P2 | 2.9 | `stage-rail.test.tsx` 新建；`stage-window.test.tsx` 三段断言 | client |
| P3 | 3.1 | `useStageStore` 状态切片重构（去 `*BySession`） | client |
| P3 | 3.2 | 全文 grep `BySession` / `forSession` / `clear(sid)` 回归 | client |
| P3 | 3.3 | `use-stage-auto-open` 单订阅化 | client |
| P3 | 3.4 | `session-store` 删 session 时的 stage cleanup 调用移除 | client |
| P3 | 3.5 | `stage-store.test.ts` 大改 | client |
| P3 | 3.6 | `split-view.test.tsx` 增"切 session 不动 stage"用例 | client |
| P3 | 3.7 | `stage-toggle-button.test.tsx` 改 mock | client |
| P3 | 3.8 | localStorage `stage.leftRail.width` / `stage.leftRail.collapsed` | client |
| P3 | 3.9 | 全套回归 + smoke IT | all |
| P4 | 4.1 | `workspace.close` deprecated alias 删除（3 版本后） | adapter |
| P4 | 4.2 | 用户反馈跟踪：是否需要 `openTabIds` 持久化 | product |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| `V13` migration 失败 | 用户已存 tabs 丢失 | (1) Flyway 在 SQLite 上对单一 migration 文件天然以**事务**包裹，DDL 失败自动 rollback 到 V12 状态（`PRAGMA foreign_keys = OFF` + `BEGIN` + 全部 DDL + `COMMIT`，Flyway 默认行为）。 (2) Migration 文件首语句加 `CREATE TABLE IF NOT EXISTS stage_tabs_backup_v13_pre AS SELECT * FROM stage_tabs;`（合法 SQLite 语法 `CREATE TABLE … AS SELECT`），事务 commit 后该备份表保留为 day-1 兜底；后续清理由独立的 V14 housekeeping migration 负责（不阻塞本变更）。 (3) 应用启动后探测：若 `stage_tabs` 行数远低于 `stage_tabs_backup_v13_pre` 行数，记 ERROR 日志 + degrade 拒绝写入并提示用户从 `~/.datatalk/datatalk.db` 文件级备份恢复（Tauri 安装目录默认有最近 N 份滚动备份）。**禁止**使用非 SQL 的 "BACKUP TABLE" 伪语法（之前草稿误用） |
| Phase 1 完成到 Phase 2 完成期间，NavTabs 仍在 sidebar 但其实已是 workspace tab | UX 略显矛盾 | Phase 1+2 之间窗口 ≤ 1 周；NavTabs 行可加一行 hint "即将移入工作台"（可选） |
| `expectedText` 强制后历史调用方未补字段 | AI 集成方报错 | DataTalk 是本地 MCP 唯一消费者；外部接入方在 release notes 通告 |
| `error.markdown` 太长污染 AI 上下文 | token 浪费 | 总硬上限 3000 字符（模板框架 ≈ 700 不计入，code block 内容 ≈ 2300 字符共享）；超过 truncate（首 8 + 末 8 行 + elision 提示）；多 edit 批次只渲染失败那条；详见 §5.7 字符预算 |
| 多 session 真并发时 frontend lock 死锁 | UI 卡 | per-tab lock 仅对 mutation；keystroke 不取锁；全局 5s 超时强 release |
| 用户 Monaco 改 + AI 同时改 | 用户编辑被打断 | Monaco 优先级最高；AI 收 markdown error 后必须重读，永远不会覆盖用户输入；AGENTS.md 写明 |
| Phase 2 删除 NavTabs 但有第三方组件引用 | 编译失败 | grep 全仓 `NavTabs` 名字回归；CLAUDE.md `Post-Edit Verification` 编译 gate |
| `originSessionTitle` 在 session rename 后未更新 | 显示陈旧标题 | 每次 ui_find / digest 时按 `originSessionId` 即时 join；不缓存 |
| `notifyArtifactArrived` 全局化后多 session 同时到 artifact 各自触发自动开 | 重复打开 | `autoOpened` 单 boolean：首次 artifact 到达时若 `open=false` → 触发自动开 + 置 `autoOpened=true`；后续 artifact 到达不再自动开。**`closeStage()`（用户手动关）必须同时 `autoOpened = false`**——这恢复了"用户主动关 → 下一次有新内容时再给一次机会"的语义，避免 per-session Set 全局化后丢失"每个新 session 都能自动开一次"的能力 |

---

## 11. 验收标准

### 11.1 功能性

| # | 验收 |
|---|---|
| V1 | 在 session A 打开 stage、保留任意 tab；切到 session B → stage 状态完全一致（open / maximized / left rail 展开/折叠 / 顶 tab 栏 / 内容区） |
| V2 | 在 session A 创建新 query_editor tab，切到 session B → 左 rail / 顶 tab 栏 都能看到；ui_find 任意 session 调用都返回该 tab |
| V3 | session A 与 session B 双向并发 `apply_text_edits` 同 tab → 后到者收 `error.markdown`，包含正确的 expected vs current diff，AI 按提示 `ui_read` + 重做 → 第二次成功 |
| V4 | 删除 session A → 其原创建的 tab 仍存在，`originSessionId` 变 null，左 rail 行的"来源"标签变"已删除"占位 |
| V5 | AI 提交 `apply_text_edits` 不带 `expectedText` → schema validation 拒绝 |
| V6 | AI 提交 `replace /content` 不带 `baseVersion` → schema validation 拒绝 |
| V7 | 左 rail 点 tab → 自动加入顶栏 + active；顶栏 X → 仅出顶栏，左 rail 仍在 |
| V8 | 左 rail 行右键 archive → 默认视图消失、归档分组可见；trash → DB 真删 |

### 11.2 协议契约

| # | 验收 |
|---|---|
| V9 | AGENTS.md 含 6 个关键 token（`Concurrency Contract` / `Library vs Workset` / `expectedText` / `error.markdown` / `baseVersion` / `inWorkset`）；`AgentPromptContractTest` 全过 |
| V10 | `STAGE_TAB_DIGEST` 渲染输出含 `originSession` / `version` / `lastTouched` 字段（**不**含 `inWorkset`），顺序稳定；`AgentPromptDigestTest` 全过 |
| V10b | `ui_read({ mode: 'state' })` 响应包含 `inWorkset: boolean` 字段，反映客户端 `openTabIds.has(tabId)` 实时态；`stage-ui-object-registry.test.tsx` 断言全过 |

### 11.3 视觉契约

| # | 验收 |
|---|---|
| V11 | 左 rail 搜索框、行、kebab 菜单的全部交互态映射到 client/DESIGN.md 语义 token；vitest snapshot + 手工 visual review 双签 |

### 11.4 性能 & 稳定

| # | 验收 |
|---|---|
| V12 | `mvn verify` 全绿；`npm run test` 全绿；`npx tsc --noEmit` 0 错 |
| V13 | `EndToEndSmokeIT` 通过；新增"双 session 并发 apply_text_edits → markdown error"用例通过 |
| V14 | 启动时 `loadAll(archived=false)` 在 100 个 active tab 量级下 < 200ms（与现状持平） |

---

## 12. 开放问题（默选答案，可翻案）

| # | 问题 | 默选 |
|---|---|---|
| O1 | `archived` 是否纳入 ui_find 默认返回？ | 否，需显式 `filter.includeArchived=true` |
| O2 | `originSessionTitle` 在 session 改名后是否随动？ | 是，每次 ui_find / digest 时按 `originSessionId` 即时 join；不缓存 |
| O3 | 顶 tab 栏最大并发显示数？超过怎么办？ | 不设硬上限；超过容器宽度走横向滚动 |
| O4 | 左 rail 默认 sort 是否允许用户切换？ | 否（仅 `lastTouchedAt desc` + pinned 置顶 + archived 折叠） |
| O5 | `notifyArtifactArrived()` 全局化后多 session 同时进 artifact 是否各自触发一次自动开 stage？ | 首次触发后置 `autoOpened=true`，后续不再自动开；**用户手动 `closeStage()` 时重置 `autoOpened=false`**——下次任意 session 有新 artifact 仍能再自动开一次（与 per-session 时代"每个新 session 都能自动开一次"语义近似但更克制） |
| O6 | localStorage 失败（Tauri 沙箱权限异常）的兜底？ | 默认值（240 / false）+ 静默 catch |
| O7 | `workspace.close` deprecation 周期？ | 3 个发版周期 |

---

## 13. 相关文档

- 承接：[Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-design.md) · [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md) · [Datatalk Client Design System](./2026-04-23-datatalk-client-design-system-design.md)
- 不变量参考：[CLAUDE.md](../../CLAUDE.md) Working Rules · [client/DESIGN.md](../../client/DESIGN.md)
- AGENTS.md 路径：`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Migration 路径：`server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql`
