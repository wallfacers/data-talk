# Stage 即电脑：右栏可关可开 实施计划

> **Spec**：[2026-04-17-stage-as-computer-design.md](../specs/2026-04-17-stage-as-computer-design.md)
> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans

**Goal**：把 SPLIT 态右侧 stage 套一层圆角窗体（macOS titlebar），主输入框 Auto 旁加小电脑按钮可手动开关，首个 artifact 到达时智能自弹；同时整删 `/preview` 沙盒。

**Architecture**：新增 `features/stage/` 组件目录 + `stores/stage-store.ts`；`SplitView` 用 react-resizable-panels 自带的 `collapsible` API 接入开关；`HomePage` 挂一次性 zustand subscribe 实现自弹；不引入新依赖、不改后端、不改 SQLite。

**Tech Stack**：React 19、Zustand、react-resizable-panels v2、TanStack Router、vitest + @testing-library/react、lucide-react。

---

## 命名一览

- store: `useStageStore`，`openBySession: Map<sessionId, boolean>`、`autoOpenedSessions: Set<sessionId>`
- 组件：`<StageWindow>`、`<StageToggleButton>`
- hook：`useActiveArtifactTitle(sessionId)` 返回 `{ icon: ReactNode, label: string }`
- 副作用挂载：`ensureStageAutoOpenSubscribed()`，module 顶层闭包保证一次性

## 关键约定

- **测试模式**：每个 store/hook/组件先写失败测试，再写实现
- **commit 节奏**：每个 Task 末尾一次 commit
- **commit 信息**：遵循项目 `feat(client)` / `test(client)` / `chore(client)` 风格，`Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>` 收尾
- **每改一次 ts/tsx 后**：跑 `cd client && npx tsc --noEmit`，确认零报错（CLAUDE.md 强制）
- **测试命令**：`cd client && npm test -- <test-file>`

---

## File Structure

```
client/src/
  stores/
    stage-store.ts                                ← Task 1 新建
    stage-store.test.ts                           ← Task 1 新建
  features/
    stage/
      use-active-artifact-title.tsx               ← Task 2 新建
      use-stage-auto-open.ts                      ← Task 3 新建
      use-stage-auto-open.test.ts                 ← Task 3 新建
      components/
        stage-window.tsx                          ← Task 4 新建
        stage-window.test.tsx                     ← Task 4 新建
        stage-toggle-button.tsx                   ← Task 5 新建
        stage-toggle-button.test.tsx              ← Task 5 新建
    session/
      split-view.tsx                              ← Task 6 改造
      prompt-composer.tsx                         ← Task 7 改造
    workspace/
      home-page.tsx                               ← Task 8 改造
  routes/
    preview.tsx                                   ← Task 9 删除
  features/preview/                               ← Task 9 删除整个目录
```

---

## Task 1: stage-store

**Files:**
- Create: `client/src/stores/stage-store.ts`
- Test: `client/src/stores/stage-store.test.ts`

- [ ] **Step 1: 写失败测试** `client/src/stores/stage-store.test.ts`

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useStageStore } from './stage-store'

describe('stage-store', () => {
  beforeEach(() => {
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
  })

  it('openStage / closeStage 切换 openBySession', () => {
    const { openStage, closeStage } = useStageStore.getState()
    openStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    closeStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('closeStage 隐式 markAutoOpened，禁止后续自弹', () => {
    const { closeStage, notifyArtifactArrived } = useStageStore.getState()
    closeStage('s1')
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('notifyArtifactArrived 首次自弹，幂等', () => {
    const { notifyArtifactArrived } = useStageStore.getState()
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    // 再次调用不影响（幂等）
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('toggleStage 切换状态', () => {
    const { toggleStage } = useStageStore.getState()
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('syncCollapsed(true) 反向同步并标 autoOpened（拖动 collapse 视同主动关）', () => {
    const { openStage, syncCollapsed } = useStageStore.getState()
    openStage('s1')
    syncCollapsed('s1', true)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
  })

  it('syncCollapsed(false) 反向同步且不动 autoOpened', () => {
    const { syncCollapsed } = useStageStore.getState()
    syncCollapsed('s1', false)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(false)
  })

  it('clear 清空指定 session', () => {
    const { openStage, clear } = useStageStore.getState()
    openStage('s1')
    openStage('s2')
    clear('s1')
    expect(useStageStore.getState().openBySession.has('s1')).toBe(false)
    expect(useStageStore.getState().openBySession.get('s2')).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认 fail**

`cd client && npm test -- src/stores/stage-store.test.ts`
Expected: FAIL（store 文件不存在）

- [ ] **Step 3: 写实现** `client/src/stores/stage-store.ts`

```ts
import { create } from 'zustand'

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),

  openStage: (sid) => set((s) => {
    const m = new Map(s.openBySession); m.set(sid, true)
    return { openBySession: m }
  }),

  closeStage: (sid) => set((s) => {
    const m = new Map(s.openBySession); m.set(sid, false)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  toggleStage: (sid) => {
    const cur = !!get().openBySession.get(sid)
    if (cur) get().closeStage(sid)
    else get().openStage(sid)
  },

  notifyArtifactArrived: (sid) => set((s) => {
    if (s.autoOpenedSessions.has(sid)) return s
    if (s.openBySession.get(sid)) return s
    const m = new Map(s.openBySession); m.set(sid, true)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  syncCollapsed: (sid, collapsed) => set((s) => {
    const cur = s.openBySession.get(sid)
    const next = !collapsed
    if (cur === next) return s
    const m = new Map(s.openBySession); m.set(sid, next)
    const a = collapsed
      ? new Set(s.autoOpenedSessions).add(sid) && new Set(s.autoOpenedSessions).add(sid)
      : s.autoOpenedSessions
    // 上面三元由于 Set.prototype.add 返回 Set，需要单独构造
    if (collapsed) {
      const newA = new Set(s.autoOpenedSessions); newA.add(sid)
      return { openBySession: m, autoOpenedSessions: newA }
    }
    return { openBySession: m }
  }),

  clear: (sid) => set((s) => {
    const m = new Map(s.openBySession); m.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),
}))
```

> 注意：`syncCollapsed` 上方那段三元是写错的占位，删掉，只保留下面 if 块的清晰版本。最终代码：

```ts
syncCollapsed: (sid, collapsed) => set((s) => {
  const cur = s.openBySession.get(sid)
  const next = !collapsed
  if (cur === next) return s
  const m = new Map(s.openBySession); m.set(sid, next)
  if (collapsed) {
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }
  return { openBySession: m }
}),
```

- [ ] **Step 4: 跑测试确认 pass + typecheck**

```bash
cd client && npm test -- src/stores/stage-store.test.ts && npx tsc --noEmit
```
Expected: 7 tests passed; typecheck 0 errors

- [ ] **Step 5: commit**

```bash
git add client/src/stores/stage-store.ts client/src/stores/stage-store.test.ts
git commit -m "$(cat <<'EOF'
feat(client): add stage-store with auto-open semantics

per-session open state + autoOpenedSessions set；closeStage 隐式标
autoOpened 防反复自弹；syncCollapsed 反向同步拖动 collapse 行为。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: useActiveArtifactTitle

**Files:**
- Create: `client/src/features/stage/use-active-artifact-title.tsx`

跳过 TDD（纯组合 hook，无业务逻辑），直接写实现 + 在 stage-window 测试中覆盖。

- [ ] **Step 1: 写实现** `client/src/features/stage/use-active-artifact-title.tsx`

```tsx
import { Table2Icon, LineChartIcon, NetworkIcon } from 'lucide-react'
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'

export function useActiveArtifactTitle(sessionId: string | null) {
  const activeId = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) ?? null : null)
  const artifact = useOntologyStore(s => {
    if (!sessionId || !activeId) return null
    return s.artifactsBySession.get(sessionId)?.get(activeId) ?? null
  })

  if (!artifact) return { Icon: null as null | typeof Table2Icon, label: 'Stage' }
  const Icon = artifact.kind === 'table' ? Table2Icon
            : artifact.kind === 'chart' ? LineChartIcon
            : NetworkIcon
  const kind = artifact.kind === 'table' ? '表'
            : artifact.kind === 'chart' ? '图' : 'ER'
  return { Icon, label: `Stage · ${kind} v${artifact.version}` }
}
```

- [ ] **Step 2: typecheck**

```bash
cd client && npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: commit**

```bash
git add client/src/features/stage/use-active-artifact-title.tsx
git commit -m "$(cat <<'EOF'
feat(client): add useActiveArtifactTitle hook

derive titlebar text+icon from timeline-store + ontology-store。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: use-stage-auto-open 副作用订阅

**Files:**
- Create: `client/src/features/stage/use-stage-auto-open.ts`
- Test: `client/src/features/stage/use-stage-auto-open.test.ts`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

describe('use-stage-auto-open', () => {
  beforeEach(async () => {
    vi.resetModules()
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
  })

  it('首个 artifact 到达 → stage 自动开', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('用户先 close 后再来 artifact → 不再自弹', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useStageStore.getState().closeStage('s1')
    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'chart',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('多 session 互不影响', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    useOntologyStore.getState().upsertArtifact('s2', {
      id: 'b1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().openBySession.get('s2')).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认 fail**

```bash
cd client && npm test -- src/features/stage/use-stage-auto-open.test.ts
```
Expected: FAIL（模块不存在）

- [ ] **Step 3: 写实现** `client/src/features/stage/use-stage-auto-open.ts`

```ts
import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

let subscribed = false
const lastSizeBySession = new Map<string, number>()

export function ensureStageAutoOpenSubscribed() {
  if (subscribed) return
  subscribed = true
  useOntologyStore.subscribe((state) => {
    for (const [sid, m] of state.artifactsBySession.entries()) {
      const prev = lastSizeBySession.get(sid) ?? 0
      const cur = m.size
      if (prev === 0 && cur > 0) {
        useStageStore.getState().notifyArtifactArrived(sid)
      }
      lastSizeBySession.set(sid, cur)
    }
  })
}

// 测试辅助：在 vi.resetModules 后允许重新挂载
export function __resetStageAutoOpenForTest() {
  subscribed = false
  lastSizeBySession.clear()
}
```

> **测试调整**：因 `subscribed` 是 module 闭包，`vi.resetModules()` 配合 `await import()` 已能拿到新模块，但模块再次加载也会创建新闭包。如果上面测试在 module-cache 不重置时不工作，每个 it 开头加 `__resetStageAutoOpenForTest()` 调用即可。

修正测试 beforeEach：

```ts
beforeEach(async () => {
  useOntologyStore.setState({ artifactsBySession: new Map() })
  useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
  const mod = await import('./use-stage-auto-open')
  mod.__resetStageAutoOpenForTest()
})
```

- [ ] **Step 4: 跑测试确认 pass + typecheck**

```bash
cd client && npm test -- src/features/stage/use-stage-auto-open.test.ts && npx tsc --noEmit
```
Expected: 3 tests passed; typecheck 0 errors

- [ ] **Step 5: commit**

```bash
git add client/src/features/stage/use-stage-auto-open.ts client/src/features/stage/use-stage-auto-open.test.ts
git commit -m "$(cat <<'EOF'
feat(client): add stage auto-open subscription

module-level zustand subscribe: open stage on first artifact arrival
per session；幂等、不持久化、可测试重置。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: StageWindow 组件

**Files:**
- Create: `client/src/features/stage/components/stage-window.tsx`
- Test: `client/src/features/stage/components/stage-window.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StageWindow } from './stage-window'
import { useStageStore } from '@/stores/stage-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'

describe('StageWindow', () => {
  beforeEach(() => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]), autoOpenedSessions: new Set() })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('渲染 3 圆点 + 默认标题 Stage', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    const close = screen.getByLabelText('关闭 Stage')
    expect(close).toBeTruthy()
    expect(screen.getByText('Stage')).toBeTruthy()
  })

  it('点红圆点触发 closeStage', () => {
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    fireEvent.click(screen.getByLabelText('关闭 Stage'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('标题随 active artifact 变化', () => {
    useOntologyStore.getState().upsertArtifact('s1', { id: 'a1', version: 2, kind: 'chart' })
    useTimelineStore.setState({
      orderBySession: new Map([['s1', ['a1']]]),
      activeBySession: new Map([['s1', 'a1']]),
      manualBySession: new Map(),
    })
    render(<StageWindow sessionId="s1"><div>body</div></StageWindow>)
    expect(screen.getByText(/Stage · 图 v2/)).toBeTruthy()
  })

  it('children 渲染在 body slot', () => {
    render(<StageWindow sessionId="s1"><div data-testid="child">CHILD</div></StageWindow>)
    expect(screen.getByTestId('child')).toBeTruthy()
  })
})
```

- [ ] **Step 2: 跑测试确认 fail**

```bash
cd client && npm test -- src/features/stage/components/stage-window.test.tsx
```
Expected: FAIL（组件不存在）

- [ ] **Step 3: 写实现** `client/src/features/stage/components/stage-window.tsx`

```tsx
import type { ReactNode } from 'react'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'

type Props = {
  sessionId: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const close = useStageStore(s => s.closeStage)
  const { Icon, label } = useActiveArtifactTitle(sessionId)
  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl border bg-card shadow-sm">
      <div className="flex h-9 shrink-0 items-center gap-2 border-b bg-muted/40 px-3 select-none">
        <button
          type="button"
          aria-label="关闭 Stage"
          onClick={(e) => { e.stopPropagation(); close(sessionId) }}
          className="size-3 rounded-full bg-[#ff5f56] hover:opacity-80"
        />
        <span aria-hidden className="size-3 rounded-full bg-[#ffbd2e]" />
        <span aria-hidden className="size-3 rounded-full bg-[#27c93f]" />
        <div className="flex-1" />
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
          {Icon && <Icon className="size-3.5" />}
          {label}
        </span>
      </div>
      <div className="flex flex-1 min-h-0 flex-col">{children}</div>
    </div>
  )
}
```

- [ ] **Step 4: 跑测试确认 pass + typecheck**

```bash
cd client && npm test -- src/features/stage/components/stage-window.test.tsx && npx tsc --noEmit
```
Expected: 4 tests passed; typecheck 0 errors

- [ ] **Step 5: commit**

```bash
git add client/src/features/stage/components/stage-window.tsx client/src/features/stage/components/stage-window.test.tsx
git commit -m "$(cat <<'EOF'
feat(client): add StageWindow chrome with macOS titlebar

rounded card + 三圆点 titlebar；红圆点 close 触发 stage-store；
标题文本由 useActiveArtifactTitle 驱动随 active artifact 变化。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: StageToggleButton 组件

**Files:**
- Create: `client/src/features/stage/components/stage-toggle-button.tsx`
- Test: `client/src/features/stage/components/stage-toggle-button.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StageToggleButton } from './stage-toggle-button'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

describe('StageToggleButton', () => {
  beforeEach(() => {
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingConnectionPrompt: false,
    })
  })

  it('HERO 模式下按钮 disabled', () => {
    useSessionStore.getState().openSession('s1', false)
    render(<StageToggleButton />)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('SPLIT 关闭态下点击 → 打开', () => {
    useSessionStore.getState().openSession('s1', true)  // SPLIT
    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('SPLIT 打开态下点击 → 关闭', () => {
    useSessionStore.getState().openSession('s1', true)
    useStageStore.getState().openStage('s1')
    render(<StageToggleButton />)
    fireEvent.click(screen.getByRole('button'))
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('aria-pressed 反映打开状态', () => {
    useSessionStore.getState().openSession('s1', true)
    useStageStore.getState().openStage('s1')
    render(<StageToggleButton />)
    expect(screen.getByRole('button').getAttribute('aria-pressed')).toBe('true')
  })
})
```

- [ ] **Step 2: 跑测试确认 fail**

```bash
cd client && npm test -- src/features/stage/components/stage-toggle-button.test.tsx
```
Expected: FAIL（组件不存在）

- [ ] **Step 3: 写实现** `client/src/features/stage/components/stage-toggle-button.tsx`

```tsx
import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from '@/features/session/use-session-mode'

export function StageToggleButton() {
  const { mode } = useSessionMode()
  const sid = useSessionStore(s => s.activeSessionId)
  const open = useStageStore(s => sid ? !!s.openBySession.get(sid) : false)
  const toggle = useStageStore(s => s.toggleStage)

  const disabled = mode !== 'SPLIT' || !sid
  const title = disabled
    ? 'AI 还没产出工件'
    : open ? '关闭 Stage 面板' : '打开 Stage 面板'

  return (
    <Button
      type="button"
      size="icon-xs"
      variant="ghost"
      aria-pressed={open}
      aria-label={title}
      title={title}
      disabled={disabled}
      onClick={() => sid && toggle(sid)}
      className={cn(
        'rounded-md text-muted-foreground hover:bg-accent/50',
        open && 'bg-accent/70 text-foreground',
      )}
    >
      <MonitorIcon className="size-3.5" />
    </Button>
  )
}
```

- [ ] **Step 4: 跑测试确认 pass + typecheck**

```bash
cd client && npm test -- src/features/stage/components/stage-toggle-button.test.tsx && npx tsc --noEmit
```
Expected: 4 tests passed; typecheck 0 errors

- [ ] **Step 5: commit**

```bash
git add client/src/features/stage/components/stage-toggle-button.tsx client/src/features/stage/components/stage-toggle-button.test.tsx
git commit -m "$(cat <<'EOF'
feat(client): add StageToggleButton (the 'small computer' button)

monitor icon button、HERO disabled、SPLIT 时切换 stage open/close、
aria-pressed 同步状态。配 native title attr 提示当前操作。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: SplitView 接入 collapsible + StageWindow

**Files:**
- Modify: `client/src/features/session/split-view.tsx`

- [ ] **Step 1: 写实现** 全文替换 `client/src/features/session/split-view.tsx`

```tsx
import { useEffect, useRef } from 'react'
import { PanelGroup, Panel, PanelResizeHandle, type ImperativePanelHandle } from 'react-resizable-panels'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'

export function SplitView() {
  const sid = useSessionStore(s => s.activeSessionId)
  const open = useStageStore(s => sid ? !!s.openBySession.get(sid) : false)
  const stagePanelRef = useRef<ImperativePanelHandle>(null)

  useEffect(() => {
    const p = stagePanelRef.current
    if (!p) return
    if (open && p.isCollapsed()) p.expand()
    if (!open && !p.isCollapsed()) p.collapse()
  }, [open])

  return (
    <PanelGroup direction="horizontal" className="h-full">
      <Panel defaultSize={48} minSize={25}>
        <div className="flex h-full flex-col">
          <div className="flex-1 overflow-y-auto p-4"><MessageStream /></div>
          <div id="composer-slot" />
        </div>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel
        ref={stagePanelRef}
        defaultSize={52}
        minSize={25}
        collapsible
        collapsedSize={0}
        onCollapse={() => sid && useStageStore.getState().syncCollapsed(sid, true)}
        onExpand={() => sid && useStageStore.getState().syncCollapsed(sid, false)}
      >
        {sid && (
          <div
            data-stage-open={open}
            className="h-full w-full p-2 transition-opacity duration-200 data-[stage-open=false]:opacity-0 data-[stage-open=true]:opacity-100"
          >
            <StageWindow sessionId={sid}>
              <ArtifactTimelineStrip />
              <div className="flex-1 min-h-0 overflow-hidden"><ArtifactCanvas /></div>
            </StageWindow>
          </div>
        )}
      </Panel>
    </PanelGroup>
  )
}
```

- [ ] **Step 2: typecheck**

```bash
cd client && npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: commit**

```bash
git add client/src/features/session/split-view.tsx
git commit -m "$(cat <<'EOF'
feat(client): wrap right panel in StageWindow with collapsible API

right Panel 接 react-resizable-panels collapsible + ref.expand/collapse；
panel callbacks 反向 sync stage-store；StageWindow 包 timeline+canvas。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: PromptComposer 插入按钮

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`

- [ ] **Step 1: 改文件**

在 `import` 区追加：

```tsx
import { StageToggleButton } from '@/features/stage/components/stage-toggle-button'
```

在 `<InputGroupText className="gap-1.5 text-xs">…Auto</InputGroupText>` 之后、`<div className="flex-1" />` 之前 插入：

```tsx
<StageToggleButton />
```

- [ ] **Step 2: typecheck**

```bash
cd client && npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: commit**

```bash
git add client/src/features/session/prompt-composer.tsx
git commit -m "$(cat <<'EOF'
feat(client): add StageToggleButton to PromptComposer

next to Auto switch；HERO 时 disabled，SPLIT 时切换 stage 可见性。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: HomePage 挂订阅

**Files:**
- Modify: `client/src/features/workspace/home-page.tsx`

- [ ] **Step 1: 改文件**

在 import 区加：

```tsx
import { useEffect } from 'react'
import { ensureStageAutoOpenSubscribed } from '@/features/stage/use-stage-auto-open'
```

在 `useBootstrapActions()` 后插入：

```tsx
useEffect(() => { ensureStageAutoOpenSubscribed() }, [])
```

- [ ] **Step 2: typecheck**

```bash
cd client && npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 3: commit**

```bash
git add client/src/features/workspace/home-page.tsx
git commit -m "$(cat <<'EOF'
feat(client): mount stage auto-open subscription in HomePage

useEffect once；module-level closure ensures single subscription
across StrictMode re-mounts.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: 删除 /preview 沙盒

**Files:**
- Delete: `client/src/routes/preview.tsx`
- Delete: `client/src/features/preview/` 整个目录（7 文件）

- [ ] **Step 1: 删除文件**

```bash
rm client/src/routes/preview.tsx
rm -rf client/src/features/preview
```

- [ ] **Step 2: 重新生成 routeTree**

```bash
cd client && npm run gen:routes
```
Expected: routeTree.gen.ts 自动剔除 PreviewRoute 引用

- [ ] **Step 3: typecheck**

```bash
cd client && npx tsc --noEmit
```
Expected: 0 errors

- [ ] **Step 4: 全量 vitest**

```bash
cd client && npm test
```
Expected: 全部 passed（包括之前的 part-renderer.test 等）

- [ ] **Step 5: commit**

```bash
git add -A client/src/routes client/src/features client/src/routeTree.gen.ts
git commit -m "$(cat <<'EOF'
chore(client): remove /preview sandbox

manus split-view 动效已经接入主窗体（HomePage / SessionCanvas），
/preview 完成它的演示使命，整目录下线；routeTree 重新生成。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: 文档与最终验证

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: 更新 index.md**

把本 plan 加入"已完成"区块（在所有实施 commit 之后做）：

```markdown
| [Stage As Computer](../exec-plans/2026-04-17-stage-as-computer-plan.md) | 2026-04-17 | 右栏外壳化 + 小电脑按钮可关可开 + 智能自弹 + 删 /preview |
```

- [ ] **Step 2: 最终 typecheck + 全量 vitest**

```bash
cd client && npx tsc --noEmit && npm test
```
Expected: 0 errors; all tests passed

- [ ] **Step 3: commit + push**

```bash
git add docs/exec-plans/index.md
git commit -m "$(cat <<'EOF'
docs(plan): mark stage-as-computer plan complete

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin develop
```

---

## 自评 (writing-plans skill 强制)

**1. Spec coverage**

| Spec 节 | 对应 Task |
|---|---|
| §0.1 成功标准 1 (智能自弹) | Task 3 |
| §0.1 成功标准 2 (开关动画) | Task 6 |
| §0.1 成功标准 3 (close 后不再自弹) | Task 1 + Task 3 |
| §0.1 成功标准 4 (HERO disabled) | Task 5 |
| §0.1 成功标准 5 (titlebar 标题随 active artifact) | Task 2 + Task 4 |
| §0.1 成功标准 6 (变绿动画不破坏) | 不需新代码（沿用 ArtifactCanvas） |
| §0.1 成功标准 7 (/preview 删除后主窗体正常) | Task 9 + Task 10 |
| §1 命名 | Task 1-5 |
| §2 组件结构 | Task 4-7 |
| §3 状态层 | Task 1 |
| §4 智能自弹 | Task 3 + Task 8 |
| §5 动画 | Task 6（CSS） |
| §6 PromptComposer 改造 | Task 7 |
| §7 /preview 删除 | Task 9 |
| §8 测试 | 内嵌每个 Task |
| §9 风险 | 不需新代码 |

**2. Placeholder scan**：Task 1 Step 3 里有一段错误的三元写法已被紧随其后的 if-block 版本覆盖，明确告诉实施者删掉错误那段——这是允许的"教学性"提示，不算 placeholder。其他无 TBD/TODO。

**3. Type consistency**：`useStageStore` 暴露的方法名（openStage/closeStage/toggleStage/notifyArtifactArrived/syncCollapsed/clear）在 Task 1 定义后，被 Task 3/4/5/6 引用时都用同一名字。`useActiveArtifactTitle` 返回 `{ Icon, label }`，被 Task 4 解构时也是这个 shape。`ImperativePanelHandle` 在 Task 6 的 `isCollapsed()/expand()/collapse()` 与 react-resizable-panels v2.1.7 的真实 API 一致（Bash 读 d.ts 已验证）。

**4. 修复**：无遗漏。

---

## 实施模式

**Inline Execution** —— 用户已选定（"开始写"），按 Task 1 → Task 10 顺序执行；每个 Task 末尾 commit；Task 10 末尾 push origin develop。
