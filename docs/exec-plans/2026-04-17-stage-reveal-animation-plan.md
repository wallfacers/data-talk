# Stage 气泡展开动画 + 圆角内背景色区分 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在既有"Stage 即电脑"方案上叠加气泡式开/关动画（以小电脑按钮为圆心的 `clip-path: circle()` 过渡），并修复 light 模式下 Stage 圆角内部与外层背景同为纯白的层次缺失问题。

**Architecture:** `stage-store` 追加单字段 `revealOrigin: { x, y } | null`，由 `StageToggleButton` click 前 `getBoundingClientRect` 测量按钮中心视口坐标写入。`SplitView` 用 `useLayoutEffect` 把视口坐标换算成 stage 容器本地坐标 + 计算覆盖半径，driving `clip-path` 过渡；`translateX(100%)` 改由 `translateLatched` state 做双段驱动（打开立即就位、关闭延迟 400ms 后退出视口）。`StageWindow` 的 `bg-card` 替换为 `bg-muted`，修复同色问题。零新依赖。

**Tech Stack:** React 19 + TypeScript + Zustand + Tailwind CSS（项目既有），vitest + @testing-library/react。

**Spec:** [`docs/product-specs/2026-04-17-stage-reveal-animation-design.md`](../product-specs/2026-04-17-stage-reveal-animation-design.md)

---

## 文件映射

| 文件 | 操作 | 职责 |
|---|---|---|
| `client/src/stores/stage-store.ts` | 修改 | 追加 `revealOrigin` 字段 + `setRevealOrigin` 方法 |
| `client/src/stores/stage-store.test.ts` | 修改 | 追加 `revealOrigin` 相关单测 |
| `client/src/features/stage/components/stage-toggle-button.tsx` | 修改 | 加 `buttonRef`，click 前同步测量并写入 origin |
| `client/src/features/stage/components/stage-toggle-button.test.tsx` | 修改 | 追加"点击后 revealOrigin 被写入"单测 |
| `client/src/features/stage/components/stage-window.tsx` | 修改 | `bg-card` → `bg-muted` |
| `client/src/features/session/split-view.tsx` | 修改 | 新增 `stageContainerRef` + `clipGeom` state + `translateLatched` state + clip-path 渲染 |
| `client/src/features/session/split-view.test.tsx` | 新建 | 集成测：clip-path 渲染 + translateLatched 双段 |
| `client/src/styles/globals.css` | 修改 | 追加 `@media (prefers-reduced-motion: reduce)` 规则 |
| `docs/exec-plans/index.md` | 修改 | 登记本计划 |

共 6 改 + 1 新增文件 + 1 索引登记。

---

## Task 1：stage-store 追加 `revealOrigin` 字段

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Test: `client/src/stores/stage-store.test.ts`

- [ ] **Step 1：追加失败测试**

在 `client/src/stores/stage-store.test.ts` 文件末尾的 `describe` 块**内部**（最后一个 `it` 之后、`})` 之前）追加：

```ts
  it('setRevealOrigin 写入 revealOrigin 字段', () => {
    const { setRevealOrigin } = useStageStore.getState()
    setRevealOrigin({ x: 100, y: 200 })
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 100, y: 200 })
  })

  it('setRevealOrigin(null) 清空 revealOrigin', () => {
    const { setRevealOrigin } = useStageStore.getState()
    setRevealOrigin({ x: 100, y: 200 })
    setRevealOrigin(null)
    expect(useStageStore.getState().revealOrigin).toBeNull()
  })

  it('closeStage 不触碰 revealOrigin（供关闭动画复用）', () => {
    const { setRevealOrigin, closeStage } = useStageStore.getState()
    setRevealOrigin({ x: 50, y: 50 })
    closeStage('s1')
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 50, y: 50 })
  })

  it('notifyArtifactArrived 不触碰 revealOrigin', () => {
    const { setRevealOrigin, notifyArtifactArrived } = useStageStore.getState()
    setRevealOrigin({ x: 50, y: 50 })
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 50, y: 50 })
  })
```

同时把既有的 `beforeEach` 第 6 行的 `setState` 初始值补上 `revealOrigin: null`，让每个测试用例在干净状态起步：

```ts
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
    })
  })
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd client && npx vitest run src/stores/stage-store.test.ts
```

Expected：前 4 条新测试 FAIL，原因 `useStageStore.getState().setRevealOrigin is not a function`。

- [ ] **Step 3：实现 store 扩展**

编辑 `client/src/stores/stage-store.ts`，在 `StageState` 类型里追加两行（放在 `maximizedBySession` 后、`openStage` 前）：

```ts
type RevealOrigin = { x: number; y: number }

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  maximizedBySession: Map<string, boolean>
  revealOrigin: RevealOrigin | null

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  toggleMaximized: (sessionId: string) => void
  setRevealOrigin: (origin: RevealOrigin | null) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void
}
```

然后在 `create<StageState>(...)` 的初始对象里追加：

```ts
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  maximizedBySession: new Map(),
  revealOrigin: null,
```

以及在方法区追加（插入到 `toggleMaximized` 和 `notifyArtifactArrived` 之间）：

```ts
  setRevealOrigin: (origin) => set({ revealOrigin: origin }),
```

- [ ] **Step 4：运行测试确认通过**

```bash
cd client && npx vitest run src/stores/stage-store.test.ts
```

Expected：全部测试 PASS（包含新加的 4 条 + 既有 7 条）。

- [ ] **Step 5：提交**

```bash
cd .. && git add client/src/stores/stage-store.ts client/src/stores/stage-store.test.ts
git commit -m "feat(stage-store): add revealOrigin field for bubble animation"
```

---

## Task 2：StageToggleButton 测量并写入 origin

**Files:**
- Modify: `client/src/features/stage/components/stage-toggle-button.tsx`
- Test: `client/src/features/stage/components/stage-toggle-button.test.tsx`

- [ ] **Step 1：追加失败测试**

在 `client/src/features/stage/components/stage-toggle-button.test.tsx` 文件末尾 `describe` 块**内部**追加：

```tsx
  it('点击后 revealOrigin 被写入按钮中心视口坐标', () => {
    useSessionStore.getState().openSession('s1', true)
    render(<StageToggleButton />)
    const btn = screen.getByRole('button')
    // jsdom 默认 getBoundingClientRect 返回全零，这里 stub 成已知矩形
    btn.getBoundingClientRect = () => ({
      left: 100, top: 200, right: 120, bottom: 220,
      width: 20, height: 20, x: 100, y: 200, toJSON: () => ({}),
    }) as DOMRect
    fireEvent.click(btn)
    expect(useStageStore.getState().revealOrigin).toEqual({ x: 110, y: 210 })
  })
```

同时把 `beforeEach` 里的 `useStageStore.setState({...})` 补上 `revealOrigin: null`：

```tsx
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
    })
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd client && npx vitest run src/features/stage/components/stage-toggle-button.test.tsx
```

Expected：新加的测试 FAIL —— `revealOrigin` 仍为 `null`。

- [ ] **Step 3：实现按钮 ref + 测量逻辑**

编辑 `client/src/features/stage/components/stage-toggle-button.tsx`，完整替换为：

```tsx
import { useRef } from 'react'
import { MonitorIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from '@/features/session/use-session-mode'

export function StageToggleButton() {
  const btnRef = useRef<HTMLButtonElement>(null)
  const sid = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const { mode } = useSessionMode()
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const openStage = useStageStore((s) => s.openStage)
  const toggle = useStageStore((s) => s.toggleStage)
  const setRevealOrigin = useStageStore((s) => s.setRevealOrigin)

  const title = open ? '关闭 Stage 面板' : '打开 Stage 面板'

  function handleClick() {
    if (!sid) return
    const rect = btnRef.current?.getBoundingClientRect()
    if (rect) {
      setRevealOrigin({
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2,
      })
    }
    if (mode === 'HERO') {
      enterSplit(sid)
      openStage(sid)
    } else {
      toggle(sid)
    }
  }

  return (
    <Button
      ref={btnRef}
      type="button"
      size="icon-xs"
      variant="ghost"
      aria-pressed={open}
      aria-label={title}
      title={title}
      disabled={!sid}
      onClick={handleClick}
      className={cn(
        'cursor-pointer rounded-md text-black hover:bg-accent/50 disabled:cursor-not-allowed disabled:opacity-50 dark:text-white',
        open && 'bg-accent/70',
      )}
    >
      <MonitorIcon className="size-3.5" />
    </Button>
  )
}
```

- [ ] **Step 4：运行测试确认通过**

```bash
cd client && npx vitest run src/features/stage/components/stage-toggle-button.test.tsx
```

Expected：全部 PASS（新加 1 条 + 既有 5 条）。

- [ ] **Step 5：类型检查**

```bash
cd client && npm run typecheck
```

Expected：0 error。

- [ ] **Step 6：提交**

```bash
cd .. && git add client/src/features/stage/components/stage-toggle-button.tsx client/src/features/stage/components/stage-toggle-button.test.tsx
git commit -m "feat(stage-toggle): capture button center as reveal origin on click"
```

---

## Task 3：SplitView 接入 clip-path 动画 + translateLatched 双段

**Files:**
- Modify: `client/src/features/session/split-view.tsx`
- Test: `client/src/features/session/split-view.test.tsx`（新建）

- [ ] **Step 1：写失败测试（新建文件）**

创建 `client/src/features/session/split-view.test.tsx`，完整内容：

```tsx
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, act } from '@testing-library/react'
import { SplitView } from './split-view'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'

const DURATION = 400

function findStagePanel(container: HTMLElement): HTMLElement {
  const el = container.querySelector('[data-stage-panel]') as HTMLElement | null
  if (!el) throw new Error('stage panel not found')
  return el
}

describe('SplitView clip-path reveal', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
    })
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingConnectionPrompt: false,
    })
    useChatPartsStore.setState({ partsBySession: new Map() })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('open=false 时 stage 容器 clip-path 为 circle(0px ...)', () => {
    const { container } = render(<SplitView />)
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toMatch(/circle\(0px/)
  })

  it('open=true + revealOrigin 有值 → clip-path 用 origin 坐标换算', () => {
    useStageStore.setState({
      openBySession: new Map([['s1', true]]),
      revealOrigin: { x: 100, y: 300 },
    })
    const { container } = render(<SplitView />)
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toMatch(/circle\(\d+(\.\d+)?px at -?\d+(\.\d+)?px -?\d+(\.\d+)?px\)/)
    // 半径必定 > 0（使用了有效 origin + jsdom 默认 rect 零尺寸，公式仍返回非负数）
    const match = panel.style.clipPath.match(/circle\(([\d.]+)px/)
    expect(match).not.toBeNull()
    expect(Number(match![1])).toBeGreaterThanOrEqual(0)
  })

  it('open=true + revealOrigin=null → clip-path fallback 到 circle(2000px at 100% 100%)', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container } = render(<SplitView />)
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toBe('circle(2000px at 100% 100%)')
  })

  it('打开时 translateX 立即为 0，关闭后 DURATION ms 才切到 100%', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container, rerender } = render(<SplitView />)
    let panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0px)')

    // 关闭
    act(() => {
      useStageStore.setState({ openBySession: new Map([['s1', false]]) })
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0px)')  // 仍未退出视口

    // 等 400ms 后
    act(() => {
      vi.advanceTimersByTime(DURATION)
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(100%)')
  })
})
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd client && npx vitest run src/features/session/split-view.test.tsx
```

Expected：FAIL —— 因为 `SplitView` 尚未渲染 `data-stage-panel` / 正确 clip-path / translateLatched 行为。

- [ ] **Step 3：改 SplitView 实现**

编辑 `client/src/features/session/split-view.tsx`，**完整替换**为：

```tsx
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { DatabaseIcon } from 'lucide-react'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'
import { StageWindow } from '@/features/stage/components/stage-window'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { ChatHeader } from './chat-header'

const DURATION = 400
const EASE = 'cubic-bezier(0.32, 0.72, 0.24, 1)'

export function SplitView() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const maximized = useStageStore((s) => (sid ? !!s.maximizedBySession.get(sid) : false))
  const revealOrigin = useStageStore((s) => s.revealOrigin)
  const hasMessages = useChatPartsStore((s) => {
    const parts = sid ? s.partsBySession.get(sid) : undefined
    return parts ? parts.size > 0 : false
  })

  const stageContainerRef = useRef<HTMLDivElement>(null)
  const [clipGeom, setClipGeom] = useState<{ r: number; x: number; y: number } | null>(null)
  // 关闭态：stage 退出视口（translateX 100%）；打开时：立即就位（0）；
  // 关闭动画结束后再切回 100%，避免遮挡事件
  const [translateLatched, setTranslateLatched] = useState<boolean>(!open)

  useLayoutEffect(() => {
    if (!revealOrigin) { setClipGeom(null); return }
    const rect = stageContainerRef.current?.getBoundingClientRect()
    if (!rect) return
    const localX = revealOrigin.x - rect.left
    const localY = revealOrigin.y - rect.top
    const r = Math.max(
      Math.hypot(localX, localY),
      Math.hypot(rect.width - localX, localY),
      Math.hypot(localX, rect.height - localY),
      Math.hypot(rect.width - localX, rect.height - localY),
    )
    setClipGeom({ r, x: localX, y: localY })
  }, [revealOrigin, open])

  useEffect(() => {
    if (open) {
      setTranslateLatched(false)
      return
    }
    const t = setTimeout(() => setTranslateLatched(true), DURATION)
    return () => clearTimeout(t)
  }, [open])

  // chat：width 从 100% 收缩到 46%（maximized 时归零）
  const chatWidth = maximized ? '0%' : open ? '46%' : '100%'
  // stage：maximized 时 100% 宽；否则固定 54%
  const stageWidth = maximized ? '100%' : '54%'
  const stageTransform = translateLatched ? 'translateX(100%)' : 'translateX(0px)'

  const clipPath = clipGeom
    ? `circle(${open ? clipGeom.r : 0}px at ${clipGeom.x}px ${clipGeom.y}px)`
    : `circle(${open ? 2000 : 0}px at 100% 100%)`

  const transition = `clip-path ${DURATION}ms ${EASE}, width ${DURATION}ms ${EASE}`

  const stageStyle: CSSProperties = {
    position: 'absolute',
    top: 0, right: 0, bottom: 0,
    width: stageWidth,
    transform: stageTransform,
    clipPath,
    transition,
    willChange: 'clip-path, transform',
  }

  return (
    <div className="relative h-full overflow-hidden">
      {/* chat 列 */}
      <div
        style={{
          position: 'absolute',
          top: 0, left: 0, bottom: 0,
          width: chatWidth,
          transition: `width ${DURATION}ms ${EASE}`,
          overflow: 'hidden',
          willChange: 'width',
        }}
      >
        {hasMessages ? (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex-1 overflow-y-auto px-2 py-4">
              <div className="w-full max-w-3xl mx-auto">
                <MessageStream />
              </div>
            </div>
            <div className="px-2 pb-4">
              <div id="composer-slot" className="w-full max-w-3xl mx-auto" />
            </div>
          </div>
        ) : (
          <div className="flex h-full flex-col">
            <ChatHeader />
            <div className="flex flex-1 flex-col items-center justify-center px-2">
              <div className="flex flex-col items-center gap-3 text-center">
                <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
                  <DatabaseIcon className="size-5" />
                </div>
                <h1 className="text-xl font-semibold tracking-tight">DataTalk</h1>
                <p className="text-sm text-muted-foreground">用自然语言和你的数据库对话</p>
              </div>
              <div className="mt-8 w-full max-w-3xl">
                <div id="composer-slot" />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* stage 列：clip-path 气泡 + translateLatched 双段 */}
      <div ref={stageContainerRef} data-stage-panel style={stageStyle}>
        <div className="h-full w-full p-2">
          <StageWindow sessionId={sid ?? undefined}>
            {sid && <ArtifactTimelineStrip />}
            {sid && (
              <div className="flex-1 min-h-0 overflow-hidden">
                <ArtifactCanvas />
              </div>
            )}
          </StageWindow>
        </div>
      </div>
    </div>
  )
}
```

关键变更：
- 新增 `stageContainerRef / clipGeom / translateLatched` 三个状态
- `useLayoutEffect` 测量 stage 容器矩形 + 半径（深度使用 `revealOrigin` 的视口坐标换算为本地坐标）
- `useEffect` 驱动 `translateLatched` 双段：open=true → 立即 false；open=false → 400ms 后 true
- Stage 容器 `<div>` 加 `ref + data-stage-panel`，style 直接内联（不再用既有的 `translateX` 动画，改由 `translateLatched` 切换）
- clip-path 作为核心动画走 transition；transform 不走 transition（瞬切）

- [ ] **Step 4：运行测试确认通过**

```bash
cd client && npx vitest run src/features/session/split-view.test.tsx
```

Expected：4 条测试全部 PASS。

- [ ] **Step 5：回归跑整个 vitest + typecheck**

```bash
cd client && npm run typecheck && npm run test
```

Expected：typecheck 0 error；所有既有测试（含 stage-store、stage-toggle-button、stage-window、session 其他测试）都 PASS。

- [ ] **Step 6：提交**

```bash
cd .. && git add client/src/features/session/split-view.tsx client/src/features/session/split-view.test.tsx
git commit -m "feat(split-view): clip-path bubble reveal animation for stage panel"
```

---

## Task 4：StageWindow 配色 `bg-card` → `bg-muted`

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`

此改动纯视觉，不走 TDD（`bg-card` vs `bg-muted` 在 jsdom 下无法断言像素色值；既有 `stage-window.test.tsx` 已覆盖交互行为，本步仅需确保它们仍 PASS）。

- [ ] **Step 1：改 className**

编辑 `client/src/features/stage/components/stage-window.tsx` 第 27 行：

```tsx
// 旧
<div className="flex h-full w-full flex-col overflow-hidden rounded-2xl border bg-card shadow-xl ring-1 ring-black/5 dark:ring-white/10">

// 新
<div className="flex h-full w-full flex-col overflow-hidden rounded-2xl border bg-muted shadow-xl ring-1 ring-black/5 dark:ring-white/10">
```

- [ ] **Step 2：跑 stage-window 既有测试确认不破坏**

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx
```

Expected：既有测试全部 PASS。

- [ ] **Step 3：类型检查**

```bash
cd client && npm run typecheck
```

Expected：0 error。

- [ ] **Step 4：提交**

```bash
cd .. && git add client/src/features/stage/components/stage-window.tsx
git commit -m "style(stage-window): bg-card -> bg-muted to separate from outer background"
```

---

## Task 5：追加 reduced-motion CSS

**Files:**
- Modify: `client/src/styles/globals.css`

- [ ] **Step 1：追加媒体查询**

编辑 `client/src/styles/globals.css`，在文件**末尾**追加：

```css
/* Reduced motion: Stage bubble reveal 降级为瞬切 */
@media (prefers-reduced-motion: reduce) {
  [data-stage-panel] {
    transition: none !important;
  }
}
```

- [ ] **Step 2：类型检查 + 测试回归**

```bash
cd client && npm run typecheck && npm run test
```

Expected：typecheck 0 error；全部测试 PASS。

- [ ] **Step 3：提交**

```bash
cd .. && git add client/src/styles/globals.css
git commit -m "style(globals): disable stage transitions under prefers-reduced-motion"
```

---

## Task 6：手动验收

无自动化，依次操作确认。**每条不通过则停下排查，不继续**。

- [ ] **Step 1：启动 dev**

```bash
cd client && npm run dev
```

- [ ] **Step 2：按清单验收**

打开浏览器（或 `npm run tauri dev`）后：

- [ ] 先建/选一个 session，处于 HERO → 点 composer 里的小电脑按钮 → stage 从按钮位置圆形膨胀展开，chat 同步让位到 46%
- [ ] 在 SPLIT + open 态再次点按钮 → stage 从按钮位置收缩回零
- [ ] 点 titlebar X 关闭 → stage 仍从"上一次按钮位置"为圆心收缩（非 X 按钮位置），属预期
- [ ] 首次 artifact 自动弹出：
  - 场景 A（从未点过按钮）→ stage 从右侧滑入（fallback 行为）
  - 场景 B（点过按钮一次再关闭后）→ stage 从上次按钮位置气泡膨胀
- [ ] 切换 session → stage 状态独立保持
- [ ] Stage 圆角内部 light 模式微灰 vs 外层纯白；dark 模式明显亮一档 vs 外层近黑
- [ ] DevTools Rendering → Emulate CSS → `prefers-reduced-motion: reduce` 开启 → 切换 stage 即时无动画
- [ ] maximized → 退出 maximized 时 stage 宽度平滑缩回 54%，clip-path 不产生气泡

- [ ] **Step 3：停 dev**

`Ctrl+C` 停 `npm run dev`。

---

## Task 7：登记到 exec-plans 索引

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1：更新索引**

编辑 `docs/exec-plans/index.md`，在「活跃计划」或「已完成计划」表中追加一行（完成后归入已完成；此处假设完成）：

```md
| [Stage Reveal Animation](./2026-04-17-stage-reveal-animation-plan.md) | 2026-04-17 | Stage 气泡式 clip-path 开/关动画 + 圆角内 bg-muted 色差修复 |
```

放置位置：在 `[Stage As Computer]` 行**之前**（日期相同，按相关性排在一起）。

- [ ] **Step 2：提交**

```bash
git add docs/exec-plans/index.md docs/exec-plans/2026-04-17-stage-reveal-animation-plan.md
git commit -m "docs(exec-plan): register stage reveal animation plan"
```

---

## 全局验收

完成 Task 1–7 后：

- [ ] `cd client && npm run typecheck` → 0 error
- [ ] `cd client && npm run test` → 全绿
- [ ] 手动验收清单（Task 6 Step 2）全部 ✅
- [ ] `git log --oneline -10` 看到 6 个 feat/style/docs commit（Task 1–5 + Task 7）

---

## 回滚策略

每个 Task 是独立 commit，出问题可 `git revert <sha>` 单步回滚。Task 3（SplitView）改动最大，若测试不稳定可单独 revert 后重做。
