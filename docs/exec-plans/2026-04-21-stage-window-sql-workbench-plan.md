# Stage Window SQL Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将当前 Stage 重构为基于 `shadcn/ui` 的多面板 SQL 工作台，用统一的 `query_editor` 取代 `bang_query` 页面，并同步收敛 UI Object 协议与资源目录 `AGENTS.md`。

**Architecture:** 前端继续沿用现有 `StageWindow -> StageSidebar -> StageTabBar -> StageTabContent` workbench 骨架，但右侧内容区升级为多面板 SQL 工作页和统一空态 / 占位页。`query_editor` 将扩展为唯一 SQL 工作页模型，承接手动打开、资源树打开、AI 预填和 `!select / !with` 直查四种入口；`bang_query` 仅保留聊天消息语义与持久化接口，不再保留独立 tab / adapter / UIObject。协议层新增 `QueryEditorAdapter`，并把 `workspace` / `query_editor` 运行时注册到 `UIRouter`。

**Tech Stack:** React 19、Zustand、Vitest、Testing Library、shadcn/ui、CodeMirror 6、现有 `useStageStore` / `use-sql-execute` / `UIRouter`

**Design Spec:** `docs/product-specs/2026-04-21-stage-window-sql-workbench-design.md`

---

## Spec Mapping

- Spec §3 硬约束（`shadcn/ui`、文档同步）→ Task 1 / Task 2 / Task 5 / Task 6
- Spec §4 总体架构 → Task 1 / Task 3
- Spec §5 页面布局映射 → Task 1 / Task 2
- Spec §6 SQL 工作页统一模型 → Task 2 / Task 4
- Spec §7 交互与数据流 → Task 2 / Task 3 / Task 4
- Spec §8 组件与代码层变更 → Task 1 / Task 2 / Task 3 / Task 4
- Spec §9 协议与文档同步 → Task 5
- Spec §10 错误处理 → Task 2 / Task 4
- Spec §11 测试策略 → Task 1-6 全覆盖
- Spec §12 分批实施建议 → Task 1-6 顺序映射

## File Map

### Create

| 文件 | 责任 |
|------|------|
| `client/src/features/stage/components/stage-workbench-empty-state.tsx` | Stage 无 tab 时的统一空态工作台 |
| `client/src/features/stage/components/stage-placeholder-tab.tsx` | ER / Report / Dashboard 共用占位工作页 |
| `client/src/features/stage/components/query-editor-toolbar.tsx` | Query Editor 顶部上下文与运行工具栏 |
| `client/src/features/stage/components/query-editor-result-panel.tsx` | Query Editor 底部嵌入结果区 |
| `client/src/features/stage/components/query-editor-inspector.tsx` | Query Editor 右侧 inspector / metadata 面板 |
| `client/src/features/stage/components/stage-ui-object-registry.tsx` | 统一注册 `workspace` 与 `query_editor` UIObject |
| `client/src/features/stage/adapters/QueryEditorAdapter.ts` | `query_editor` 的 UIObject adapter |
| `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` | `query_editor` adapter 单测 |
| `client/src/features/stage/components/stage-ui-object-registry.test.tsx` | UIObject 运行时注册测试 |
| `client/src/features/stage/utils/normalize-query-editor-payload.ts` | 兼容旧 payload 并输出统一 `query_editor` 状态 |
| `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts` | payload 归一化测试 |
| `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` | `!select / !with` 直查改为打开 `query_editor` 的 helper |
| `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts` | 直查 helper 测试 |

### Modify

| 文件 | 改动 |
|------|------|
| `client/src/features/session/split-view.tsx` | 移除旧 `StageTabStrip` / `ArtifactCanvas` 默认链路，接入新 empty state |
| `client/src/features/stage/components/stage-window.tsx` | 接入 UIObject registry，统一 no-tab / active-tab workbench 布局 |
| `client/src/features/stage/components/stage-window.test.tsx` | 覆盖新空态与共享工作台壳体 |
| `client/src/features/stage/components/stage-tab-content.tsx` | 为 `er_canvas` / `report` / `dashboard` 添加统一占位页，并删除 `bang_query` 分支 |
| `client/src/features/stage/components/query-editor-tab.tsx` | 重构为多面板工作页，支持统一 payload 模型和结果回写 |
| `client/src/features/stage/components/query-editor-tab.test.tsx` | 扩充 direct_sql / ai_generated / 无连接 / 自动执行场景 |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | 删除 `bang_query` 假设，保留 `query_editor` / 其他工作台 tab 行为 |
| `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts` | 去掉 `bang_query` 断言，补 `query_editor` / `er_canvas` 断言 |
| `client/src/features/session/prompt-composer.tsx` | `!select / !with` 路径改走 `open-direct-sql-query-editor-tab` |
| `client/src/features/session/__tests__/prompt-composer.test.tsx` | 断言直查仍持久化消息，但打开的是 `query_editor` |
| `client/src/stores/stage-store.test.ts` | 若存在 `bang_query` 断言，则改成新的 SQL / placeholder tab 断言 |
| `client/src/i18n/messages.ts` | 新增 empty state / inspector / direct_sql / placeholder 页文案 |
| `docs/FRONTEND.md` | 同步新的 Stage workbench 与 `query_editor` 约定 |
| `docs/references/ui-objects-reference.md` | 用 `query_editor` 替换 `bang_query` 协议定义 |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | 删除 `bang_query` UIObject 说明，改为 `query_editor` |
| `docs/exec-plans/index.md` | 登记本计划；完成后从 Active 移到 Completed |

### Delete

| 文件 | 原因 |
|------|------|
| `client/src/features/stage/components/bang-query-tab.tsx` | `bang_query` 页面退场 |
| `client/src/features/stage/components/bang-query-tab.test.tsx` | 删除对应页面测试 |
| `client/src/features/stage/adapters/BangQueryAdapter.ts` | `bang_query` UIObject 退场 |
| `client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts` | 删除对应 adapter 测试 |
| `client/src/features/stage/utils/open-bang-query-tab.ts` | 由 `open-direct-sql-query-editor-tab.ts` 替代 |
| `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts` | 删除对应 helper 测试 |
| `client/src/features/stage/components/stage-tab-strip.tsx` | 旧第二条 tab chrome 退出默认链路后删除 |

### Keep As-Is

| 文件 | 说明 |
|------|------|
| `client/src/services/api/bang-query-message.ts` | 继续负责持久化直查用户消息；只保留聊天消息语义，不再打开 `bang_query` 页 |
| `client/src/features/chat/components/turn/user-bubble.tsx` 等 `bang_query_user` 相关渲染 | 保持直查消息的聊天可见性，不在本计划内改名 |

### Verify

| 命令 | 用途 |
|------|------|
| `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx src/features/stage/components/query-editor-tab.test.tsx src/features/stage/components/stage-ui-object-registry.test.tsx src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts src/features/session/__tests__/prompt-composer.test.tsx` | 关键前端专项验证 |
| `cd client && npx tsc --noEmit` | 前端类型检查 |
| `rg -n "bang_query" client/src docs/references/ui-objects-reference.md server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | 最终确认仅保留允许存在的消息语义引用 |

---

## Task 1: Shared Workbench Shell, Empty State, and Placeholder Tabs

**Files:**
- Create: `client/src/features/stage/components/stage-workbench-empty-state.tsx`
- Create: `client/src/features/stage/components/stage-placeholder-tab.tsx`
- Modify: `client/src/features/session/split-view.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/i18n/messages.ts`
- Delete: `client/src/features/stage/components/stage-tab-strip.tsx`

- [x] **Step 1: Extend `stage-window.test.tsx` with failing shell + empty-state assertions**

Add tests that currently fail because the code still renders the legacy children path:

```tsx
it('renders the new Stage empty workbench instead of the legacy stage tab strip path', () => {
  useStageStore.setState({
    workspaceTabs: [],
    tabsBySession: new Map(),
    activeWorkspaceTabId: null,
    activeTabIdBySession: new Map([['s1', null]]),
  })

  render(<StageWindow sessionId="s1"><div>legacy child</div></StageWindow>)

  expect(screen.getByTestId('stage-empty-workbench')).toBeInTheDocument()
  expect(screen.queryByText('legacy child')).toBeNull()
})

it('routes er/report/dashboard tabs into the shared placeholder scaffold', () => {
  useStageStore.setState({
    workspaceTabs: [
      { tabId: 'er-1', type: 'er_canvas', title: 'ER', scope: 'workspace', createdAt: 0, payload: {} },
    ],
    activeWorkspaceTabId: 'er-1',
    tabsBySession: new Map(),
    activeTabIdBySession: new Map([['s1', null]]),
  })

  render(<StageWindow sessionId="s1"><div>legacy child</div></StageWindow>)

  expect(screen.getByTestId('stage-placeholder-tab')).toHaveTextContent('ER')
})
```

- [x] **Step 2: Create `stage-workbench-empty-state.tsx`**

Implement a reusable empty-state shell that stays inside the Stage workbench visual system:

```tsx
import { DatabaseIcon, SparklesIcon } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

type Props = {
  title: string
  description: string
  primaryActionLabel: string
  onPrimaryAction?: () => void
}

export function StageWorkbenchEmptyState({
  title,
  description,
  primaryActionLabel,
  onPrimaryAction,
}: Props) {
  return (
    <div data-testid="stage-empty-workbench" className="flex h-full min-h-0 flex-col gap-4 overflow-auto p-4">
      <Card className="border-border/60 bg-background/95">
        <CardHeader>
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <DatabaseIcon className="size-5" />
          </div>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          <Button type="button" onClick={onPrimaryAction}>
            {primaryActionLabel}
          </Button>
          <div className="inline-flex items-center gap-2 rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground">
            <SparklesIcon className="size-3.5" />
            Stage workbench is ready for SQL, ER, reports, and dashboards
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
```

- [x] **Step 3: Create `stage-placeholder-tab.tsx`**

Use a shared placeholder shell for non-SQL tabs so they inherit the same workbench structure:

```tsx
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

type Props = {
  title: string
  kind: 'er' | 'report' | 'dashboard'
  description: string
}

export function StagePlaceholderTab({ title, kind, description }: Props) {
  return (
    <div data-testid="stage-placeholder-tab" className="flex h-full min-h-0 flex-col gap-4 p-4">
      <Card className="border-border/60 bg-background/95">
        <CardHeader className="gap-3">
          <div className="flex items-center gap-3">
            <Badge variant="secondary">{kind.toUpperCase()}</Badge>
            <CardTitle>{title}</CardTitle>
          </div>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          This workbench slot is intentionally scaffolded now and will host the real tool later.
        </CardContent>
      </Card>
    </div>
  )
}
```

- [x] **Step 4: Rewire `stage-tab-content.tsx`, `split-view.tsx`, and `stage-window.tsx`**

Make the Stage use the new empty-state and placeholder path:

```tsx
// stage-tab-content.tsx
switch (tab.type) {
  case 'query_editor':
    return <QueryEditorTab tab={tab} />
  case 'er_canvas':
    return <StagePlaceholderTab kind="er" title={tab.title} description={t('stage.placeholder.erWorkbench')} />
  case 'report':
    return <StagePlaceholderTab kind="report" title={tab.title} description={t('stage.placeholder.reportWorkbench')} />
  case 'dashboard':
    return <StagePlaceholderTab kind="dashboard" title={tab.title} description={t('stage.placeholder.dashboardWorkbench')} />
  default:
    return <StagePlaceholderTab kind="report" title={tab.title} description={t('stage.placeholder.unknownWorkbench')} />
}

// split-view.tsx
<StageWindow sessionId={sid ?? undefined}>
  <StageWorkbenchEmptyState
    title={t('stage.empty.title')}
    description={t('stage.empty.description')}
    primaryActionLabel={t('stage.toolRow.sql')}
  />
</StageWindow>
```

Also remove the `StageTabStrip` import and file, and stop passing `ArtifactCanvas` / legacy children as the default Stage body.

- [x] **Step 5: Add i18n strings for the empty state and placeholder pages**

Append new message keys in `client/src/i18n/messages.ts`:

```ts
'stage.empty.title': 'SQL 工作台',
'stage.empty.description': '从左侧资源中选择上下文，或直接打开 SQL 编辑器开始工作。',
'stage.placeholder.erWorkbench': 'ER 工作台外壳已就位，后续会接入 ReactFlow 画布。',
'stage.placeholder.reportWorkbench': '报表工作台外壳已就位，后续会接入真实报表能力。',
'stage.placeholder.dashboardWorkbench': 'Dashboard 工作台外壳已就位，后续会接入真实仪表板能力。',
'stage.placeholder.unknownWorkbench': '此工作页类型尚未实现，但已接入统一 workbench 外壳。',
```

- [x] **Step 6: Run the targeted Stage shell tests**

Run:

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx
```

Expected: the new empty-state and placeholder assertions pass; no references to `StageTabStrip` remain in the render path.

- [x] **Step 7: Run the batch type check**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: PASS with zero type errors.

- [x] **Step 8: Commit Batch 1**

```bash
git add client/src/features/session/split-view.tsx \
        client/src/features/stage/components/stage-window.tsx \
        client/src/features/stage/components/stage-window.test.tsx \
        client/src/features/stage/components/stage-tab-content.tsx \
        client/src/features/stage/components/stage-workbench-empty-state.tsx \
        client/src/features/stage/components/stage-placeholder-tab.tsx \
        client/src/features/stage/components/stage-tab-strip.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(client): scaffold stage sql workbench shell"
```

---

## Task 2: Query Editor Payload Normalization and Multi-Panel Layout

**Files:**
- Create: `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- Create: `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts`
- Create: `client/src/features/stage/components/query-editor-toolbar.tsx`
- Create: `client/src/features/stage/components/query-editor-result-panel.tsx`
- Create: `client/src/features/stage/components/query-editor-inspector.tsx`
- Modify: `client/src/features/stage/components/query-editor-tab.tsx`
- Modify: `client/src/features/stage/components/query-editor-tab.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 1: Write failing tests for payload normalization and new `query_editor` modes**

Add tests covering:

```ts
// normalize-query-editor-payload.test.ts
it('maps legacy sql/source payloads into the new normalized model', () => {
  expect(normalizeQueryEditorPayload({ sql: 'SELECT 1', source: 'user' })).toMatchObject({
    entryMode: 'manual',
    initialSql: 'SELECT 1',
    source: 'user',
  })
})

it('preserves direct_sql metadata and initial result payloads', () => {
  expect(normalizeQueryEditorPayload({
    entryMode: 'direct_sql',
    initialSql: 'select 1',
    initialResult: { columns: ['n'], rows: [[1]], rowCount: 1, executionMs: 3, truncated: false },
  })).toMatchObject({
    entryMode: 'direct_sql',
    initialSql: 'select 1',
    initialResult: { rowCount: 1 },
  })
})
```

Extend `query-editor-tab.test.tsx` with failing assertions for:

- direct query mode badge
- embedded inspector panel
- no-connection empty state
- `autoRun` triggering `execute`
- success state rendering in the embedded result panel

- [x] **Step 2: Implement `normalize-query-editor-payload.ts`**

Create a single normalization source of truth used by the UI and adapter:

```ts
export type NormalizedQueryEditorPayload = {
  entryMode: 'manual' | 'resource' | 'direct_sql' | 'ai_generated'
  initialSql: string
  source: 'user' | 'ai'
  autoRun: boolean
  initialResult: {
    columns: string[]
    rows: unknown[][]
    rowCount: number
    executionMs: number
    truncated: boolean
  } | null
  lastRun: {
    columns: string[]
    rowCount: number
    executionMs: number
    truncated: boolean
  } | null
  contextNotice: string | null
}

export function normalizeQueryEditorPayload(payload: unknown): NormalizedQueryEditorPayload {
  const value = (payload ?? {}) as Record<string, unknown>
  const source = value.source === 'ai' ? 'ai' : 'user'
  const entryMode = (
    value.entryMode === 'resource' ||
    value.entryMode === 'direct_sql' ||
    value.entryMode === 'ai_generated'
  ) ? value.entryMode : (source === 'ai' ? 'ai_generated' : 'manual')

  return {
    entryMode,
    initialSql: String(value.initialSql ?? value.sql ?? ''),
    source,
    autoRun: value.autoRun === true,
    initialResult: (value.initialResult as NormalizedQueryEditorPayload['initialResult']) ?? null,
    lastRun: (value.lastRun as NormalizedQueryEditorPayload['lastRun']) ?? null,
    contextNotice: typeof value.contextNotice === 'string' ? value.contextNotice : null,
  }
}
```

- [x] **Step 3: Create the toolbar / result / inspector subcomponents**

Keep generic controls on `shadcn/ui`:

```tsx
// query-editor-toolbar.tsx
<div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 px-4 py-3">
  <div className="flex flex-wrap items-center gap-2">
    <Badge variant="secondary">{entryLabel}</Badge>
    <span className="text-xs text-muted-foreground">{connectionLabel}</span>
    {contextNotice && <Badge variant="outline">{contextNotice}</Badge>}
  </div>
  <Button size="sm" onClick={onRun}>
    <PlayIcon className="size-3.5" />
    {runLabel}
  </Button>
</div>

// query-editor-result-panel.tsx
<Card className="h-full rounded-none border-0 border-t border-border/60">
  <CardHeader className="flex-row items-center justify-between py-3">
    <CardTitle className="text-sm">Results</CardTitle>
    <div className="text-xs text-muted-foreground">{summary}</div>
  </CardHeader>
  <CardContent className="min-h-0 flex-1 overflow-hidden p-0">{body}</CardContent>
</Card>

// query-editor-inspector.tsx
<Card className="h-full border-border/60 bg-muted/10">
  <CardHeader className="gap-2">
    <CardTitle className="text-sm">Inspector</CardTitle>
    <CardDescription>{modeDescription}</CardDescription>
  </CardHeader>
  <CardContent className="space-y-3 text-xs">
    <div><span className="text-muted-foreground">Connection</span><div>{connectionLabel}</div></div>
    <div><span className="text-muted-foreground">Database</span><div>{databaseLabel}</div></div>
    <div><span className="text-muted-foreground">Last Run</span><div>{lastRunLabel}</div></div>
  </CardContent>
</Card>
```

- [x] **Step 4: Refactor `query-editor-tab.tsx` into the multi-panel workbench**

Replace the current single-column layout with a grid that uses the new subcomponents and normalized payload:

```tsx
const payload = normalizeQueryEditorPayload(tab.payload)
const [hasAutoRunFired, setHasAutoRunFired] = useState(false)

useEffect(() => {
  if (!payload.autoRun || hasAutoRunFired || !effectiveContext.connectionId) return
  setHasAutoRunFired(true)
  void execute(payload.initialSql, effectiveContext.connectionId, payload.source, {
    sessionId: effectiveContext.sessionId ?? undefined,
    database: effectiveContext.database,
    schema: effectiveContext.schema,
  })
}, [payload.autoRun, payload.initialSql, payload.source, effectiveContext, hasAutoRunFired, execute])

return (
  <div className="grid h-full min-h-0 gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_320px]">
    <Card className="min-h-0 overflow-hidden border-border/60">
      <QueryEditorToolbar ... />
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="min-h-[320px] flex-1 overflow-hidden">
          <SqlEditor initialValue={payload.initialSql} ... />
        </div>
        <div className="min-h-[260px] border-t border-border/60">
          <QueryEditorResultPanel ... />
        </div>
      </div>
    </Card>
    <QueryEditorInspector ... />
  </div>
)
```

When `effectiveContext.connectionId` is missing, render an empty-state card inside the editor pane instead of a broken run button.

- [x] **Step 5: Mirror SQL draft and latest run metadata back into the tab payload**

Use `useStageStore.getState().updateTabPayload(tab.tabId, updater)` so the tab becomes the source of truth for the adapter:

```ts
function updatePayload(patch: Partial<NormalizedQueryEditorPayload>) {
  useStageStore.getState().updateTabPayload(tab.tabId, (prev) => ({
    ...normalizeQueryEditorPayload(prev),
    ...patch,
  }))
}

// on editor change
updatePayload({ initialSql: nextSql })

// on success
updatePayload({
  autoRun: false,
  lastRun: {
    columns: result.columns,
    rowCount: result.rowCount,
    executionMs: result.executionMs,
    truncated: result.truncated,
  },
  initialResult: {
    columns: result.columns,
    rows: result.rows,
    rowCount: result.rowCount,
    executionMs: result.executionMs,
    truncated: result.truncated,
  },
  contextNotice: result.contextNotice ?? null,
})
```

- [x] **Step 6: Run the targeted Query Editor tests**

Run:

```bash
cd client && npx vitest run src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts src/features/stage/components/query-editor-tab.test.tsx
```

Expected: PASS with coverage for manual / ai / direct_sql modes and the embedded result / inspector layout.

- [x] **Step 7: Run the batch type check**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: PASS with zero type errors.

- [x] **Step 8: Commit Batch 2**

```bash
git add client/src/features/stage/utils/normalize-query-editor-payload.ts \
        client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
        client/src/features/stage/components/query-editor-toolbar.tsx \
        client/src/features/stage/components/query-editor-result-panel.tsx \
        client/src/features/stage/components/query-editor-inspector.tsx \
        client/src/features/stage/components/query-editor-tab.tsx \
        client/src/features/stage/components/query-editor-tab.test.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(client): redesign query editor as sql workbench"
```

---

## Task 3: Register `workspace` and `query_editor` as Real UI Objects

**Files:**
- Create: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- Create: `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`
- Create: `client/src/features/stage/components/stage-ui-object-registry.tsx`
- Create: `client/src/features/stage/components/stage-ui-object-registry.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`

- [x] **Step 1: Write failing tests for `QueryEditorAdapter` and runtime registration**

Add adapter tests that currently fail because the file does not exist:

```ts
it('reads sql + context + lastRun from the tab payload', () => {
  useStageStore.getState().openTab({
    tabId: 'q1',
    type: 'query_editor',
    title: 'SQL',
    scope: 'session',
    originSessionId: 's1',
    connectionId: 'conn-1',
    database: 'db-1',
    schema: 'public',
    payload: {
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      lastRun: { columns: ['n'], rowCount: 1, executionMs: 5, truncated: false },
    },
    createdAt: 0,
  })
  const adapter = new QueryEditorAdapter('q1')
  expect(adapter.read('state')).toMatchObject({
    sql: 'select 1',
    connectionId: 'conn-1',
    database: 'db-1',
    schema: 'public',
    entryMode: 'direct_sql',
  })
})
```

Add a registry integration test:

```tsx
it('registers workspace and open query_editor tabs into uiRouter', async () => {
  render(<StageUIObjectRegistry sessionId="s1" tabs={[tab]} />)
  const out = await uiRouter.handle({
    tool: 'ui_list',
    object: '',
    target: '',
    payload: { filter: { type: 'query_editor' } },
  })
  expect(out.data).toEqual(expect.arrayContaining([expect.objectContaining({ objectId: 'q1' })]))
})
```

- [x] **Step 2: Implement `QueryEditorAdapter.ts`**

The adapter should read directly from `useStageStore` so it stays in sync with payload updates:

```ts
export class QueryEditorAdapter implements UIObject {
  type = 'query_editor'

  constructor(public objectId: string) {}

  private getTab() {
    const state = useStageStore.getState()
    return state.workspaceTabs.find((tab) => tab.tabId === this.objectId)
      ?? Array.from(state.tabsBySession.values()).flat().find((tab) => tab.tabId === this.objectId)
      ?? null
  }

  get title() { return this.getTab()?.title ?? 'Query Editor' }
  get connectionId() { return this.getTab()?.connectionId }
  get database() { return this.getTab()?.database }

  read(mode: 'state' | 'schema' | 'actions' | 'full') {
    const tab = this.getTab()
    if (!tab) return {}
    const payload = normalizeQueryEditorPayload(tab.payload)
    const state = {
      sql: payload.initialSql,
      entryMode: payload.entryMode,
      connectionId: tab.connectionId ?? null,
      connectionName: tab.connectionName ?? null,
      database: tab.database ?? null,
      schema: tab.schema ?? null,
      lastRun: payload.lastRun,
      contextNotice: payload.contextNotice,
    }
    if (mode === 'state') return state
    if (mode === 'actions') return [
      { name: 'focus', description: 'Focus this query editor', paramsSchema: { type: 'object', properties: {} } },
      { name: 'close', description: 'Close this query editor', paramsSchema: { type: 'object', properties: {} } },
    ]
    if (mode === 'schema') return { type: 'object', properties: { sql: { type: 'string' }, entryMode: { type: 'string' } } }
    return { state, schema: this.read('schema'), actions: this.read('actions') }
  }

  patch() { return Promise.resolve({ status: 'error', message: 'query_editor is read-only; edit through the UI' }) }

  async exec(action: string) {
    if (action === 'focus') { useStageStore.getState().focusTab(this.objectId); return { success: true } }
    if (action === 'close') { useStageStore.getState().closeTab(this.objectId); return { success: true } }
    return { success: false, error: `Unknown action: ${action}` }
  }
}
```

- [x] **Step 3: Implement `stage-ui-object-registry.tsx` and wire it into `stage-window.tsx`**

Register `workspace` plus every open `query_editor` tab:

```tsx
function RegisteredInstance({ instance }: { instance: UIObject | null }) {
  useUIObjectRegistry(instance)
  return null
}

function RegisteredQueryEditor({ tabId }: { tabId: string }) {
  const instance = useMemo(() => new QueryEditorAdapter(tabId), [tabId])
  useUIObjectRegistry(instance)
  return null
}

export function StageUIObjectRegistry({ sessionId, tabs }: { sessionId: string | null; tabs: StageTab[] }) {
  const workspace = useMemo(() => new WorkspaceAdapter(() => sessionId), [sessionId])
  return (
    <>
      <RegisteredInstance instance={workspace} />
      {tabs.filter((tab) => tab.type === 'query_editor').map((tab) => (
        <RegisteredQueryEditor key={tab.tabId} tabId={tab.tabId} />
      ))}
    </>
  )
}
```

In `stage-window.tsx` render:

```tsx
<StageUIObjectRegistry sessionId={sessionId ?? null} tabs={tabs} />
```

- [x] **Step 4: Update `WorkspaceAdapter.ts` and its tests to remove `bang_query` assumptions**

Replace:

```ts
const WORKSPACE_SCOPE_TYPES = new Set<string>(['bang_query', 'er_canvas', 'markdown_note'])
```

with:

```ts
const WORKSPACE_SCOPE_TYPES = new Set<string>(['er_canvas', 'markdown_note', 'report', 'dashboard'])
```

Then update tests that opened `bang_query` tabs to use `er_canvas` or another workspace-scoped placeholder type instead:

```ts
await adapter.exec('open', { type: 'er_canvas', title: 'ER' })
```

- [x] **Step 5: Run the adapter + registry test slice**

Run:

```bash
cd client && npx vitest run src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts src/features/stage/components/stage-ui-object-registry.test.tsx src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts
```

Expected: PASS; `uiRouter` can list `workspace` and open `query_editor` objects.

- [x] **Step 6: Run the batch type check**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: PASS with zero type errors.

- [x] **Step 7: Commit Batch 3**

```bash
git add client/src/features/stage/adapters/QueryEditorAdapter.ts \
        client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
        client/src/features/stage/components/stage-ui-object-registry.tsx \
        client/src/features/stage/components/stage-ui-object-registry.test.tsx \
        client/src/features/stage/components/stage-window.tsx \
        client/src/features/stage/adapters/WorkspaceAdapter.ts \
        client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts
git commit -m "feat(client): register query editor ui objects"
```

---

## Task 4: Migrate Direct SQL to `query_editor` and Remove the `bang_query` Page

**Files:**
- Create: `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts`
- Create: `client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts`
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/stores/stage-store.test.ts`
- Delete: `client/src/features/stage/components/bang-query-tab.tsx`
- Delete: `client/src/features/stage/components/bang-query-tab.test.tsx`
- Delete: `client/src/features/stage/adapters/BangQueryAdapter.ts`
- Delete: `client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts`
- Delete: `client/src/features/stage/utils/open-bang-query-tab.ts`
- Delete: `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts`

**Implementation note:** Execution also removed the temporary `bang_query` registry/test compatibility added in Batch 3 (`stage-ui-object-registry*`, `WorkspaceAdapter*`, `UIRouter.test.ts`) and renamed the internal chooser reason from `bang_query` to `direct_sql` so the final protocol sanity search only leaves intentional `bang_query_user` message semantics.

- [x] **Step 1: Write failing tests for the new direct-SQL helper**

Add a helper test that captures the new behavior:

```ts
it('opens a new session-scoped query_editor with direct_sql payload and initial result', async () => {
  executeQueryMock.mockResolvedValue({
    columns: ['n'],
    rows: [[1]],
    rowCount: 1,
    durationMs: 4,
    resolvedContext: { connectionId: 'conn-1', connectionName: 'Main', database: 'db-1', schema: 'public' },
    contextNotice: null,
  })

  const tabId = await openDirectSqlQueryEditorTab({
    sessionId: 's1',
    connectionId: 'conn-1',
    sql: 'select 1',
  })

  const tab = useStageStore.getState().tabsBySession.get('s1')?.find((item) => item.tabId === tabId)
  expect(tab).toMatchObject({
    type: 'query_editor',
    connectionId: 'conn-1',
    database: 'db-1',
    schema: 'public',
    payload: expect.objectContaining({
      entryMode: 'direct_sql',
      initialSql: 'select 1',
      initialResult: expect.objectContaining({ rowCount: 1 }),
    }),
  })
})
```

- [x] **Step 2: Implement `open-direct-sql-query-editor-tab.ts`**

Keep the message-persistence API unchanged, but replace the tab opening target:

```ts
export async function openDirectSqlQueryEditorTab({ sessionId, connectionId, sql }: Args): Promise<string> {
  if (!connectionId) throw new Error('No active connection — please select a data source')

  const sessionContext = sessionId ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null : null
  const result = await executeQuery({
    connectionId,
    sql,
    sessionId,
    database: sessionContext?.database,
    schema: sessionContext?.schema,
  })

  const resolvedContext = result.resolvedContext ?? null
  const tabId = `query_editor_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  useStageStore.getState().openTab({
    tabId,
    type: 'query_editor',
    title: 'SQL 编辑器',
    scope: 'session',
    originSessionId: sessionId ?? undefined,
    connectionId: resolvedContext?.connectionId ?? connectionId,
    connectionName: resolvedContext?.connectionName ?? undefined,
    database: resolvedContext?.database ?? undefined,
    schema: resolvedContext?.schema ?? undefined,
    payload: {
      entryMode: 'direct_sql',
      initialSql: sql,
      source: 'user',
      autoRun: false,
      contextNotice: result.contextNotice ?? null,
      lastRun: {
        columns: result.columns,
        rowCount: result.rowCount,
        executionMs: result.durationMs,
        truncated: result.rows.length < result.rowCount,
      },
      initialResult: {
        columns: result.columns,
        rows: result.rows,
        rowCount: result.rowCount,
        executionMs: result.durationMs,
        truncated: result.rows.length < result.rowCount,
      },
    },
    createdAt: Date.now(),
  })
  if (sessionId) useStageStore.getState().openStage(sessionId)
  return tabId
}
```

- [x] **Step 3: Update `prompt-composer.tsx` and its tests**

Keep `createBangQueryMessage(...)` exactly as-is, but swap the opened tab helper:

```ts
import { openDirectSqlQueryEditorTab } from '@/features/stage/utils/open-direct-sql-query-editor-tab'

await openDirectSqlQueryEditorTab({
  sessionId: bangSessionId,
  connectionId,
  sql,
})
```

Change the tests to assert the new helper name and `query_editor` result path:

```ts
const openDirectSqlQueryEditorTabMock = directSqlTabApi.openDirectSqlQueryEditorTab as unknown as Mock
openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-1')

expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
  sessionId: 'sess-1',
  connectionId: 'conn-1',
  sql: 'select 1',
})
```

Do **not** rename `createBangQueryMessage` or the `bang_query_user` chat metadata in this task.

- [x] **Step 4: Remove the `bang_query` page and dead references**

Perform the deletes listed above and remove the `bang_query` switch branch from `stage-tab-content.tsx`.

Also replace any remaining test fixtures that create `type: 'bang_query'` tabs with `query_editor` or `er_canvas`, depending on the behavior being tested.

- [x] **Step 5: Run the direct-query + cleanup test slice**

Run:

```bash
cd client && npx vitest run src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts src/features/session/__tests__/prompt-composer.test.tsx src/features/stage/components/query-editor-tab.test.tsx src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts
```

Expected: PASS; direct SQL still persists chat messages, but now opens a `query_editor`.

- [x] **Step 6: Run the batch type check**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: PASS with zero type errors.

- [x] **Step 7: Commit Batch 4**

```bash
git add client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts \
        client/src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts \
        client/src/features/session/prompt-composer.tsx \
        client/src/features/session/__tests__/prompt-composer.test.tsx \
        client/src/features/stage/components/stage-tab-content.tsx \
        client/src/stores/stage-store.test.ts
git rm client/src/features/stage/components/bang-query-tab.tsx \
       client/src/features/stage/components/bang-query-tab.test.tsx \
       client/src/features/stage/adapters/BangQueryAdapter.ts \
       client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts \
       client/src/features/stage/utils/open-bang-query-tab.ts \
       client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts
git commit -m "refactor(client): replace bang query page with query editor"
```

---

## Task 5: Sync Protocol Docs and Resource AGENTS

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `docs/references/ui-objects-reference.md`
- Modify: `docs/FRONTEND.md`

- [x] **Step 1: Update `server/.../resources/agents/AGENTS.md` to describe `query_editor` instead of `bang_query`**

Replace the current object table and workflow text with the new contract:

```md
| type | objectId | Description |
|------|----------|-------------|
| `workspace` | `workspace` | The tab container; reports open tabs and the active tab |
| `query_editor` | `<tabId>` | A SQL workbench tab with SQL text, execution metadata, and focus/close actions |

**`query_editor` state fields**: `sql`, `entryMode`, `connectionId`, `connectionName`, `database`, `schema`, `lastRun`, `contextNotice`.
```

Update the `datatalk.ui.exec` table so `workspace.open` valid values no longer mention `bang_query`, and change the “Inspect the workspace” workflow to `ui_read(query_editor, ...)`.

- [x] **Step 2: Update `docs/references/ui-objects-reference.md`**

Make the protocol doc match the implementation:

```md
### 2. `query_editor`

**源文件**：`client/src/features/stage/adapters/QueryEditorAdapter.ts`
**objectId**：`tabId`
**说明**：统一 SQL 工作页，承接手动 SQL、资源树 SQL、AI 预填 SQL 与 direct SQL。

#### `read` 输出
- `state`: `{ sql, entryMode, connectionId, connectionName, database, schema, lastRun, contextNotice }`

#### Exec Actions
- `focus`
- `close`
```

Also fix the maintenance note at the top to say the runtime `AGENTS.md` is loaded from `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` by `OpenCodeGatewayBeans.writeAgentsMd()`, rather than a nonexistent inline constant.

- [x] **Step 3: Update `docs/FRONTEND.md`**

Append Stage workbench conventions that become canonical after this refactor:

```md
- Stage 的通用控件必须使用 `shadcn/ui`；例外仅限 SQL 编辑器（CodeMirror）与未来 ER 画布（ReactFlow）。
- `query_editor` 是 Stage 内唯一 SQL 工作页；顶部工具行、资源树 SQL 动作和 `!select/!with` 都打开它。
- `bang_query_user` 仍是聊天消息语义，但不再对应独立 Stage tab 类型。
```

- [x] **Step 4: Verify the docs only mention `bang_query` where it is still intentionally valid**

Run:

```bash
rg -n "bang_query" client/src docs/references/ui-objects-reference.md server/data-talk-adapter/src/main/resources/agents/AGENTS.md docs/FRONTEND.md
```

Expected:

- no matches in `docs/references/ui-objects-reference.md`
- no matches in `server/.../agents/AGENTS.md`
- no new Stage-page references in frontend docs
- allowed remaining matches only in chat message semantics or historical docs outside this edit set

- [x] **Step 5: Commit Batch 5**

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md \
        docs/references/ui-objects-reference.md \
        docs/FRONTEND.md
git commit -m "docs: align stage protocol with query editor workbench"
```

---

## Task 6: Final Verification and Plan Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-21-stage-window-sql-workbench-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Run the full targeted verification slice**

Run:

```bash
cd client && npx vitest run \
  src/features/stage/components/stage-window.test.tsx \
  src/features/stage/components/query-editor-tab.test.tsx \
  src/features/stage/components/stage-ui-object-registry.test.tsx \
  src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts \
  src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
  src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
  src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts \
  src/features/session/__tests__/prompt-composer.test.tsx
cd client && npx tsc --noEmit
```

Expected:

- all listed vitest files PASS
- `npx tsc --noEmit` PASS

- [x] **Step 2: Run the final protocol/documentation sanity search**

Run:

```bash
rg -n "bang_query" client/src docs/references/ui-objects-reference.md server/data-talk-adapter/src/main/resources/agents/AGENTS.md
```

Expected: any remaining hits are limited to intentionally retained chat-message semantics such as `bang_query_user`; there are no remaining `bang_query` page / adapter / UIObject references.

- [x] **Step 3: Mark this plan file complete**

Edit this plan file in place so every checkbox is checked and add short notes if any implementation detail intentionally differed from the plan.

- [x] **Step 4: Move the plan index entry from Active to Completed**

Update `docs/exec-plans/index.md`:

```md
| [Stage Window SQL Workbench](./2026-04-21-stage-window-sql-workbench-plan.md) | 2026-04-21 | Stage 升级为基于 shadcn/ui 的多面板 SQL 工作台，`query_editor` 成为唯一 SQL 工作页，`bang_query` 页面/adapter/helper 退场，`resources/agents/AGENTS.md` 与 UI Object 协议文档同步完成；相关 vitest 与 `npx tsc --noEmit` 通过。 |
```

- [x] **Step 5: Commit the verified implementation + housekeeping**

```bash
git add docs/exec-plans/2026-04-21-stage-window-sql-workbench-plan.md \
        docs/exec-plans/index.md
git commit -m "chore: finalize stage sql workbench rollout"
```

---

## Self-Review Checklist

- Spec coverage: every design section maps to at least one task above
- Placeholder scan: no `TODO` / `TBD` / “later” implementation gaps remain in executable tasks
- Type consistency: `entryMode`, `initialSql`, `initialResult`, `lastRun`, `QueryEditorAdapter`, and `openDirectSqlQueryEditorTab` use the same names throughout
- Scope guard: `createBangQueryMessage` / `bang_query_user` chat semantics are intentionally preserved; only the Stage page / tab / protocol object is removed

**Completion note:** Completed on 2026-04-21. Final verification passed with `8/8` targeted vitest files green (`72` tests), a direct-query cleanup slice green (`78` tests), `client` `npx tsc --noEmit` green, and the final `bang_query` search limited to intentionally retained `bang_query_user` chat-message semantics.
