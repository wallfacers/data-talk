# Query Editor Object Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `query_editor` 收敛为由 `StageStore` 统一打开、命名和维护的对象，并通过 `WorkspaceAdapter` / `QueryEditorAdapter` 对 AI 暴露稳定的 `state + actions + capabilities` 与文件式 SQL 编辑语义。

**Architecture:** 保持现有双 store 结构，但收紧职责边界：`StageStore` 成为 `query_editor` 打开、命名、聚焦、上下文写入和文档编辑的公开入口；`useSqlWorkbenchStore` 继续承接执行状态、结果、history、limit 等 workbench runtime，并补齐 `version / selection` 等文档编辑元数据。旧入口（工作台 `+`、direct SQL helper、`WorkspaceAdapter.exec('open')`）统一收敛到 `openQueryEditor()`，`QueryEditorAdapter` 与 `UIRouter` 负责把这套内部能力整理成 AI 可消费的对象契约与结构化错误。

**Tech Stack:** React 19 + TypeScript + Zustand + Vitest + DataTalk UI Router

**Spec:** `docs/product-specs/2026-04-23-query-editor-object-actions-design.md`

---

## Scope

**In scope**

- 统一 `query_editor` 的打开 / 标题分配 / 聚焦 / 上下文初始化语义
- 为 `query_editor` 暴露标准化 `state / actions / patchCapabilities / capabilities`
- 支持 `ui_patch(/content)` 全量覆盖与 `apply_text_edits` range 编辑
- 为 `UIRouter` 增加可自救的结构化错误返回
- 同步运行时 AGENTS 文档与 UI object 参考文档

**Out of scope**

- 不改后端 `/api/sql/execute` 契约
- 不改 markdown `sql-execute` 当前“派发给 composer”语义
- 不把所有 Stage tab 都做成文件对象；本轮只处理 `query_editor`

## Spec Mapping

- `§5 StageStore 作为唯一真相源` → Task 1 / Task 2
- `§6 Query Editor 对象模型` → Task 3
- `§7 SQL 作为“文件内容”编辑` → Task 1 / Task 3
- `§8 对象 actions 设计` → Task 3 / Task 4
- `§9 四条入口的统一映射` → Task 2
- `§10 Adapter 改造` → Task 2 / Task 3 / Task 4
- `§11 AGENTS.md 同步修改清单` → Task 4
- `§12 测试策略` → Task 1-5
- `§13 风险与兼容性` → 通过 facade 迁移、结构化错误和回归验证在 Task 2-5 收口

## File Structure

### Core State / Helpers

| 文件 | 角色 | 改动 |
|------|------|------|
| `client/src/stores/stage-store.ts` | Stage tab 生命周期与 query_editor 打开入口 | 新增 `openQueryEditor`、标题唯一性、上下文/内容编辑 action |
| `client/src/stores/stage-store.test.ts` | Stage store 回归 | 新增可见标题计数、open mode、workspace focus 规则测试 |
| `client/src/features/stage/stores/sql-workbench-store.ts` | SQL workbench runtime | 新增 `version / selection` 与 text edit 支撑 |
| `client/src/features/stage/stores/sql-workbench-store.test.ts` | runtime 回归 | 新增 replace/edit/version 冲突测试 |
| `client/src/features/stage/utils/normalize-query-editor-payload.ts` | 旧 payload 兼容层 | 补齐新 entry/context 字段的向后兼容归一化 |
| `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts` | payload 回归 | 覆盖旧新 payload 混用场景 |

### Entry Unification

| 文件 | 角色 | 改动 |
|------|------|------|
| `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts` | 旧 Stage tool helper | `query_editor` 分支降级为 facade，非 SQL tab 逻辑保持不变 |
| `client/src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts` | helper 回归 | 改为验证委托 `openQueryEditor()` 结果 |
| `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` | direct SQL helper | 改为委托 `openQueryEditor()` |
| `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts` | direct SQL 回归 | 验证 session/context/autoRun 参数透传 |
| `client/src/features/stage/components/stage-window.tsx` | Stage 空态 CTA | `+ / SQL 编辑器` 直连新的 store action |
| `client/src/features/stage/components/stage-window.test.tsx` | Stage UI 回归 | 验证空态打开 SQL 时名称递增、workspace focus 正确 |

### Object Contract / Shared Commands

| 文件 | 角色 | 改动 |
|------|------|------|
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | `workspace` UI object | `open(query_editor)` 只做参数翻译，不再手工造 tab |
| `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts` | workspace adapter 回归 | 验证两条 `open(query_editor)` 分支都委托 store |
| `client/src/features/stage/adapters/QueryEditorAdapter.ts` | `query_editor` UI object | 输出标准 state/actions/capabilities；支持 patch/exec |
| `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` | query editor adapter 回归 | 覆盖 state shape、六条 action、patch 白名单、version conflict |
| `client/src/features/stage/components/sql-workbench-tab.tsx` | 用户侧 SQL 工作台 | 与 adapter 共享 run/format/edit 语义，避免双实现漂移 |
| `client/src/features/stage/components/sql-workbench-tab.test.tsx` | SQL 工作台回归 | 验证 toolbar 行为仍与 adapter 暴露语义一致 |
| `client/src/features/stage/utils/query-editor-actions.ts` | 新增共享命令层 | 封装 `runQueryEditorSql / formatQueryEditorSql / setQueryEditorContext` |
| `client/src/features/stage/utils/query-editor-actions.test.ts` | 新增命令层回归 | 覆盖 run/format/set_context 的纯函数或模块行为 |

### Router / Docs

| 文件 | 角色 | 改动 |
|------|------|------|
| `client/src/services/ui-router/errors.ts` | UI Router 错误工厂 | 升级为 `code/message/hint/...` 结构 |
| `client/src/services/ui-router/UIRouter.ts` | patch/exec 校验入口 | 输出 `unsupported_patch / unknown_action / invalid_params` 结构化错误 |
| `client/src/services/ui-router/__tests__/UIRouter.test.ts` | Router 回归 | 验证错误 shape 与 hint/availableActions |
| `docs/references/ui-objects-reference.md` | UI object 参考 | 同步 `query_editor` 新 state / actions / patch paths |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | 运行时 AI 指令 | 同步 `query_editor` 可见字段、动作表、编辑规范 |

## Dependency Order

本计划以契约收敛为主，任务存在明确依赖，不建议一开始就全量并行：

1. Task 1 定义 store contract
2. Task 2 让所有入口都改走新 contract
3. Task 3 在稳定 contract 上升级 adapter 和共享命令层
4. Task 4 再收口 Router 错误与文档
5. Task 5 做整体验证与收尾

## Task 1: 建立 query_editor 共享 store contract

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.test.ts`

- [x] **Step 1: 先写 stage-store 红灯测试，锁定统一打开语义**

在 `client/src/stores/stage-store.test.ts` 增加以下覆盖：

```typescript
it('allocates SQL editor titles from visible workspace + session query editors', () => {
  const store = useStageStore.getState()

  store.openQueryEditor({
    sessionId: 's1',
    scope: 'workspace',
    baseTitle: 'SQL 编辑器',
    openMode: 'always_new',
    entryMode: 'blank',
  })
  store.openQueryEditor({
    sessionId: 's1',
    scope: 'session',
    baseTitle: 'SQL 编辑器',
    openMode: 'always_new',
    entryMode: 'direct_sql',
    initialContent: 'select 1',
  })

  const titles = store.listTabs('s1').filter((tab) => tab.type === 'query_editor').map((tab) => tab.title)
  expect(titles).toEqual(['SQL 编辑器', 'SQL 编辑器2'])
})

it('reuses the same resource-scoped query editor when openMode is reuse_by_resource_context', () => {
  const store = useStageStore.getState()
  const first = store.openQueryEditor({
    sessionId: 's1',
    scope: 'session',
    baseTitle: 'SQL 编辑器',
    openMode: 'reuse_by_resource_context',
    entryMode: 'ui_exec',
    connectionId: 'conn-1',
    database: 'analytics',
    schema: 'public',
  })
  const second = store.openQueryEditor({
    sessionId: 's1',
    scope: 'session',
    baseTitle: 'SQL 编辑器',
    openMode: 'reuse_by_resource_context',
    entryMode: 'ui_exec',
    connectionId: 'conn-1',
    database: 'analytics',
    schema: 'public',
  })

  expect(second).toEqual({ tabId: first.tabId, created: false })
})
```

- [x] **Step 2: 再写 sql-workbench-store 红灯测试，锁定文件式编辑语义**

在 `client/src/features/stage/stores/sql-workbench-store.test.ts` 增加 `version / selection / applyTextEdits` 场景：

```typescript
it('replaces full content and increments version', () => {
  const store = useSqlWorkbenchStore.getState()
  store.ensureTab('tab-1', { sqlText: 'select 1' })

  store.replaceSqlText('tab-1', 'select 2')

  expect(useSqlWorkbenchStore.getState().tabsById['tab-1']).toMatchObject({
    sqlText: 'select 2',
    version: 2,
  })
})

it('rejects stale baseVersion when applying text edits', () => {
  const store = useSqlWorkbenchStore.getState()
  store.ensureTab('tab-1', { sqlText: 'select 1' })
  store.replaceSqlText('tab-1', 'select 11')

  const result = store.applyTextEdits('tab-1', {
    baseVersion: 1,
    edits: [{ range: { startLine: 1, startColumn: 8, endLine: 1, endColumn: 9 }, text: '2' }],
  })

  expect(result.ok).toBe(false)
  expect(result.code).toBe('version_conflict')
  expect(result.currentState?.version).toBe(2)
})
```

- [x] **Step 3: 在 StageStore 增加 query_editor 专用公开 action**

在 `client/src/stores/stage-store.ts` 新增这些签名，并让所有 `query_editor` 打开/聚焦都走它们：

```typescript
type QueryEditorOpenMode = 'always_new' | 'reuse_by_resource_context'

type QueryEditorOpenInput = {
  sessionId: string | null
  scope: 'workspace' | 'session'
  baseTitle: string
  openMode: QueryEditorOpenMode
  entryMode: 'blank' | 'direct_sql' | 'resource_sql' | 'ui_exec' | 'ai_open'
  initialContent?: string
  autoRun?: boolean
  connectionId?: string | null
  connectionName?: string | null
  database?: string | null
  schema?: string | null
}

type QueryEditorTextEdit = {
  range: {
    startLine: number
    startColumn: number
    endLine: number
    endColumn: number
  }
  text: string
}

type QueryEditorEditResult =
  | { ok: true; version: number; content: string }
  | { ok: false; code: 'version_conflict'; currentState: { version: number; content: string } }

openQueryEditor(input: QueryEditorOpenInput): { tabId: string; created: boolean }
setQueryEditorContext(tabId: string, context: { connectionId?: string | null; connectionName?: string | null; database?: string | null; schema?: string | null }): void
replaceQueryEditorContent(tabId: string, content: string): { version: number }
applyQueryEditorTextEdits(tabId: string, params: { baseVersion: number; edits: QueryEditorTextEdit[] }): QueryEditorEditResult
setQueryEditorCursor(tabId: string, cursor: { line: number; column: number }): void
```

实现要求：

- 标题只按“当前 session 可见的 workspace + session `query_editor`”计数
- `scope === 'workspace'` 打开成功时，必须把 `activeTabIdBySession.get(sessionId)` 置为 `null`
- `openMode === 'reuse_by_resource_context'` 只按 `connectionId + database + schema + type` 复用 session tab
- `openQueryEditor()` 内部负责调用 `useSqlWorkbenchStore.ensureTab()` 初始化内容，不允许调用方再单独补一遍

- [x] **Step 4: 在 sql-workbench-store 补齐文档元数据，但不抢走 lifecycle 入口**

在 `client/src/features/stage/stores/sql-workbench-store.ts` 补齐：

```typescript
type SqlWorkbenchTabState = {
  sqlText: string
  version: number
  selection: {
    startLine: number
    startColumn: number
    endLine: number
    endColumn: number
  } | null
  // 其余现有字段保持
}

replaceSqlText(tabId: string, sqlText: string): { version: number }
applyTextEdits(tabId: string, params: { baseVersion: number; edits: QueryEditorTextEdit[] }): QueryEditorEditResult
setSelection(tabId: string, selection: {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
} | null): void
```

约束：

- `ensureTab()` 初始 `version = 1`
- 成功改文档时 version 自增；`setRunning/applyExecuteSuccess` 不改 version
- `dirty` 继续由 `sqlText !== savedSqlText` 派生，不新增第三个来源

- [x] **Step 5: 运行 store 定向测试**

Run:

```bash
cd client && npx vitest run src/stores/stage-store.test.ts src/features/stage/stores/sql-workbench-store.test.ts
```

Expected: PASS

## Task 2: 把所有 query_editor 入口收口到 openQueryEditor()

**Files:**
- Modify: `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts`
- Modify: `client/src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts`
- Modify: `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts`
- Modify: `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [x] **Step 1: 先把旧 helper 测试改成“验证委托”而不是“验证手工拼 tab”**

把两个 helper 测试的断言方向改成：

- `open-or-focus-stage-tool-tab` 的 `query_editor` 分支返回 `openQueryEditor()` 的 `{tabId, created}`
- `open-direct-sql-query-editor-tab` 负责透传 `sessionId / connectionId / database / schema / autoRun / sql`
- `StageWindow` 的空态 SQL CTA 直接命中 `useStageStore.getState().openQueryEditor(...)`

其中 `StageWindow` 新断言应覆盖：

```typescript
expect(openQueryEditorMock).toHaveBeenCalledWith(expect.objectContaining({
  sessionId: 's1',
  scope: 'workspace',
  baseTitle: 'SQL 编辑器',
  openMode: 'always_new',
  entryMode: 'blank',
}))
```

- [x] **Step 2: 把 open-or-focus-stage-tool-tab 的 query_editor 分支降级为 facade**

在 `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts` 中保留非 SQL 类型现状，仅替换 `tool === 'sql'` 路径：

```typescript
if (tabType === 'query_editor') {
  return useStageStore.getState().openQueryEditor({
    sessionId,
    scope: target.kind === 'global_tool' ? 'workspace' : 'session',
    baseTitle: target.title,
    openMode: target.kind === 'global_tool' ? 'always_new' : 'reuse_by_resource_context',
    entryMode: target.kind === 'global_tool' ? 'blank' : 'resource_sql',
    connectionId: target.kind === 'resource_tool' ? target.connectionId : null,
    database: target.kind === 'resource_tool' ? target.database ?? null : null,
    schema: target.kind === 'resource_tool' ? target.schema ?? null : null,
  })
}
```

- [x] **Step 3: 把 direct SQL helper 改为 facade**

在 `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` 中删除手工 `StageTab` 构造，替换为：

```typescript
const { tabId } = useStageStore.getState().openQueryEditor({
  sessionId,
  scope: 'session',
  baseTitle,
  openMode: 'always_new',
  entryMode: 'direct_sql',
  initialContent: sql,
  autoRun,
  connectionId,
  connectionName,
  database: sessionContext?.database ?? null,
  schema: sessionContext?.schema ?? null,
})
```

保留函数签名不变，这样 `prompt-composer` 和 `user-bubble` 不需要改调用代码。

- [x] **Step 4: 让 StageWindow 的空态 SQL CTA 直接调用 store action**

在 `client/src/features/stage/components/stage-window.tsx` 中移除 `openOrFocusStageToolTab` 对空态 SQL CTA 的依赖，改为直接调用 `openQueryEditor()`：

```typescript
useStageStore.getState().openQueryEditor({
  sessionId: sessionId ?? null,
  scope: 'workspace',
  baseTitle: t('stage.toolRow.sql'),
  openMode: 'always_new',
  entryMode: 'blank',
})
```

这样 `+` 的语义与 spec §9.1 对齐，不再受 `global_tool/sql` 的旧 dedupe 逻辑影响。

- [x] **Step 5: 运行入口统一相关测试**

Run:

```bash
cd client && npx vitest run \
  src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts \
  src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts \
  src/features/stage/components/stage-window.test.tsx
```

Expected: PASS

## Task 3: 升级 query_editor 对象契约，并抽出共享命令层

**Files:**
- Modify: `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- Modify: `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts`
- Create: `client/src/features/stage/utils/query-editor-actions.ts`
- Create: `client/src/features/stage/utils/query-editor-actions.test.ts`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Modify: `docs/references/ui-objects-reference.md`

- [x] **Step 1: 先写 adapter 红灯测试，锁定 state/actions/capabilities**

在 `QueryEditorAdapter.test.ts` 增加这些断言：

```typescript
expect(adapter.read('actions')).toEqual([
  expect.objectContaining({ name: 'apply_text_edits' }),
  expect.objectContaining({ name: 'set_context' }),
  expect.objectContaining({ name: 'run_sql' }),
  expect.objectContaining({ name: 'format_sql' }),
  expect.objectContaining({ name: 'focus' }),
  expect.objectContaining({ name: 'close' }),
])

expect(adapter.read('full')).toEqual(expect.objectContaining({
  capabilities: {
    editableContent: true,
    acceptsTextEdits: true,
    runnable: true,
    formattable: true,
    supportsContextBinding: true,
    supportsResults: true,
  },
}))
```

并显式检查：

- `state.content / version / dirty / cursor / selection / executeStatus / results / activeResultId / limit`
- `results` 中只允许摘要字段，断言 `rows` 不存在
- `patch('/content')` 成功，`patch('/title')` 返回 `unsupported_patch`
- `apply_text_edits(baseVersion=旧值)` 返回 `version_conflict`

- [x] **Step 2: 更新 payload 兼容层，接受新入口命名但保留旧值兼容**

在 `normalize-query-editor-payload.ts` 中把新旧 entry mode 做兼容映射，至少支持：

```typescript
'manual' -> 'blank'
'resource' -> 'resource_sql'
'ai_generated' -> 'ai_open'
```

并保证 `initialSql/sql` 老字段仍然能归一化到统一 `content` 初值。

- [x] **Step 3: 新增共享命令层，让 adapter 与 SqlWorkbenchTab 共用 run/format/context 语义**

新增 `client/src/features/stage/utils/query-editor-actions.ts`，至少暴露：

```typescript
export async function runQueryEditorSql(params: {
  tabId: string
  sessionId: string | null
  limit?: 10 | 100 | 1000 | null
}): Promise<{ executeStatus: 'success' | 'risk_blocked' | 'error'; activeResultId: string | null }>

export function formatQueryEditorSql(tabId: string): { version: number; content: string }

export function setQueryEditorContext(params: {
  tabId: string
  connectionId?: string | null
  database?: string | null
  schema?: string | null
}): void
```

要求：

- `runQueryEditorSql()` 复用现有 `executeSql()` API、`useSqlWorkbenchStore.setRunning/applyExecuteSuccess/setRiskBlocked/setError`
- `formatQueryEditorSql()` 复用已有 `formatSql()` 工具，并通过 `replaceQueryEditorContent()` 更新文档，保证 version 自增
- `SqlWorkbenchTab` 的 toolbar `Run / Format` 也改调这个共享层，避免用户路径与 adapter 路径产生分叉

- [x] **Step 4: 升级 WorkspaceAdapter 与 QueryEditorAdapter**

实现要求：

- `WorkspaceAdapter.exec('open')` 在 `type === 'query_editor'` 时只做参数翻译，不再手工 `openTab()`
- `QueryEditorAdapter.read('state')` 从 `StageStore + useSqlWorkbenchStore` 组装标准 state
- `QueryEditorAdapter.patchCapabilities` 仅允许：

```typescript
[
  { pathPattern: '/content', ops: ['replace'] },
  { pathPattern: '/connectionId', ops: ['replace'] },
  { pathPattern: '/database', ops: ['replace'] },
  { pathPattern: '/schema', ops: ['replace'] },
]
```

- `QueryEditorAdapter.patch()` 实现 `/content` 和单字段 context patch
- `QueryEditorAdapter.exec()` 只暴露六条 action，并全部转调 store action 或共享命令层

- [x] **Step 5: 同步 UI object 参考文档**

更新 `docs/references/ui-objects-reference.md` 中 `query_editor` 章节，至少同步：

- `read(state)` 字段从 `sql/source/...` 升级到 `content/version/dirty/...`
- `patch` 不再写“只读”，而是列出 `/content /connectionId /database /schema`
- `exec actions` 只保留六条：`apply_text_edits / set_context / run_sql / format_sql / focus / close`

- [x] **Step 6: 运行对象契约相关测试**

Run:

```bash
cd client && npx vitest run \
  src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
  src/features/stage/utils/query-editor-actions.test.ts \
  src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts \
  src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
  src/features/stage/components/sql-workbench-tab.test.tsx
```

Expected: PASS

## Task 4: 收口 UIRouter 错误契约并同步运行时 AGENTS 文档

**Files:**
- Modify: `client/src/services/ui-router/errors.ts`
- Modify: `client/src/services/ui-router/UIRouter.ts`
- Modify: `client/src/services/ui-router/__tests__/UIRouter.test.ts`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 1: 先写 Router 红灯测试，锁定错误结构**

在 `client/src/services/ui-router/__tests__/UIRouter.test.ts` 增加：

```typescript
expect(bad.error).toContain('Unsupported')
expect(bad.data).toEqual(expect.objectContaining({
  code: 'unsupported_patch',
  hint: expect.stringContaining('/content'),
}))

expect(unknownAction.data).toEqual(expect.objectContaining({
  code: 'unknown_action',
  availableActions: ['apply_text_edits', 'set_context', 'run_sql', 'format_sql', 'focus', 'close'],
}))

expect(missingParams.data).toEqual(expect.objectContaining({
  code: 'invalid_params',
  expectedSchema: expect.any(Object),
}))
```

- [x] **Step 2: 把 errors.ts 升级为结构化错误工厂**

把 `client/src/services/ui-router/errors.ts` 从字符串拼接升级为统一 detail shape：

```typescript
export type UIErrorDetail = {
  code: string
  message: string
  hint?: string
  availableActions?: string[]
  expectedSchema?: unknown
  currentState?: unknown
}
```

`execError()` / `patchError()` 需要同时保留：

- 顶层人类可读 `error/message`
- `data` 内机器可读 detail，供 AI 自救

- [x] **Step 3: 在 UIRouter 中为 patch/exec 校验错误填充 hint 和替代路径**

在 `client/src/services/ui-router/UIRouter.ts` 中收口这些场景：

- 未命中 `patchCapabilities` → `unsupported_patch`
- 未命中 `read('actions')` → `unknown_action`
- 缺必填参数 → `invalid_params`

实现要求：

- `unsupported_patch` 需要把允许路径和可用 action 名写进 `hint` / `availableActions`
- `unknown_action` 需要把 `read('actions')` 中实际动作名回给调用方
- 保留现有 `UIResponse.error` 兼容字符串，但 `UIResponse.data` 必须同时带 detail

- [x] **Step 4: 同步运行时 AGENTS.md 的 query_editor 章节**

更新 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`，至少包含：

- `datatalk.ui.patch` 支持的四条 `query_editor` patch path
- `datatalk.ui.exec` 中 `query_editor` 的六条 action
- `datatalk.ui.read` 对 `query_editor` 返回字段说明
- 新增“Query Editor 编辑规范”小节，明确：
  - 整段覆盖用 `ui_patch(/content, replace)`
  - 精确编辑用 `ui_exec(apply_text_edits, { baseVersion, edits })`
  - `results` 只返回摘要，不返回原始 rows
  - 出现 `hint` 时按 hint 自救，不要复述给用户

- [x] **Step 5: 运行 Router 定向测试**

Run:

```bash
cd client && npx vitest run src/services/ui-router/__tests__/UIRouter.test.ts
```

Expected: PASS

## Task 5: 综合验证与收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-23-query-editor-object-actions-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: 跑本计划涉及的前端回归套件**

Run:

```bash
cd client && npx vitest run \
  src/stores/stage-store.test.ts \
  src/features/stage/stores/sql-workbench-store.test.ts \
  src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts \
  src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts \
  src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
  src/features/stage/utils/query-editor-actions.test.ts \
  src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts \
  src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
  src/features/stage/components/stage-window.test.tsx \
  src/features/stage/components/sql-workbench-tab.test.tsx \
  src/services/ui-router/__tests__/UIRouter.test.ts
```

Expected: PASS

- [x] **Step 2: 跑前端类型检查**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: exit code 0, no type errors

- [x] **Step 3: 做手动 smoke**

状态说明：本轮在 CLI 环境完成的是脚本化 smoke，而非桌面端人工视觉联调。计划要求的 5 个场景分别由 `stage-store` / `open-direct-sql-query-editor-tab` / `QueryEditorAdapter` / `UIRouter` 相关回归用例覆盖：标题递增、session query editor 新开、`ui_patch(/content)` 后 `content/version` 更新、`version_conflict + currentState.version` 返回、以及 `results` 不含 `rows`。

至少手动验证以下场景：

1. 当前 session 点工作台空态 `SQL 编辑器` 两次，标题依次为 `SQL 编辑器`、`SQL 编辑器2`
2. `!select 1` 连续执行两次，各自打开新的 session `query_editor`
3. AI 通过 `ui_patch(/content)` 能覆盖 SQL，`ui_read(state)` 返回新 `content/version`
4. AI 传旧 `baseVersion` 调 `apply_text_edits` 时，返回 `code: 'version_conflict'` 与 `currentState.version`
5. `ui_read(state).results` 不包含 `rows`

- [x] **Step 4: 执行计划与索引收尾**

实现完成后必须同步：

- 把本计划文件里的所有复选框更新为实际状态
- 在 `docs/exec-plans/index.md` 中把本计划从 `Active` 移到 `Completed`
- 如运行时对象契约有偏差，反向修正 `docs/references/ui-objects-reference.md` 与 `server/.../AGENTS.md`

## Decision Log

- 2026-04-23: 初版计划不把执行结果和 history 从 `useSqlWorkbenchStore` 挪回 `StageStore`；`StageStore` 只作为 query_editor 生命周期和公开 action 的统一入口，避免制造第三份状态来源。
