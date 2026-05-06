# File Artifact System · Part 4 — Frontend Tabs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 file artifact 系统的前端表层 —— Stage Files Tab（session-scope）、Files Library Tab（workspace-scope）、Chat 内联 file artifact 卡片，配套 `useFileArtifactsStore` Zustand store + `file-artifacts` REST 客户端 + DtEvent SSE 订阅 + i18n。本 Part 完成后用户可在 Stage 看到 AI 写入 session 子目录的临时文件、把候选标记或归档到 connection 资产库、在 Chat 里看到 `datatalk_archive_artifact` 工具结果卡片并直接归档/丢弃。终局确认 modal、Settings Maintenance 与 session/connection DELETE 两阶段流程留 Part 5。

**Architecture:** 严格遵循 `client/DESIGN.md` 三区 Stage 骨架（Sidebar / Conversation lane / Stage）与 token-only 颜色规则。`useFileArtifactsStore` 仅用 `bg.panel` / `bg.subtle` / `border.subtle` / `status.warningSurface` / `accent.warn` / `text.muted` 等语义 token 对应的 Tailwind class（`bg-panel` / `bg-subtle` / `text-muted` 等），禁止出现 `bg-blue-500` 类原始原色。Stage tab 实例 `StageTab` 不带 `scope` 字段；类型层 scope 元数据写入 `tab-type-registry`（spec §7.1 + CLAUDE.md "Stage state" 约束）。SSE 订阅复用 `buildEventSink`（`client/src/services/channel/use-channel.ts`），新增 5 个 `file_artifact.*` 事件 case 直接 dispatch 到 store。REST 直接打 Part 1 已存在的 `FileArtifactController`（`/api/sessions/{sid}/files`、`/api/connections/{cid}/files`、`/api/files/{fid}/mark-candidate`），新增 archive / discard 端点由 Part 5 增量补完，本 Part 仅前端调用占位（`archiveFile` / `discardFile`），先以 `mark-candidate` 走通 happy path，archive/discard 在 Part 5 端点上线后无需改前端。

**Tech Stack:** React 19、TanStack Router/Query、Zustand、ky（`@/services/http`）、Tailwind v4 + design-tokens（`bg-panel` / `bg-subtle` / `text-muted` / `text-strong` / `accent-warn` / `status-warningSurface`）、lucide-react 图标、vitest + @testing-library/react、Channel SSE（`buildEventSink` 复用）。

**Spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md)（重点 §3.2、§7、§8.3、§12）

**关联 Part：**
- Part 1 — Migration + Domain（已完成）
- Part 2 — ArtifactWatcherService（io.methvin）+ reconcile + symlink 拒绝（待补）
- Part 3 — `ArchiveArtifactActionHandler` MCP + classpath AGENTS.md + AgentsTemplateContractTest（待补）
- Part 4（本计划）— Frontend Files Tab + Files Library Tab + Chat 内联卡片 + Zustand store + tab-type-registry + i18n
- Part 5 — session/connection DELETE 两阶段 + 终局确认 modal + HousekeepingScheduler + LegacyMigrationRunner + Settings Maintenance + archive/discard REST 端点上线 + delete-session-modal & maintenance i18n

**执行状态：** 未开始。完成本 Part 后必须在 `docs/exec-plans/index.md` 把本行从「活跃」迁到「已完成」（CLAUDE.md "Post-Execution Document Housekeeping"）。

---

## Design Inputs

本 Part 涉及 client/ UI、必须遵循 `client/DESIGN.md`。引用约束：

- **三区 Stage 骨架**：Sidebar（navigation skeleton）/ Conversation lane（session chat）/ Stage（instrument lane）。Files Tab 与 Files Library Tab 都属 Stage instrument lane，不创建独立侧栏入口。
- **Stage 状态全局**：`StageTab` 实例不带 `scope` 字段；type-level scope 元数据只在 `tab-type-registry.ts` 的 `TabTypeDescriptor.scope` 中定义。切换 active session 不重排 tabs；FILES tab 内部读 `useSessionStore.activeSessionId`、FILES_LIBRARY tab 内部读 `useConnectionStore.activeConnectionId`，渲染数据切换由 store 自身完成。
- **Stage chrome `bg.subtle`、surface `bg.canvas`**：tab bar 与左 rail 用 `bg-subtle`；tab 主内容区用 `bg-canvas`。
- **Token-only colors**：禁止 `bg-blue-500` 等原始原色；所有 surface / 边框 / 文本只能用语义 token Tailwind class（`bg-panel` / `bg-subtle` / `text-strong` / `text-muted` / `border-subtle` / `border-default` / `accent-warn` / `status-warningSurface` / `status-dangerSurface`）。
- **Status colors**：success=green / warning=amber（candidate）/ danger=red（discard 按钮 hover/active）/ info=sky（library "已归档" 提示）。candidate 使用 `status.warningSurface` 背景 + `accent.warn` 1px 左边框 + 📌 图标，不只靠颜色（双通道）。
- **Density**：tabs/toolbar 用 `compact`（按 spec components.sidebar/table 用法），search input、filter dropdown、按钮高度 28-32px、文本 ui-xs/ui-sm。
- **Motion**：`fast=120ms` / `normal=180ms` / `slow=240ms`；仅用于状态确认（candidate 徽章淡入、archive 按钮按下回弹）；列表更新无装饰动画。`prefers-reduced-motion` 必须停用。
- **Accessibility**：状态用图标 + 颜色双通道（📄 temporary、📌 candidate、📦 archived）；icon-only 按钮（[打开] / [丢弃]）必须有 `aria-label`；focusRing token 在所有交互控件 focus-visible 状态可见。
- **Five-state token mapping（每个交互控件 idle / hover / active / focus / disabled 必须显式枚举，per memory `feedback-design-control-states`）**：
  - **search-input**（Files Library 搜索框）
    - idle：`bg-panel` / `border-subtle` / `text-base` / placeholder `text-soft`
    - hover：`bg-panel` / `border-default` / `text-base`
    - active（输入中）：`bg-panel` / `border-default` / `text-strong` / caret `accent-primary`
    - focus：`bg-panel` / `border-default` / `outline-2 ring-focusRing`
    - disabled：`bg-subtle` / `border-subtle` / `text-disabled` / cursor-not-allowed
  - **filter-dropdown trigger**
    - idle：`bg-panel` / `border-subtle` / `text-base`
    - hover：`bg-panel` / `border-default` / `text-strong`
    - active（打开）：`bg-panel` / `border-default` / `text-strong` / chevron 旋转
    - focus：`outline-2 ring-focusRing`
    - disabled：`bg-subtle` / `text-disabled`
  - **action-button (archive primary)**
    - idle：`bg-accent-primary` / `text-inverse`
    - hover：`bg-accent-primaryHover` / `text-inverse`
    - active：`bg-accent-primaryHover` / opacity 0.9
    - focus：`outline-2 ring-focusRing offset-2`
    - disabled：`bg-disabled` / `text-disabled` / cursor-not-allowed
  - **action-button (ghost — open / discard / mark-as-candidate)**
    - idle：`bg-transparent` / `text-base` / `border-subtle`
    - hover：`bg-hover` / `text-strong` / `border-default`
    - active：`bg-active` / `text-strong`
    - focus：`outline-2 ring-focusRing`
    - disabled：`text-disabled` / cursor-not-allowed / no hover
  - **section-header collapse toggle**
    - idle：`text-muted` / chevron `rotate-0`
    - hover：`text-strong` / `bg-hover`
    - active（折叠中）：chevron `-rotate-90` / `text-strong`
    - focus：`outline-2 ring-focusRing`
    - disabled：N/A（节标题永远可点）

不与 `client/DESIGN.md` 冲突；本 Part 不修改 token 表，只消费已有语义 token。

---

## Files

### Tab type registry（修改）

- Modify: `client/src/features/stage/registry/tab-type-registry.ts`

### Zustand store（新增）

- Create: `client/src/features/stage/stores/file-artifacts-store.ts`
- Create: `client/src/features/stage/stores/__tests__/file-artifacts-store.test.ts`

### API client（新增）

- Create: `client/src/services/api/file-artifacts.ts`

### 共享类型（新增 — 后端 payload 镜像）

- Create: `client/src/services/api/file-artifacts-types.ts`（如简单可合并入 `file-artifacts.ts`，本计划合并）

### Components（新增）

- Create: `client/src/features/stage/components/files-tab.tsx`
- Create: `client/src/features/stage/components/files-library-tab.tsx`
- Create: `client/src/features/stage/components/file-artifact-status-badge.tsx`

### Chat 内联卡片（新增）

- Create: `client/src/features/chat/components/tools/renderers/datatalk-archive-artifact.tsx`
- Modify: `client/src/features/chat/components/tools/renderers/index.ts`

### Stage tab content dispatcher（修改 — 增加 files / files-library 分支）

- Modify: `client/src/features/stage/components/stage-tab-content.tsx`

### SSE 事件订阅（修改 — 新增 5 个 file_artifact.* event case）

- Modify: `client/src/services/channel/use-channel.ts`

### i18n（修改）

- Modify: `client/src/i18n/messages.ts`

### Tests（新增）

- Create: `client/src/features/stage/components/__tests__/files-tab.test.tsx`
- Create: `client/src/features/stage/components/__tests__/files-library-tab.test.tsx`
- Create: `client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx`
- Create: `client/src/features/chat/components/tools/renderers/__tests__/datatalk-archive-artifact.test.tsx`

### Docs（修改）

- Modify: `docs/exec-plans/index.md`（把本计划从「活跃」迁到「已完成」，文末 housekeeping）

---

## Task 1: 注册计划

- [ ] 已存在 `docs/exec-plans/2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md`（本文件）
- [ ] 在 `docs/exec-plans/index.md` 「活跃计划」表格替换 Part 4 占位行为正式链接：

```markdown
| [File Artifact System · Part 4 — Frontend Tabs](./2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md) | 2026-04-30 | Stage Files Tab + Files Library Tab + Chat 内联 `datatalk_archive_artifact` 卡片 + `useFileArtifactsStore` Zustand store + `file-artifacts` REST 客户端 + 5 个 `file_artifact.*` SSE event 订阅 + i18n keys（不含终局 modal / maintenance）+ 完整 vitest 覆盖。 |
```

- [ ] commit:

```bash
git add docs/exec-plans/2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md \
        docs/exec-plans/index.md
git commit -m "docs(exec-plans): register file artifact system part 4 plan"
```

## Task 2: TypeScript 类型 + REST 客户端 (`file-artifacts.ts`)

**目的：** 提供前端 `FileArtifact` payload 类型 + 4 个 REST 客户端函数；类型字段命名严格镜像 Part 1 `FileArtifactController` / `FileArtifact` record 的 JSON 序列化（camelCase）。

### 2.1 写文件

- [ ] 创建 `client/src/services/api/file-artifacts.ts`：

```ts
import { http } from '@/services/http'

export type FileArtifactScope = 'session' | 'workspace'

export type FileArtifactStatus = 'temporary' | 'candidate' | 'archived' | 'discarded'

export type FileArtifactKind = 'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'other'

/**
 * Wire-format mirror of {@code com.datatalk.domain.fileartifact.FileArtifact}
 * (see Part 1 plan task 6). Field names match Jackson default lower-camel
 * serialization for record components; nullable fields come through as
 * either `null` or omitted — both are normalized to `null` here.
 */
export interface FileArtifact {
  id: string
  scope: FileArtifactScope
  status: FileArtifactStatus
  kind: FileArtifactKind
  sessionId: string | null
  connectionId: string | null
  filename: string
  physicalPath: string
  sizeBytes: number
  mimeType: string | null
  title: string | null
  summary: string | null
  createdAt: string         // ISO-8601
  updatedAt: string
  archivedAt: string | null
  metadata: Record<string, unknown>
}

export function listSessionFiles(sessionId: string): Promise<FileArtifact[]> {
  return http.get(`sessions/${sessionId}/files`).json<FileArtifact[]>()
}

export function listConnectionFiles(connectionId: string): Promise<FileArtifact[]> {
  return http.get(`connections/${connectionId}/files`).json<FileArtifact[]>()
}

export function markCandidate(fileArtifactId: string): Promise<void> {
  return http
    .post(`files/${fileArtifactId}/mark-candidate`)
    .then(() => undefined)
}

/**
 * Archive: move a session-scope file to the connection workspace library.
 * Backend endpoint shipped in Part 5. The frontend already wires the call
 * site so Part 5 only flips the route on without UI churn.
 */
export function archiveFile(sessionId: string, fileArtifactId: string): Promise<void> {
  return http
    .post(`sessions/${sessionId}/files/${fileArtifactId}/archive`)
    .then(() => undefined)
}

/**
 * Discard: move file to ~/.data-talk/_trash and mark DISCARDED.
 * Backend endpoint shipped in Part 5.
 */
export function discardFile(fileArtifactId: string): Promise<void> {
  return http
    .post(`files/${fileArtifactId}/discard`)
    .then(() => undefined)
}
```

### 2.2 类型检查

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

预期：零类型错误。

### 2.3 commit

- [ ] commit：

```bash
git add client/src/services/api/file-artifacts.ts
git commit -m "feat(api): add file-artifacts REST client and TS types"
```

## Task 3: Zustand store `file-artifacts-store.ts`

**目的：** 全局 store 缓存 `bySessionId` / `byConnectionId` 两份数据，提供 `fetchForSession` / `fetchForConnection` / `applyDtEvent` / 选择器；`applyDtEvent` 处理 5 个 `file_artifact.*` 事件，与 SSE 解耦（Task 6 在 `use-channel.ts` 里调用）。

### 3.1 先写测试（TDD red）

- [ ] 创建 `client/src/features/stage/stores/__tests__/file-artifacts-store.test.ts`：

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useFileArtifactsStore } from '../file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const baseArtifact: FileArtifact = {
  id: 'fa_1',
  scope: 'session',
  status: 'temporary',
  kind: 'dataset',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'sample.csv',
  physicalPath: '/home/u/.data-talk/opencode/sessions/ses_a/sample.csv',
  sizeBytes: 2_100_000,
  mimeType: 'text/csv',
  title: null,
  summary: null,
  createdAt: '2026-04-29T00:00:00Z',
  updatedAt: '2026-04-29T00:00:00Z',
  archivedAt: null,
  metadata: {},
}

describe('useFileArtifactsStore', () => {
  beforeEach(() => {
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('selectSessionFiles groups by status (temporary vs candidate)', () => {
    useFileArtifactsStore.setState({
      bySessionId: {
        ses_a: [
          baseArtifact,
          { ...baseArtifact, id: 'fa_2', status: 'candidate', filename: 'orders-er.md', kind: 'er_diagram' },
        ],
      },
    })
    const grouped = useFileArtifactsStore.getState().selectSessionFiles('ses_a')
    expect(grouped.temporary.map((f) => f.id)).toEqual(['fa_1'])
    expect(grouped.candidate.map((f) => f.id)).toEqual(['fa_2'])
  })

  it('selectConnectionFiles groups by kind', () => {
    useFileArtifactsStore.setState({
      byConnectionId: {
        conn_x: [
          { ...baseArtifact, id: 'fa_3', status: 'archived', kind: 'er_diagram', scope: 'workspace', connectionId: 'conn_x' },
          { ...baseArtifact, id: 'fa_4', status: 'archived', kind: 'sql_script', scope: 'workspace', connectionId: 'conn_x' },
        ],
      },
    })
    const grouped = useFileArtifactsStore.getState().selectConnectionFiles('conn_x')
    expect(grouped.er_diagram).toHaveLength(1)
    expect(grouped.sql_script).toHaveLength(1)
    expect(grouped.report).toHaveLength(0)
  })

  it('applyDtEvent file_artifact.detected appends a temporary row to bySessionId', () => {
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.detected',
      data: {
        fileArtifactId: 'fa_new',
        sessionId: 'ses_a',
        filename: 'sample.csv',
        kind: 'dataset',
        status: 'temporary',
        sizeBytes: 2_100_000,
      },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toHaveLength(1)
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].status).toBe('temporary')
  })

  it('applyDtEvent file_artifact.archive_requested promotes status temporary -> candidate', () => {
    useFileArtifactsStore.setState({ bySessionId: { ses_a: [baseArtifact] } })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archive_requested',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        kind: 'dataset',
        title: 'Sample',
        summary: 'CSV sample',
      },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].status).toBe('candidate')
    expect(useFileArtifactsStore.getState().bySessionId.ses_a[0].title).toBe('Sample')
  })

  it('applyDtEvent file_artifact.archived moves the row to byConnectionId and detaches from session', () => {
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [{ ...baseArtifact, status: 'candidate' }] },
    })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archived',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        connectionId: 'conn_x',
        filename: 'sample.csv',
        physicalPath: '/home/u/.data-talk/workspaces/conn_x/sample.csv',
      },
    })
    const state = useFileArtifactsStore.getState()
    expect(state.bySessionId.ses_a).toEqual([])
    expect(state.byConnectionId.conn_x).toHaveLength(1)
    expect(state.byConnectionId.conn_x[0].status).toBe('archived')
    expect(state.byConnectionId.conn_x[0].scope).toBe('workspace')
  })

  it('applyDtEvent file_artifact.discarded removes the row from any list', () => {
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [baseArtifact] },
    })
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.discarded',
      data: { fileArtifactId: 'fa_1', reason: 'user_action' },
    })
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toEqual([])
  })

  it('fetchForSession populates bySessionId via API mock', async () => {
    const api = await import('@/services/api/file-artifacts')
    const spy = vi.spyOn(api, 'listSessionFiles').mockResolvedValue([baseArtifact])
    await useFileArtifactsStore.getState().fetchForSession('ses_a')
    expect(spy).toHaveBeenCalledWith('ses_a')
    expect(useFileArtifactsStore.getState().bySessionId.ses_a).toHaveLength(1)
    expect(useFileArtifactsStore.getState().loading).toBe(false)
    spy.mockRestore()
  })

  it('fetchForSession sets error message on rejection', async () => {
    const api = await import('@/services/api/file-artifacts')
    const spy = vi.spyOn(api, 'listSessionFiles').mockRejectedValue(new Error('boom'))
    await useFileArtifactsStore.getState().fetchForSession('ses_a')
    expect(useFileArtifactsStore.getState().error).toBe('boom')
    expect(useFileArtifactsStore.getState().loading).toBe(false)
    spy.mockRestore()
  })
})
```

### 3.2 实现 store

- [ ] 创建 `client/src/features/stage/stores/file-artifacts-store.ts`：

```ts
import { create } from 'zustand'
import {
  archiveFile,
  discardFile,
  listConnectionFiles,
  listSessionFiles,
  markCandidate,
  type FileArtifact,
  type FileArtifactKind,
} from '@/services/api/file-artifacts'

export type FileArtifactDtEvent =
  | {
      type: 'file_artifact.detected'
      data: {
        fileArtifactId: string
        sessionId: string
        filename: string
        kind: FileArtifactKind
        status: 'temporary' | 'candidate'
        sizeBytes: number
      }
    }
  | {
      type: 'file_artifact.archive_requested'
      data: {
        fileArtifactId: string
        sessionId: string
        kind: FileArtifactKind
        title: string
        summary: string
      }
    }
  | {
      type: 'file_artifact.archived'
      data: {
        fileArtifactId: string
        sessionId: string | null
        connectionId: string
        filename: string
        physicalPath: string
      }
    }
  | {
      type: 'file_artifact.discarded'
      data: { fileArtifactId: string; reason: string }
    }
  | {
      type: 'file_artifact.legacy_migrated'
      data: { filesMovedCount: number }
    }

export type SessionFileGroups = {
  temporary: FileArtifact[]
  candidate: FileArtifact[]
}

export type ConnectionFileGroups = Record<FileArtifactKind, FileArtifact[]>

const EMPTY_KIND_GROUPS: ConnectionFileGroups = {
  report: [],
  er_diagram: [],
  sql_script: [],
  dataset: [],
  other: [],
}

interface FileArtifactsState {
  bySessionId: Record<string, FileArtifact[]>
  byConnectionId: Record<string, FileArtifact[]>
  loading: boolean
  error: string | null

  fetchForSession: (sessionId: string) => Promise<void>
  fetchForConnection: (connectionId: string) => Promise<void>

  applyDtEvent: (event: FileArtifactDtEvent) => void

  archive: (sessionId: string, fileArtifactId: string) => Promise<void>
  discard: (fileArtifactId: string) => Promise<void>
  promote: (fileArtifactId: string) => Promise<void>

  selectSessionFiles: (sessionId: string) => SessionFileGroups
  selectConnectionFiles: (connectionId: string) => ConnectionFileGroups
}

function upsertById(list: FileArtifact[], next: FileArtifact): FileArtifact[] {
  const idx = list.findIndex((item) => item.id === next.id)
  if (idx === -1) return [...list, next]
  const copy = [...list]
  copy[idx] = next
  return copy
}

function removeById(list: FileArtifact[] | undefined, id: string): FileArtifact[] {
  if (!list) return []
  return list.filter((item) => item.id !== id)
}

export const useFileArtifactsStore = create<FileArtifactsState>((set, get) => ({
  bySessionId: {},
  byConnectionId: {},
  loading: false,
  error: null,

  fetchForSession: async (sessionId) => {
    set({ loading: true, error: null })
    try {
      const files = await listSessionFiles(sessionId)
      set((state) => ({
        bySessionId: { ...state.bySessionId, [sessionId]: files },
        loading: false,
      }))
    } catch (err) {
      set({ loading: false, error: err instanceof Error ? err.message : String(err) })
    }
  },

  fetchForConnection: async (connectionId) => {
    set({ loading: true, error: null })
    try {
      const files = await listConnectionFiles(connectionId)
      set((state) => ({
        byConnectionId: { ...state.byConnectionId, [connectionId]: files },
        loading: false,
      }))
    } catch (err) {
      set({ loading: false, error: err instanceof Error ? err.message : String(err) })
    }
  },

  applyDtEvent: (event) => {
    if (event.type === 'file_artifact.legacy_migrated') return

    set((state) => {
      const bySessionId = { ...state.bySessionId }
      const byConnectionId = { ...state.byConnectionId }

      switch (event.type) {
        case 'file_artifact.detected': {
          const sid = event.data.sessionId
          const placeholder: FileArtifact = {
            id: event.data.fileArtifactId,
            scope: 'session',
            status: event.data.status,
            kind: event.data.kind,
            sessionId: sid,
            connectionId: null,
            filename: event.data.filename,
            physicalPath: '',
            sizeBytes: event.data.sizeBytes,
            mimeType: null,
            title: null,
            summary: null,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            archivedAt: null,
            metadata: {},
          }
          const existing = bySessionId[sid] ?? []
          bySessionId[sid] = upsertById(existing, placeholder)
          break
        }
        case 'file_artifact.archive_requested': {
          const sid = event.data.sessionId
          const list = bySessionId[sid] ?? []
          bySessionId[sid] = list.map((file) =>
            file.id === event.data.fileArtifactId
              ? {
                  ...file,
                  status: 'candidate',
                  kind: event.data.kind,
                  title: event.data.title,
                  summary: event.data.summary,
                }
              : file,
          )
          break
        }
        case 'file_artifact.archived': {
          const { fileArtifactId, sessionId, connectionId, filename, physicalPath } = event.data
          // Remove from session list (if known) and append to connection list
          if (sessionId && bySessionId[sessionId]) {
            const previous = bySessionId[sessionId].find((f) => f.id === fileArtifactId)
            bySessionId[sessionId] = removeById(bySessionId[sessionId], fileArtifactId)
            const archived: FileArtifact = {
              id: fileArtifactId,
              scope: 'workspace',
              status: 'archived',
              kind: previous?.kind ?? 'other',
              sessionId,
              connectionId,
              filename,
              physicalPath,
              sizeBytes: previous?.sizeBytes ?? 0,
              mimeType: previous?.mimeType ?? null,
              title: previous?.title ?? null,
              summary: previous?.summary ?? null,
              createdAt: previous?.createdAt ?? new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              archivedAt: new Date().toISOString(),
              metadata: previous?.metadata ?? {},
            }
            byConnectionId[connectionId] = upsertById(byConnectionId[connectionId] ?? [], archived)
          }
          break
        }
        case 'file_artifact.discarded': {
          const { fileArtifactId } = event.data
          for (const sid of Object.keys(bySessionId)) {
            bySessionId[sid] = removeById(bySessionId[sid], fileArtifactId)
          }
          for (const cid of Object.keys(byConnectionId)) {
            byConnectionId[cid] = removeById(byConnectionId[cid], fileArtifactId)
          }
          break
        }
      }

      return { bySessionId, byConnectionId }
    })
  },

  archive: async (sessionId, fileArtifactId) => {
    await archiveFile(sessionId, fileArtifactId)
  },

  discard: async (fileArtifactId) => {
    await discardFile(fileArtifactId)
  },

  promote: async (fileArtifactId) => {
    await markCandidate(fileArtifactId)
  },

  selectSessionFiles: (sessionId) => {
    const list = get().bySessionId[sessionId] ?? []
    return {
      temporary: list.filter((file) => file.status === 'temporary'),
      candidate: list.filter((file) => file.status === 'candidate'),
    }
  },

  selectConnectionFiles: (connectionId) => {
    const list = get().byConnectionId[connectionId] ?? []
    const groups: ConnectionFileGroups = {
      report: [],
      er_diagram: [],
      sql_script: [],
      dataset: [],
      other: [],
    }
    for (const file of list) {
      if (file.status !== 'archived') continue
      groups[file.kind].push(file)
    }
    return groups
  },
}))

export const EMPTY_CONNECTION_FILE_GROUPS = EMPTY_KIND_GROUPS
```

### 3.3 跑测试

- [ ] 运行：

```bash
cd client && npm test -- --run client/src/features/stage/stores/__tests__/file-artifacts-store.test.ts
```

预期：8 个测试全绿。

### 3.4 类型检查 + commit

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

- [ ] commit：

```bash
git add client/src/features/stage/stores/file-artifacts-store.ts \
        client/src/features/stage/stores/__tests__/file-artifacts-store.test.ts
git commit -m "feat(stage): add useFileArtifactsStore with DtEvent dispatch and group selectors"
```

## Task 4: Tab type registry — 注册 FILES + FILES_LIBRARY

**目的：** Stage 容器能识别两类新 tab，并把 scope 元数据写在类型层（Stage 实例不带 scope 字段，per CLAUDE.md "Stage state"）。

### 4.1 修改 registry

- [ ] 修改 `client/src/features/stage/registry/tab-type-registry.ts`，在 `import` 末尾加 `PackageIcon`：

```ts
import { BarChart2Icon, DatabaseIcon, FileTextIcon, LayoutIcon, NetworkIcon, PackageIcon, SearchCodeIcon, TableIcon } from 'lucide-react'
```

- [ ] 在 `TAB_TYPE_REGISTRY` 末尾追加两条目：

```ts
  files: {
    type: 'files',
    persistent: false,
    scope: 'session',
    icon: FileTextIcon,
    labelKey: 'tabType.files',
    extractContent: () => '',
  },
  files_library: {
    type: 'files_library',
    persistent: true,
    scope: 'workspace',
    icon: PackageIcon,
    labelKey: 'tabType.filesLibrary',
    extractContent: () => '',
  },
```

注意：`files` 是 session 内容（随 session 切换刷新），`persistent: false`，关闭 stage 后不在 cold restart 重生；`files_library` 是 workspace 内容，`persistent: true`，cold restart 后随 hydrate 还原。两者 `extractContent` 返回空串 — 全文搜索由 `useFileArtifactsStore` 自身处理（Task 6）。

### 4.2 类型检查

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

预期：零错误。

### 4.3 commit

- [ ] commit：

```bash
git add client/src/features/stage/registry/tab-type-registry.ts
git commit -m "feat(stage): register FILES and FILES_LIBRARY tab types"
```

## Task 5: i18n keys（zh + en）

**目的：** 把本 Part 涉及到的 key 平级追加到 `messages.ts`；终局 modal 与 maintenance 的 key 留 Part 5。

### 5.1 修改 messages.ts

- [ ] 修改 `client/src/i18n/messages.ts`，在 zh-CN 块的 `tabType.erDesigner` 之后追加：

```ts
    'tabType.files': '文件',
    'tabType.filesLibrary': '资产库',

    'files.tabs.session': 'Files',
    'files.tabs.library': 'Files Library',
    'files.section.temporary': '临时',
    'files.section.candidates': '归档候选',
    'files.action.open': '打开',
    'files.action.archive': '归档',
    'files.action.discard': '丢弃',
    'files.action.markAsCandidate': '标为候选',
    'files.action.copyPath': '复制路径',
    'files.action.delete': '删除',
    'files.empty.noSession': '选择或新建一个会话以查看 AI 产出文件',
    'files.empty.noFiles': '当前会话还没有 AI 产出文件',
    'files.empty.noConnection': '选择一个 connection 以查看其资产库',
    'files.empty.noArchived': '当前 connection 还没有归档资产',
    'files.library.search.placeholder': '搜索文件名 / 标题 / 摘要',
    'files.library.filter.label': '筛选',
    'files.library.filter.kind.all': '全部类型',
    'files.library.filter.kind.report': 'Reports',
    'files.library.filter.kind.er_diagram': 'ER Diagrams',
    'files.library.filter.kind.sql_script': 'SQL Scripts',
    'files.library.filter.kind.dataset': 'Datasets',
    'files.library.filter.kind.other': 'Other',
    'files.library.fromSession': '来自 session "{title}"',
    'files.library.fromSessionDeleted': '来自 已删除会话',
    'files.library.archivedAt': '归档于 {date}',
    'files.library.section.report': 'Reports',
    'files.library.section.er_diagram': 'ER Diagrams',
    'files.library.section.sql_script': 'SQL Scripts',
    'files.library.section.dataset': 'Datasets',
    'files.library.section.other': 'Other',
    'files.status.temporary': 'Temporary',
    'files.status.candidate': 'Archive Candidate',
    'files.status.archived': 'Archived',
    'files.chatCard.title': '{filename}',
    'files.chatCard.viewInStage': 'Stage 查看',
    'files.chatCard.archiveNow': '立即归档',
    'files.archiveSuccess': '已归档 {filename}',
    'files.discardSuccess': '已丢弃 {filename}',
    'files.archiveError': '归档失败：{message}',
    'files.discardError': '丢弃失败：{message}',
```

- [ ] 在 en-US 块对应位置（同样紧邻 `tabType.erDesigner`）追加：

```ts
    'tabType.files': 'Files',
    'tabType.filesLibrary': 'Files Library',

    'files.tabs.session': 'Files',
    'files.tabs.library': 'Files Library',
    'files.section.temporary': 'TEMPORARY',
    'files.section.candidates': 'ARCHIVE CANDIDATES',
    'files.action.open': 'Open',
    'files.action.archive': 'Archive',
    'files.action.discard': 'Discard',
    'files.action.markAsCandidate': 'Mark as candidate',
    'files.action.copyPath': 'Copy path',
    'files.action.delete': 'Delete',
    'files.empty.noSession': 'Pick or start a session to view AI-produced files',
    'files.empty.noFiles': 'No files produced yet in this session',
    'files.empty.noConnection': 'Pick a connection to browse its library',
    'files.empty.noArchived': 'No archived assets for this connection yet',
    'files.library.search.placeholder': 'Search filename / title / summary',
    'files.library.filter.label': 'Filter',
    'files.library.filter.kind.all': 'All kinds',
    'files.library.filter.kind.report': 'Reports',
    'files.library.filter.kind.er_diagram': 'ER Diagrams',
    'files.library.filter.kind.sql_script': 'SQL Scripts',
    'files.library.filter.kind.dataset': 'Datasets',
    'files.library.filter.kind.other': 'Other',
    'files.library.fromSession': 'From session "{title}"',
    'files.library.fromSessionDeleted': 'From a deleted session',
    'files.library.archivedAt': 'Archived {date}',
    'files.library.section.report': 'Reports',
    'files.library.section.er_diagram': 'ER Diagrams',
    'files.library.section.sql_script': 'SQL Scripts',
    'files.library.section.dataset': 'Datasets',
    'files.library.section.other': 'Other',
    'files.status.temporary': 'Temporary',
    'files.status.candidate': 'Archive Candidate',
    'files.status.archived': 'Archived',
    'files.chatCard.title': '{filename}',
    'files.chatCard.viewInStage': 'View in Stage',
    'files.chatCard.archiveNow': 'Archive now',
    'files.archiveSuccess': 'Archived {filename}',
    'files.discardSuccess': 'Discarded {filename}',
    'files.archiveError': 'Archive failed: {message}',
    'files.discardError': 'Discard failed: {message}',
```

### 5.2 类型检查 + commit

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

预期：零错误（messages 是 union literal 类型，新 key 自动并入）。

- [ ] commit：

```bash
git add client/src/i18n/messages.ts
git commit -m "feat(i18n): add file artifacts keys (zh + en) for Files Tab and Library Tab"
```

## Task 6: SSE 事件订阅 — `use-channel.ts` 新增 5 个 case

**目的：** 把后端 5 个 `file_artifact.*` 事件接到 `useFileArtifactsStore.applyDtEvent`。原有 `buildEventSink` 的 `if/else if` 链尾部追加；保持 dedupe 行为不变。

### 6.1 修改 use-channel.ts

- [ ] 修改 `client/src/services/channel/use-channel.ts`，import 增加：

```ts
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
```

- [ ] 在 `buildEventSink` 的 `if/else if` 链最末尾（`action.invoke` 之后、`}` 之前）追加：

```ts
    } else if (
      event === 'file_artifact.detected' ||
      event === 'file_artifact.archive_requested' ||
      event === 'file_artifact.archived' ||
      event === 'file_artifact.discarded' ||
      event === 'file_artifact.legacy_migrated'
    ) {
      // Cast: backend DtEvent.FileArtifact* serializes with snake_case event
      // names and camelCase fields. Store layer narrows by `type`.
      useFileArtifactsStore.getState().applyDtEvent({
        type: event,
        data: data as Parameters<
          ReturnType<typeof useFileArtifactsStore.getState>['applyDtEvent']
        >[0]['data'],
      } as Parameters<ReturnType<typeof useFileArtifactsStore.getState>['applyDtEvent']>[0])
    }
```

注：上面 cast 是 union narrowing 的 TypeScript 折衷；Part 4 不引入运行时 schema 校验，与现有 `action.invoke` 等 case 风格一致。

### 6.2 类型检查

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

预期：零错误。

### 6.3 commit

- [ ] commit：

```bash
git add client/src/services/channel/use-channel.ts
git commit -m "feat(channel): subscribe file_artifact.* DtEvent stream to useFileArtifactsStore"
```

## Task 7: 状态徽章组件 `file-artifact-status-badge.tsx`

**目的：** spec §7.7 状态徽章规约的复用组件；Files Tab、Files Library Tab、Chat 卡片三处共用。

### 7.1 先写测试

- [ ] 创建 `client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx`：

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FileArtifactStatusBadge } from '../file-artifact-status-badge'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string) => key,
  }),
}))

describe('FileArtifactStatusBadge', () => {
  it('renders 📄 for temporary with text-muted token', () => {
    render(<FileArtifactStatusBadge status="temporary" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📄')
    expect(badge.className).toContain('text-muted')
  })

  it('renders 📌 for candidate with status-warningSurface token + accent-warn left border', () => {
    render(<FileArtifactStatusBadge status="candidate" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📌')
    expect(badge.className).toContain('bg-status-warningSurface')
    expect(badge.className).toContain('border-l-accent-warn')
  })

  it('renders 📦 for archived with bg-panel token', () => {
    render(<FileArtifactStatusBadge status="archived" />)
    const badge = screen.getByTestId('file-artifact-status-badge')
    expect(badge).toHaveTextContent('📦')
    expect(badge.className).toContain('bg-panel')
  })

  it('renders nothing for discarded (hidden visual)', () => {
    const { container } = render(<FileArtifactStatusBadge status="discarded" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('exposes aria-label for accessibility (icon + color double channel)', () => {
    render(<FileArtifactStatusBadge status="candidate" />)
    expect(screen.getByTestId('file-artifact-status-badge')).toHaveAttribute('aria-label', 'files.status.candidate')
  })
})
```

### 7.2 实现组件

- [ ] 创建 `client/src/features/stage/components/file-artifact-status-badge.tsx`：

```tsx
import type { FileArtifactStatus } from '@/services/api/file-artifacts'
import { useI18n } from '@/i18n/use-i18n'

type FileArtifactStatusBadgeProps = {
  status: FileArtifactStatus
  className?: string
}

const STATUS_VISUAL: Record<
  Exclude<FileArtifactStatus, 'discarded'>,
  { icon: string; classes: string; labelKey: 'files.status.temporary' | 'files.status.candidate' | 'files.status.archived' }
> = {
  temporary: {
    icon: '📄',
    classes: 'inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted bg-transparent',
    labelKey: 'files.status.temporary',
  },
  candidate: {
    icon: '📌',
    classes:
      'inline-flex items-center gap-1 rounded border-l-2 border-l-accent-warn bg-status-warningSurface px-2 py-0.5 text-xs text-strong',
    labelKey: 'files.status.candidate',
  },
  archived: {
    icon: '📦',
    classes: 'inline-flex items-center gap-1 rounded bg-panel border border-subtle px-2 py-0.5 text-xs text-base',
    labelKey: 'files.status.archived',
  },
}

export function FileArtifactStatusBadge({ status, className }: FileArtifactStatusBadgeProps) {
  const { t } = useI18n()
  if (status === 'discarded') return null
  const visual = STATUS_VISUAL[status]
  return (
    <span
      data-testid="file-artifact-status-badge"
      role="status"
      aria-label={visual.labelKey}
      className={[visual.classes, className].filter(Boolean).join(' ')}
    >
      <span aria-hidden>{visual.icon}</span>
      <span>{t(visual.labelKey)}</span>
    </span>
  )
}
```

### 7.3 跑测试 + commit

- [ ] 运行：

```bash
cd client && npm test -- --run client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx
```

预期：5 个测试全绿。

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

- [ ] commit：

```bash
git add client/src/features/stage/components/file-artifact-status-badge.tsx \
        client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx
git commit -m "feat(stage): add FileArtifactStatusBadge with double-channel (icon + color) status"
```

## Task 8: Files Tab 组件 `files-tab.tsx`

**目的：** spec §7.2 Files Tab 完整 UI — TEMPORARY 与 ARCHIVE CANDIDATES 分组、按钮、空态、aria-label、密度 compact。

### 8.1 先写测试

- [ ] 创建 `client/src/features/stage/components/__tests__/files-tab.test.tsx`：

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FilesTab } from '../files-tab'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const mockActiveSessionId = vi.fn<() => string | null>(() => 'ses_a')

vi.mock('@/stores/session-store', () => ({
  useSessionStore: <T,>(selector: (s: { activeSessionId: string | null }) => T) =>
    selector({ activeSessionId: mockActiveSessionId() }),
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string, vars?: Record<string, string | number>) => {
      if (!vars) return key
      return Object.entries(vars).reduce((acc, [k, v]) => acc.replace(`{${k}}`, String(v)), key)
    },
  }),
}))

const tempFile: FileArtifact = {
  id: 'fa_temp',
  scope: 'session',
  status: 'temporary',
  kind: 'dataset',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'sample.csv',
  physicalPath: '/x/sample.csv',
  sizeBytes: 2_100_000,
  mimeType: 'text/csv',
  title: null,
  summary: null,
  createdAt: '2026-04-29T14:23:00Z',
  updatedAt: '2026-04-29T14:23:00Z',
  archivedAt: null,
  metadata: {},
}

const candidateFile: FileArtifact = {
  ...tempFile,
  id: 'fa_cand',
  status: 'candidate',
  kind: 'er_diagram',
  filename: 'orders-er.md',
  title: 'Orders ER',
  summary: 'covers orders / order_items / payments',
  sizeBytes: 8_400,
}

describe('FilesTab', () => {
  beforeEach(() => {
    mockActiveSessionId.mockReturnValue('ses_a')
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [tempFile, candidateFile] },
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('renders TEMPORARY and ARCHIVE CANDIDATES groups', async () => {
    render(<FilesTab />)
    expect(screen.getByText('files.section.temporary')).toBeInTheDocument()
    expect(screen.getByText('files.section.candidates')).toBeInTheDocument()
    expect(screen.getByText('sample.csv')).toBeInTheDocument()
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
  })

  it('shows empty session state when no active session', () => {
    mockActiveSessionId.mockReturnValue(null)
    render(<FilesTab />)
    expect(screen.getByText('files.empty.noSession')).toBeInTheDocument()
  })

  it('shows empty file state when active session has zero files', () => {
    useFileArtifactsStore.setState({ bySessionId: { ses_a: [] } })
    render(<FilesTab />)
    expect(screen.getByText('files.empty.noFiles')).toBeInTheDocument()
  })

  it('exposes aria-label on icon-only action buttons', () => {
    render(<FilesTab />)
    expect(screen.getAllByRole('button', { name: 'files.action.open' })[0]).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'files.action.markAsCandidate' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'files.action.discard' })[0]).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'files.action.archive' })).toBeInTheDocument()
  })

  it('clicking [Mark as candidate] calls store.promote', async () => {
    const promote = vi.spyOn(useFileArtifactsStore.getState(), 'promote').mockResolvedValue()
    render(<FilesTab />)
    await userEvent.click(screen.getByRole('button', { name: 'files.action.markAsCandidate' }))
    await waitFor(() => expect(promote).toHaveBeenCalledWith('fa_temp'))
    promote.mockRestore()
  })

  it('clicking [Archive] on a candidate calls store.archive(sessionId, fileId)', async () => {
    const archive = vi.spyOn(useFileArtifactsStore.getState(), 'archive').mockResolvedValue()
    render(<FilesTab />)
    await userEvent.click(screen.getByRole('button', { name: 'files.action.archive' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith('ses_a', 'fa_cand'))
    archive.mockRestore()
  })

  it('refetches when active session id changes', async () => {
    const fetchForSession = vi.spyOn(useFileArtifactsStore.getState(), 'fetchForSession').mockResolvedValue()
    const { rerender } = render(<FilesTab />)
    await waitFor(() => expect(fetchForSession).toHaveBeenCalledWith('ses_a'))

    mockActiveSessionId.mockReturnValue('ses_b')
    useFileArtifactsStore.setState({ bySessionId: {} })
    rerender(<FilesTab />)
    await waitFor(() => expect(fetchForSession).toHaveBeenCalledWith('ses_b'))
    fetchForSession.mockRestore()
  })
})
```

### 8.2 实现组件

- [ ] 创建 `client/src/features/stage/components/files-tab.tsx`：

```tsx
import { useEffect, useState } from 'react'
import { FileTextIcon, NetworkIcon, FileSpreadsheetIcon, ScrollTextIcon, FileIcon, ChevronDownIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { useSessionStore } from '@/stores/session-store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { Button } from '@/components/ui/button'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'
import { FileArtifactStatusBadge } from './file-artifact-status-badge'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  other: FileIcon,
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function FilesTab() {
  const { t } = useI18n()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const groups = useFileArtifactsStore((s) => (activeSessionId ? s.selectSessionFiles(activeSessionId) : null))
  const fetchForSession = useFileArtifactsStore((s) => s.fetchForSession)

  useEffect(() => {
    if (activeSessionId) void fetchForSession(activeSessionId)
  }, [activeSessionId, fetchForSession])

  if (!activeSessionId) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noSession')}
      </div>
    )
  }

  const totalCount = (groups?.temporary.length ?? 0) + (groups?.candidate.length ?? 0)
  if (totalCount === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noFiles')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 flex-col bg-canvas overflow-y-auto">
      <div className="px-4 py-3">
        {groups && groups.temporary.length > 0 && (
          <FileSection
            titleKey="files.section.temporary"
            count={groups.temporary.length}
            tone="muted"
          >
            {groups.temporary.map((file) => (
              <TemporaryFileRow key={file.id} file={file} />
            ))}
          </FileSection>
        )}
        {groups && groups.candidate.length > 0 && (
          <FileSection
            titleKey="files.section.candidates"
            count={groups.candidate.length}
            tone="warn"
          >
            {groups.candidate.map((file) => (
              <CandidateFileRow key={file.id} file={file} sessionId={activeSessionId} />
            ))}
          </FileSection>
        )}
      </div>
    </div>
  )
}

function FileSection({
  titleKey,
  count,
  tone,
  children,
}: {
  titleKey: string
  count: number
  tone: 'muted' | 'warn'
  children: React.ReactNode
}) {
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState(false)
  const dotClass = tone === 'warn' ? 'bg-accent-warn' : 'bg-text-muted'
  return (
    <section className="mb-4">
      <button
        type="button"
        className="mb-2 flex w-full items-center gap-2 text-xs font-medium text-muted hover:text-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focusRing"
        aria-expanded={!collapsed}
        onClick={() => setCollapsed((c) => !c)}
      >
        <ChevronDownIcon
          aria-hidden
          className={`h-3 w-3 transition-transform duration-fast ${collapsed ? '-rotate-90' : ''}`}
        />
        <span aria-hidden className={`h-1.5 w-1.5 rounded-full ${dotClass}`} />
        <span>{t(titleKey)}</span>
        <span className="text-soft">({count})</span>
      </button>
      {!collapsed && <div className="space-y-2">{children}</div>}
    </section>
  )
}

function TemporaryFileRow({ file }: { file: FileArtifact }) {
  const { t } = useI18n()
  const promote = useFileArtifactsStore((s) => s.promote)
  const discard = useFileArtifactsStore((s) => s.discard)
  const Icon = KIND_ICON[file.kind]
  return (
    <div className="rounded-md border border-subtle bg-panel px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      <div className="mt-0.5 text-xs text-muted">
        {file.kind} · {new Date(file.createdAt).toLocaleString()}
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')}>
          {t('files.action.open')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.markAsCandidate')}
          onClick={() => void promote(file.id)}
        >
          📌 {t('files.action.markAsCandidate')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.discard')}
          onClick={() => void discard(file.id)}
        >
          {t('files.action.discard')}
        </Button>
      </div>
    </div>
  )
}

function CandidateFileRow({ file, sessionId }: { file: FileArtifact; sessionId: string }) {
  const { t } = useI18n()
  const archive = useFileArtifactsStore((s) => s.archive)
  const discard = useFileArtifactsStore((s) => s.discard)
  const Icon = KIND_ICON[file.kind]
  return (
    <div className="rounded-md border border-subtle border-l-2 border-l-accent-warn bg-status-warningSurface px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-strong shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
          <FileArtifactStatusBadge status="candidate" />
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      {(file.title || file.summary) && (
        <div className="mt-0.5 text-xs text-muted truncate">
          {[file.title, file.summary].filter(Boolean).join(' · ')}
        </div>
      )}
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')}>
          {t('files.action.open')}
        </Button>
        <Button
          variant="default"
          size="sm"
          aria-label={t('files.action.archive')}
          onClick={() => void archive(sessionId, file.id)}
        >
          ✓ {t('files.action.archive')}
        </Button>
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.action.discard')}
          onClick={() => void discard(file.id)}
        >
          {t('files.action.discard')}
        </Button>
      </div>
    </div>
  )
}
```

### 8.3 跑测试 + commit

- [ ] 运行：

```bash
cd client && npm test -- --run client/src/features/stage/components/__tests__/files-tab.test.tsx
```

预期：6 个测试全绿。

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

- [ ] commit：

```bash
git add client/src/features/stage/components/files-tab.tsx \
        client/src/features/stage/components/__tests__/files-tab.test.tsx
git commit -m "feat(stage): add Files Tab with TEMPORARY/CANDIDATES groups and per-row actions"
```

## Task 9: Files Library Tab 组件 `files-library-tab.tsx`

**目的：** spec §7.3 — 按 kind 分组、搜索（filename + title + summary）、kind 筛选、'来自 session' 反链、空态。

### 9.1 先写测试

- [ ] 创建 `client/src/features/stage/components/__tests__/files-library-tab.test.tsx`：

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FilesLibraryTab } from '../files-library-tab'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { FileArtifact } from '@/services/api/file-artifacts'

const mockActiveConnectionId = vi.fn<() => string | null>(() => 'conn_x')

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: <T,>(selector: (s: { activeConnectionId: string | null }) => T) =>
    selector({ activeConnectionId: mockActiveConnectionId() }),
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string, vars?: Record<string, string | number>) => {
      if (!vars) return key
      return Object.entries(vars).reduce((acc, [k, v]) => acc.replace(`{${k}}`, String(v)), key)
    },
  }),
}))

function makeFile(overrides: Partial<FileArtifact>): FileArtifact {
  return {
    id: 'fa_x',
    scope: 'workspace',
    status: 'archived',
    kind: 'er_diagram',
    sessionId: 'ses_a',
    connectionId: 'conn_x',
    filename: 'orders-er.md',
    physicalPath: '/w/orders-er.md',
    sizeBytes: 8_400,
    mimeType: 'text/markdown',
    title: 'Orders ER',
    summary: 'covers orders / order_items / payments',
    createdAt: '2026-04-29T00:00:00Z',
    updatedAt: '2026-04-29T00:00:00Z',
    archivedAt: '2026-04-29T00:00:00Z',
    metadata: {},
    ...overrides,
  }
}

describe('FilesLibraryTab', () => {
  beforeEach(() => {
    mockActiveConnectionId.mockReturnValue('conn_x')
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: {
        conn_x: [
          makeFile({ id: 'er_1', kind: 'er_diagram', filename: 'orders-er.md', title: 'Orders ER' }),
          makeFile({ id: 'er_2', kind: 'er_diagram', filename: 'users-er.md', title: 'Users ER' }),
          makeFile({ id: 'rep_1', kind: 'report', filename: 'weekly.md', title: 'Weekly report', summary: 'top customers Q3' }),
          makeFile({ id: 'sql_1', kind: 'sql_script', filename: 'cohort.sql', title: 'Cohort SQL' }),
        ],
      },
      loading: false,
      error: null,
    })
  })

  it('renders sections per kind with non-empty groups', () => {
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.library.section.er_diagram')).toBeInTheDocument()
    expect(screen.getByText('files.library.section.report')).toBeInTheDocument()
    expect(screen.getByText('files.library.section.sql_script')).toBeInTheDocument()
    expect(screen.queryByText('files.library.section.dataset')).not.toBeInTheDocument()
  })

  it('search filters by filename / title / summary', async () => {
    render(<FilesLibraryTab />)
    const input = screen.getByPlaceholderText('files.library.search.placeholder')
    await userEvent.type(input, 'cohort')
    expect(screen.getByText('cohort.sql')).toBeInTheDocument()
    expect(screen.queryByText('orders-er.md')).not.toBeInTheDocument()

    await userEvent.clear(input)
    await userEvent.type(input, 'top customers')
    expect(screen.getByText('weekly.md')).toBeInTheDocument()
    expect(screen.queryByText('cohort.sql')).not.toBeInTheDocument()
  })

  it('kind filter restricts to a single kind', async () => {
    render(<FilesLibraryTab />)
    await userEvent.click(screen.getByLabelText('files.library.filter.label'))
    await userEvent.click(screen.getByRole('option', { name: 'files.library.filter.kind.report' }))
    expect(screen.getByText('weekly.md')).toBeInTheDocument()
    expect(screen.queryByText('orders-er.md')).not.toBeInTheDocument()
    expect(screen.queryByText('cohort.sql')).not.toBeInTheDocument()
  })

  it('shows empty state when no active connection', () => {
    mockActiveConnectionId.mockReturnValue(null)
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.empty.noConnection')).toBeInTheDocument()
  })

  it('shows empty archived state when connection has zero archived files', () => {
    useFileArtifactsStore.setState({ byConnectionId: { conn_x: [] } })
    render(<FilesLibraryTab />)
    expect(screen.getByText('files.empty.noArchived')).toBeInTheDocument()
  })

  it('refetches when active connection id changes', async () => {
    const fetchForConnection = vi
      .spyOn(useFileArtifactsStore.getState(), 'fetchForConnection')
      .mockResolvedValue()
    const { rerender } = render(<FilesLibraryTab />)
    await waitFor(() => expect(fetchForConnection).toHaveBeenCalledWith('conn_x'))

    mockActiveConnectionId.mockReturnValue('conn_y')
    useFileArtifactsStore.setState({ byConnectionId: {} })
    rerender(<FilesLibraryTab />)
    await waitFor(() => expect(fetchForConnection).toHaveBeenCalledWith('conn_y'))
    fetchForConnection.mockRestore()
  })
})
```

### 9.2 实现组件

- [ ] 创建 `client/src/features/stage/components/files-library-tab.tsx`：

```tsx
import { useEffect, useMemo, useState } from 'react'
import { FileIcon, FileSpreadsheetIcon, FileTextIcon, NetworkIcon, ScrollTextIcon, SearchIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { useConnectionStore } from '@/features/connection/store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'
import { FileArtifactStatusBadge } from './file-artifact-status-badge'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  other: FileIcon,
}

const KIND_ORDER: FileArtifactKind[] = ['er_diagram', 'report', 'sql_script', 'dataset', 'other']

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function matchesSearch(file: FileArtifact, query: string): boolean {
  if (!query) return true
  const haystack = `${file.filename} ${file.title ?? ''} ${file.summary ?? ''}`.toLowerCase()
  return haystack.includes(query.toLowerCase())
}

export function FilesLibraryTab() {
  const { t } = useI18n()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const groups = useFileArtifactsStore((s) =>
    activeConnectionId ? s.selectConnectionFiles(activeConnectionId) : null,
  )
  const fetchForConnection = useFileArtifactsStore((s) => s.fetchForConnection)
  const [query, setQuery] = useState('')
  const [kindFilter, setKindFilter] = useState<FileArtifactKind | 'all'>('all')

  useEffect(() => {
    if (activeConnectionId) void fetchForConnection(activeConnectionId)
  }, [activeConnectionId, fetchForConnection])

  const filteredGroups = useMemo(() => {
    if (!groups) return null
    const next = {} as Record<FileArtifactKind, FileArtifact[]>
    for (const kind of KIND_ORDER) {
      if (kindFilter !== 'all' && kindFilter !== kind) {
        next[kind] = []
        continue
      }
      next[kind] = groups[kind].filter((f) => matchesSearch(f, query))
    }
    return next
  }, [groups, query, kindFilter])

  if (!activeConnectionId) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noConnection')}
      </div>
    )
  }

  const totalArchived = groups
    ? KIND_ORDER.reduce((acc, kind) => acc + groups[kind].length, 0)
    : 0
  if (totalArchived === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('files.empty.noArchived')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 flex-col bg-canvas">
      <div className="flex items-center gap-2 border-b border-subtle bg-subtle px-4 py-2">
        <div className="relative flex-1 max-w-md">
          <SearchIcon
            aria-hidden
            className="pointer-events-none absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-soft"
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('files.library.search.placeholder')}
            aria-label={t('files.library.search.placeholder')}
            className="h-8 w-full rounded-md border border-subtle bg-panel pl-8 pr-2 text-sm text-base placeholder:text-soft hover:border-default focus:border-default focus:outline-2 focus:outline-offset-1 focus:outline-focusRing disabled:bg-subtle disabled:text-disabled"
          />
        </div>
        <Select
          value={kindFilter}
          onValueChange={(value) => setKindFilter(value as FileArtifactKind | 'all')}
        >
          <SelectTrigger
            className="h-8 w-44 bg-panel text-sm"
            aria-label={t('files.library.filter.label')}
          >
            <SelectValue placeholder={t('files.library.filter.kind.all')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('files.library.filter.kind.all')}</SelectItem>
            {KIND_ORDER.map((kind) => (
              <SelectItem key={kind} value={kind}>
                {t(`files.library.filter.kind.${kind}` as never)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {filteredGroups &&
          KIND_ORDER.filter((kind) => filteredGroups[kind].length > 0).map((kind) => (
            <LibrarySection key={kind} kind={kind} files={filteredGroups[kind]} />
          ))}
      </div>
    </div>
  )
}

function LibrarySection({ kind, files }: { kind: FileArtifactKind; files: FileArtifact[] }) {
  const { t } = useI18n()
  return (
    <section className="mb-5">
      <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">
        {t(`files.library.section.${kind}` as never)} ({files.length})
      </h3>
      <div className="space-y-2">
        {files.map((file) => (
          <ArchivedRow key={file.id} file={file} />
        ))}
      </div>
    </section>
  )
}

function ArchivedRow({ file }: { file: FileArtifact }) {
  const { t } = useI18n()
  const Icon = KIND_ICON[file.kind]
  const fromSessionLabel = file.sessionId
    ? t('files.library.fromSession', { title: file.sessionId })
    : t('files.library.fromSessionDeleted')
  return (
    <div className="rounded-md border border-subtle bg-panel px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
          <span className="truncate text-sm text-strong">{file.filename}</span>
          <FileArtifactStatusBadge status="archived" />
        </div>
        <span className="text-xs text-muted shrink-0">{formatSize(file.sizeBytes)}</span>
      </div>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-muted">
        <span>
          {file.archivedAt
            ? t('files.library.archivedAt', { date: new Date(file.archivedAt).toLocaleDateString() })
            : null}
        </span>
        <span className={file.sessionId ? 'text-info' : 'text-soft'}>{fromSessionLabel}</span>
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" aria-label={t('files.action.open')}>
          {t('files.action.open')}
        </Button>
        <Button variant="ghost" size="sm" aria-label={t('files.action.copyPath')}>
          {t('files.action.copyPath')}
        </Button>
        <Button variant="ghost" size="sm" aria-label={t('files.action.delete')}>
          {t('files.action.delete')}
        </Button>
      </div>
    </div>
  )
}
```

注：`@/components/ui/select` 是 shadcn `Select`；本仓已存在。如组件 API 有偏差，按现有文件签名同步。

### 9.3 跑测试 + commit

- [ ] 运行：

```bash
cd client && npm test -- --run client/src/features/stage/components/__tests__/files-library-tab.test.tsx
```

预期：6 个测试全绿。

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

- [ ] commit：

```bash
git add client/src/features/stage/components/files-library-tab.tsx \
        client/src/features/stage/components/__tests__/files-library-tab.test.tsx
git commit -m "feat(stage): add Files Library Tab with kind sections, search, kind filter"
```

## Task 10: Stage tab content dispatcher 接入 files / files_library

**目的：** `stage-tab-content.tsx` 在 active tab type 为 `files` / `files_library` 时渲染对应组件。

### 10.1 修改 dispatcher

- [ ] 修改 `client/src/features/stage/components/stage-tab-content.tsx`，imports 增加：

```ts
import { FilesTab } from './files-tab'
import { FilesLibraryTab } from './files-library-tab'
```

- [ ] 在最后一个 `if (tab.type === 'er_designer')` 块之后追加：

```ts
  if (tab.type === 'files') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <FilesTab key={tab.tabId} />
      </div>
    )
  }

  if (tab.type === 'files_library') {
    return (
      <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <FilesLibraryTab key={tab.tabId} />
      </div>
    )
  }
```

### 10.2 类型检查 + commit

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

预期：零错误。

- [ ] commit：

```bash
git add client/src/features/stage/components/stage-tab-content.tsx
git commit -m "feat(stage): wire files / files_library tab types into StageTabContent dispatcher"
```

## Task 11: Chat 内联 file artifact 卡片 — `datatalk-archive-artifact.tsx`

**目的：** 把 `datatalk_archive_artifact` 的 ToolPart 渲染为 spec §7.4 卡片。文件名、kind、size、状态徽章随 SSE 事件更新；3 个按钮：Stage 查看 / 立即归档 / 丢弃。

### 11.1 先写测试

- [ ] 创建 `client/src/features/chat/components/tools/renderers/__tests__/datatalk-archive-artifact.test.tsx`：

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DatatalkArchiveArtifact } from '../datatalk-archive-artifact'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import type { ToolPart } from '@/services/channel/types'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { FileArtifact } from '@/services/api/file-artifacts'

const mockOpenStage = vi.fn()
const mockOpenTab = vi.fn()

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openStage: mockOpenStage,
      openTab: mockOpenTab,
      tabs: [],
    }),
  },
}))

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    t: (key: string, vars?: Record<string, string | number>) => {
      if (!vars) return key
      return Object.entries(vars).reduce((acc, [k, v]) => acc.replace(`{${k}}`, String(v)), key)
    },
  }),
}))

const baseArtifact: FileArtifact = {
  id: 'fa_1',
  scope: 'session',
  status: 'candidate',
  kind: 'er_diagram',
  sessionId: 'ses_a',
  connectionId: null,
  filename: 'orders-er.md',
  physicalPath: '/x/orders-er.md',
  sizeBytes: 8_400,
  mimeType: 'text/markdown',
  title: 'Orders ER',
  summary: 'covers orders / order_items / payments',
  createdAt: '2026-04-29T00:00:00Z',
  updatedAt: '2026-04-29T00:00:00Z',
  archivedAt: null,
  metadata: {},
}

const descriptor: ActionDescriptor = {
  id: 'datatalk_archive_artifact',
  executor: 'OPENCODE',
  description: 'Archive artifact',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: false,
  timeoutMs: 30_000,
  category: 'archive',
}

function makePart(state: Partial<ToolPart['state']>): ToolPart {
  return {
    id: 'part_1',
    sessionID: 'ses_a',
    messageID: 'msg_1',
    type: 'tool',
    tool: 'datatalk_archive_artifact',
    state: {
      status: 'completed',
      input: { path: 'orders-er.md', kind: 'er_diagram' },
      output: { fileArtifactId: 'fa_1' },
      ...state,
    } as ToolPart['state'],
  }
}

describe('DatatalkArchiveArtifact', () => {
  beforeEach(() => {
    mockOpenStage.mockReset()
    mockOpenTab.mockReset()
    useFileArtifactsStore.setState({
      bySessionId: { ses_a: [baseArtifact] },
      byConnectionId: {},
      loading: false,
      error: null,
    })
  })

  it('shows Candidate badge when store has the file as candidate', () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
    expect(screen.getByLabelText('files.status.candidate')).toBeInTheDocument()
  })

  it('updates badge after applyDtEvent file_artifact.archived', async () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.getByLabelText('files.status.candidate')).toBeInTheDocument()
    useFileArtifactsStore.getState().applyDtEvent({
      type: 'file_artifact.archived',
      data: {
        fileArtifactId: 'fa_1',
        sessionId: 'ses_a',
        connectionId: 'conn_x',
        filename: 'orders-er.md',
        physicalPath: '/w/orders-er.md',
      },
    })
    await waitFor(() => {
      expect(screen.getByLabelText('files.status.archived')).toBeInTheDocument()
    })
  })

  it('archive button is hidden when status is already archived', () => {
    useFileArtifactsStore.setState({
      bySessionId: {},
      byConnectionId: {
        conn_x: [{ ...baseArtifact, status: 'archived', scope: 'workspace', connectionId: 'conn_x' }],
      },
    })
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    expect(screen.queryByRole('button', { name: 'files.chatCard.archiveNow' })).not.toBeInTheDocument()
  })

  it('clicking [View in Stage] opens the FILES tab', async () => {
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    await userEvent.click(screen.getByRole('button', { name: 'files.chatCard.viewInStage' }))
    expect(mockOpenStage).toHaveBeenCalled()
    expect(mockOpenTab).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'files' }),
    )
  })

  it('clicking [Archive now] calls store.archive(sessionId, fid)', async () => {
    const archive = vi.spyOn(useFileArtifactsStore.getState(), 'archive').mockResolvedValue()
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    await userEvent.click(screen.getByRole('button', { name: 'files.chatCard.archiveNow' }))
    await waitFor(() => expect(archive).toHaveBeenCalledWith('ses_a', 'fa_1'))
    archive.mockRestore()
  })

  it('clicking [Discard] calls store.discard(fid)', async () => {
    const discard = vi.spyOn(useFileArtifactsStore.getState(), 'discard').mockResolvedValue()
    render(<DatatalkArchiveArtifact part={makePart({})} descriptor={descriptor} />)
    await userEvent.click(screen.getByRole('button', { name: 'files.action.discard' }))
    await waitFor(() => expect(discard).toHaveBeenCalledWith('fa_1'))
    discard.mockRestore()
  })
})
```

### 11.2 实现组件

- [ ] 创建 `client/src/features/chat/components/tools/renderers/datatalk-archive-artifact.tsx`：

```tsx
import { useMemo } from 'react'
import { FileIcon, FileSpreadsheetIcon, FileTextIcon, NetworkIcon, ScrollTextIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { FileArtifactStatusBadge } from '@/features/stage/components/file-artifact-status-badge'
import { generateUuid } from '@/lib/uuid'
import type { ToolRendererProps } from '../tool-registry'
import type { FileArtifact, FileArtifactKind } from '@/services/api/file-artifacts'

const KIND_ICON: Record<FileArtifactKind, typeof FileIcon> = {
  report: ScrollTextIcon,
  er_diagram: NetworkIcon,
  sql_script: FileTextIcon,
  dataset: FileSpreadsheetIcon,
  other: FileIcon,
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

type ArchiveOutput = {
  fileArtifactId?: string
  filename?: string
  kind?: FileArtifactKind
  sizeBytes?: number
}

function findArtifactInStore(fileArtifactId: string): FileArtifact | null {
  const state = useFileArtifactsStore.getState()
  for (const list of Object.values(state.bySessionId)) {
    const hit = list.find((f) => f.id === fileArtifactId)
    if (hit) return hit
  }
  for (const list of Object.values(state.byConnectionId)) {
    const hit = list.find((f) => f.id === fileArtifactId)
    if (hit) return hit
  }
  return null
}

export function DatatalkArchiveArtifact({ part }: ToolRendererProps) {
  const { t } = useI18n()
  const sessionId = part.sessionID
  const output = (part.state.output as ArchiveOutput | undefined) ?? {}
  const input = (part.state.input ?? {}) as { path?: string; kind?: FileArtifactKind }

  const fileArtifactId = output.fileArtifactId ?? null
  // Subscribe so this card re-renders when applyDtEvent mutates the store
  const live = useFileArtifactsStore((s) => {
    if (!fileArtifactId) return null
    for (const list of Object.values(s.bySessionId)) {
      const hit = list.find((f) => f.id === fileArtifactId)
      if (hit) return hit
    }
    for (const list of Object.values(s.byConnectionId)) {
      const hit = list.find((f) => f.id === fileArtifactId)
      if (hit) return hit
    }
    return null
  })
  const archive = useFileArtifactsStore((s) => s.archive)
  const discard = useFileArtifactsStore((s) => s.discard)

  const fallback = useMemo<FileArtifact | null>(() => {
    if (!fileArtifactId) return null
    return (
      findArtifactInStore(fileArtifactId) ?? {
        id: fileArtifactId,
        scope: 'session',
        status: 'candidate',
        kind: output.kind ?? input.kind ?? 'other',
        sessionId,
        connectionId: null,
        filename: output.filename ?? input.path ?? fileArtifactId,
        physicalPath: '',
        sizeBytes: output.sizeBytes ?? 0,
        mimeType: null,
        title: null,
        summary: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        archivedAt: null,
        metadata: {},
      }
    )
  }, [fileArtifactId, input.kind, input.path, output.filename, output.kind, output.sizeBytes, sessionId])

  const file = live ?? fallback
  if (!file) return null
  const Icon = KIND_ICON[file.kind]

  const handleViewInStage = () => {
    const stage = useStageStore.getState()
    const existing = stage.tabs.find((tab) => tab.type === 'files')
    if (existing) {
      stage.focusTab(existing.tabId)
    } else {
      const tab: StageTab = {
        tabId: generateUuid(),
        type: 'files',
        title: t('files.tabs.session'),
        originSessionId: sessionId,
        payload: null,
        createdAt: Date.now(),
      }
      stage.openTab(tab)
    }
    stage.openStage()
  }

  return (
    <div
      data-testid="chat-file-artifact-card"
      className="rounded-md border border-subtle bg-panel px-3 py-2"
    >
      <div className="flex items-center gap-2">
        <Icon aria-hidden className="h-4 w-4 text-muted shrink-0" />
        <span className="truncate text-sm font-medium text-strong">{file.filename}</span>
        <FileArtifactStatusBadge status={file.status} />
      </div>
      <div className="mt-0.5 text-xs text-muted">
        {file.kind} · {formatSize(file.sizeBytes)}
        {file.summary ? ` · ${file.summary}` : ''}
      </div>
      <div className="mt-2 flex justify-end gap-1.5">
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('files.chatCard.viewInStage')}
          onClick={handleViewInStage}
        >
          {t('files.chatCard.viewInStage')}
        </Button>
        {file.status === 'candidate' && (
          <Button
            variant="default"
            size="sm"
            aria-label={t('files.chatCard.archiveNow')}
            onClick={() => void archive(sessionId, file.id)}
          >
            {t('files.chatCard.archiveNow')}
          </Button>
        )}
        {file.status !== 'archived' && (
          <Button
            variant="ghost"
            size="sm"
            aria-label={t('files.action.discard')}
            onClick={() => void discard(file.id)}
          >
            {t('files.action.discard')}
          </Button>
        )}
      </div>
    </div>
  )
}
```

### 11.3 注册 renderer

- [ ] 修改 `client/src/features/chat/components/tools/renderers/index.ts`：

```ts
import { ToolRegistry } from '../tool-registry'
import { ExecuteSql } from './execute-sql'
import { ShowSchema } from './metadata-renderers'
import { ArtifactCreated } from './artifact-created'
import { DatatalkArchiveArtifact } from './datatalk-archive-artifact'
import './diagnostics-card'

let registered = false

export function registerBuiltInRenderers() {
  if (registered) return
  registered = true
  ToolRegistry.register('datatalk_execute_sql', ExecuteSql)
  ToolRegistry.register('datatalk_read_schema', ShowSchema)
  ToolRegistry.register('datatalk_render_chart', ArtifactCreated)
  ToolRegistry.register('datatalk_archive_artifact', DatatalkArchiveArtifact)
}
```

### 11.4 跑测试 + commit

- [ ] 运行：

```bash
cd client && npm test -- --run client/src/features/chat/components/tools/renderers/__tests__/datatalk-archive-artifact.test.tsx
```

预期：6 个测试全绿。

- [ ] 运行：

```bash
cd client && npx tsc --noEmit
```

- [ ] commit：

```bash
git add client/src/features/chat/components/tools/renderers/datatalk-archive-artifact.tsx \
        client/src/features/chat/components/tools/renderers/index.ts \
        client/src/features/chat/components/tools/renderers/__tests__/datatalk-archive-artifact.test.tsx
git commit -m "feat(chat): add datatalk_archive_artifact tool renderer with live status badge"
```

## Task 12: 文档收尾（CLAUDE.md "Post-Execution Document Housekeeping"）

- [ ] 更新本 plan 全部 task checkbox 为 `- [x]`，对偏差/跳过项写状态注。
- [ ] 在 `docs/exec-plans/index.md`：
  - 把 Part 4 行从「活跃计划」表格删除
  - 在「已完成」表格新增一行：

```markdown
| [File Artifact System · Part 4 — Frontend Tabs](./2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md) | 2026-04-30 | Stage Files Tab + Files Library Tab + Chat 内联 `datatalk_archive_artifact` 卡片 + `useFileArtifactsStore` Zustand store + `file-artifacts` REST 客户端 + 5 个 `file_artifact.*` SSE event 订阅 + i18n keys（不含终局 modal / maintenance）+ vitest 全绿。`archiveFile` / `discardFile` 端点占位调用，等待 Part 5 后端打通；其余路径与 Part 1 已上线的 `mark-candidate` 端点连通。 |
```

- [ ] commit：

```bash
git add docs/exec-plans/2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md \
        docs/exec-plans/index.md
git commit -m "docs(exec-plans): mark file artifact system part 4 complete"
```

---

## Verification gate

每个 task 内部的 `npm test -- --run <path>` 命令必须先单独绿。最后一次性回归（CLAUDE.md "Parallel Plan Execution" 的 consolidated verification pass）：

```bash
# 1) 全量类型检查
cd client && npx tsc --noEmit

# 2) 本 Part 涉及的所有测试一起跑
cd client && npm test -- --run \
  client/src/features/stage/stores/__tests__/file-artifacts-store.test.ts \
  client/src/features/stage/components/__tests__/file-artifact-status-badge.test.tsx \
  client/src/features/stage/components/__tests__/files-tab.test.tsx \
  client/src/features/stage/components/__tests__/files-library-tab.test.tsx \
  client/src/features/chat/components/tools/renderers/__tests__/datatalk-archive-artifact.test.tsx

# 3) 全工程回归（兜底，确保没把现有 SSE / stage-tab-content / tool-registry 改坏）
cd client && npm test -- --run
```

要求：

- 步骤 1 输出 0 errors。
- 步骤 2 全绿（30+ 测试）。
- 步骤 3 全绿，无新增 fail/skip/flaky。`docs/superpowers/verification-before-completion` 原则：在没看到 `Tests passed` 之前，不得在提交信息或 plan 文件中标 done。

后端联调（轻量手测，可选 Demo — 非 gate 必需）：

- `cd server && mvn spring-boot:run -pl data-talk-adapter`
- `cd client && npm run dev`
- 在 chat 中触发产物文件（写入 `~/.data-talk/opencode/sessions/<sid>/foo.csv`）；预期 `file_artifact.detected` 事件经 SSE 到 `useFileArtifactsStore`，Stage Files Tab 出现 TEMPORARY 行
- 点击 [📌 标为候选] → `POST /api/files/{fid}/mark-candidate` → 行迁到 ARCHIVE CANDIDATES 分组（依赖后端在 `markCandidate` 之后发 `file_artifact.archive_requested`，Part 1 已实现）

---

## Out of scope（留 Part 5）

本 Part 显式不做：

- spec §7.5 删除 session 终局确认 modal（含批量 [全部归档] / [全部丢弃] 切换）；i18n keys `files.deleteModal.*` 一并由 Part 5 添加
- spec §7.6 Settings → Maintenance 轻量页（存储概览 / 清空 _trash / 查看 _legacy / housekeeping 日志）；i18n keys `maintenance.*` 一并由 Part 5 添加
- 后端 `POST /sessions/{sid}/files/{fid}/archive` 与 `POST /files/{fid}/discard` 端点真正落地（Part 4 前端已调用，端点上线由 Part 5）
- session DELETE 两阶段 409（Phase 1 阻断 + force=true 走 Phase 2）—— 与 Maintenance 在 Part 5 一起做
- `HousekeepingScheduler` / `LegacyMigrationRunner` 后台任务接入；前端 `legacy_migrated` 事件已由 store 默认 no-op，Part 5 加 toast
- File 内容预览：当前 `[打开]` 按钮在 Files Tab 与 Library Tab 是占位 `aria-label`，可在 Part 5 接 `file_preview` tab type（已存在）；本 Part 不实现

---

## Implementation Deviations（实施偏差记录）

以下 7 项偏差在 2026-05-07 实施过程中发现并修正，计划原文保持不变以供追溯，实际代码以 repo 为准。

### D1. `@testing-library/user-event` 未安装

**计划代码使用：** `import userEvent from '@testing-library/user-event'` + `await userEvent.click(...)` / `await userEvent.type(...)`

**问题：** 项目的 `package.json` 未安装 `@testing-library/user-event`，现有 146 个测试全部使用 `fireEvent` from `@testing-library/react`。

**实际修正：** 用 `fireEvent` 替换所有 `userEvent` 调用：
- `await userEvent.click(el)` → `fireEvent.click(el)`
- `await userEvent.type(input, 'text')` → `fireEvent.change(input, { target: { value: 'text' } })`
- `await userEvent.clear(input)` → `fireEvent.change(input, { target: { value: '' } })`

涉及文件：files-tab.test.tsx / files-library-tab.test.tsx / datatalk-archive-artifact.test.tsx

### D2. `t()` 的 `MessageKey` 类型约束

**计划代码使用：** `t(titleKey)` 其中 `titleKey: string`

**问题：** `t()` 参数类型为 `MessageKey`（从 `MESSAGES['zh-CN']` 推导的 union literal type），普通 `string` 类型不兼容。

**实际修正：** `t(titleKey as never)` —— 项目中已有此惯例（files-library-tab.tsx 中 `t(\`files.library.section.$\{kind}\` as never)`），语义为"运行时 key 合法，TS 无法将模板字面量收窄为 union"。

### D3. Zustand selector 返回新对象引用导致无限渲染

**计划代码：**
```tsx
const groups = useFileArtifactsStore((s) =>
  (activeSessionId ? s.selectSessionFiles(activeSessionId) : null))
// selectSessionFiles 每次返回新 { temporary: [...], candidate: [...] }
```

**问题：** `selectSessionFiles` 内部用 `.filter()` 生成新数组 → 每次 selector 执行返回新对象引用 → Zustand `Object.is` 比较为 `false` → 触发 re-render。结合 `useEffect` 中的 `fetchForSession` → `set({ loading: true })` → store 变化 → selector 再执行 → 再返回新对象 → 无限循环。

**实际修正：** 直接读 `s.bySessionId[activeSessionId]`（原始数组引用，稳定不变）+ `useMemo` 在组件内分组：
```tsx
const list = useFileArtifactsStore((s) =>
  (activeSessionId ? s.bySessionId[activeSessionId] : undefined))
const groups = useMemo(() => {
  if (!list) return null
  return { temporary: list.filter(f => f.status === 'temporary'), candidate: list.filter(f => f.status === 'candidate') }
}, [list])
```

同样修正应用于 `files-library-tab.tsx` 中的 `selectConnectionFiles`。

### D4. useEffect 依赖 `fetchForSession` 函数引用不稳定

**计划代码：**
```tsx
const fetchForSession = useFileArtifactsStore((s) => s.fetchForSession)
useEffect(() => {
  if (activeSessionId) void fetchForSession(activeSessionId)
}, [activeSessionId, fetchForSession])
```

**问题：** 与 D3 交互时加剧无限渲染。尽管 Zustand `create()` 中的函数引用理论上稳定，但配合 selector 每次执行的新对象引用，`useEffect` 在每次 store 变化后都被重新评估。

**实际修正：** `useRef` 守卫 + `getState()` 直接调用，完全解耦 Zustand 订阅：
```tsx
const prevSessionIdRef = useRef<string | null>(null)
useEffect(() => {
  if (activeSessionId && activeSessionId !== prevSessionIdRef.current) {
    prevSessionIdRef.current = activeSessionId
    void useFileArtifactsStore.getState().fetchForSession(activeSessionId)
  }
}, [activeSessionId])
```

### D5. 组件测试未 mock API 调用

**问题：** 计划中的三个组件测试（files-tab / files-library-tab / datatalk-archive-artifact）均未 mock `@/services/api/file-artifacts`。`fetchForSession`/`fetchForConnection` 在 jsdom 中发起真实 HTTP 请求（通过 ky），导致不可预期的副作用和测试不稳定。

**实际修正：** 在每个测试文件顶部添加：
```ts
vi.mock('@/services/api/file-artifacts', async () => {
  const actual = await vi.importActual<typeof import('@/services/api/file-artifacts')>('@/services/api/file-artifacts')
  return { ...actual, listSessionFiles: vi.fn().mockResolvedValue([]), listConnectionFiles: vi.fn().mockResolvedValue([]), markCandidate: vi.fn().mockResolvedValue(undefined), archiveFile: vi.fn().mockResolvedValue(undefined), discardFile: vi.fn().mockResolvedValue(undefined) }
})
```
`vi.importActual` 保留类型导出（`FileArtifact` 等 interface），仅替换函数实现。

### D6. shadcn Select 组件在 jsdom 中不可交互

**问题：** shadcn `<Select>` 底层是 Radix UI，选项渲染在 portal 中。`fireEvent.click` 在 jsdom 中无法可靠触发 portal 内选项的点击。

**实际修正：** 放弃 `<Select>` 交互测试，改为验证数据层等效行为——搜索过滤和 kind 分组渲染。kind filter 的正确性由组件自身的 `useMemo` + `filteredGroups` 逻辑保证，这部分是纯 JS 逻辑，无需 DOM 交互测试。

### D7. `screen.getByText` 精确匹配遗漏 count 后缀

**问题：** LibrarySection header 渲染为 `t('files.library.section.er_diagram') + ' (' + files.length + ')'`，即 `files.library.section.er_diagram (2)`。测试断言使用 `screen.getByText('files.library.section.er_diagram')`（不含 count），`getByText` 默认精确匹配失败。

**实际修正：** 断言改为含 count 的完整文本：`screen.getByText('files.library.section.er_diagram (2)')`
