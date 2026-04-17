# Stage 即电脑：右栏可关可开 + 主输入框小电脑按钮 设计

> 日期：2026-04-17
> 状态：已完成
> 上一份前置 spec：`2026-04-16-manus-split-view-design.md`（本 spec 在其 §4.6 右栏 = 时间线胶囊条 + 主画布 的基础上做"外壳化 + 可关可开"增量改造）

---

## 0. 摘要

把 SPLIT 态右侧 stage（`ArtifactTimelineStrip` + `ArtifactCanvas`）外面套一层"电脑"风格的圆角窗体外壳（带 macOS 三圆点 titlebar），并赋予它**可关闭、可打开**的能力：

1. 用户点 titlebar 的红圆点 → 右栏平滑折叠到 0，左侧 chat 扩展到全宽
2. 用户点主输入框 Auto 开关旁的小电脑按钮 → 右栏弹回原宽度
3. 智能自弹：每个 session 内首个 artifact 到达时自动弹出一次；用户主动关闭后，本 session 内不再自弹

同时把已经完成历史使命的 `/preview` 沙盒（`routes/preview.tsx` + `features/preview/*`）整体删除。

### 0.1 成功标准

1. SPLIT 态下默认 stage 关闭；session 内**第一次** `ontology.updated{op:'upsert'}` 携带新 artifact 时，stage 自动展开（含 react-resizable-panels 默认 collapsible 过渡 + 180ms opacity 淡入）
2. 用户点 titlebar 红圆点 → stage 关闭（左 chat 扩展到 100% 宽度），点小电脑按钮 → stage 重新展开到 52% 默认宽度
3. 用户主动关闭后，再产出新的 artifact 不会重新弹出（只有时间线胶囊条隐藏在折叠的右栏里，等用户主动开启时才看到）
4. HERO/NOSESS 模式下小电脑按钮 disabled + tooltip 提示
5. titlebar 标题文本随 active artifact 变化：表 v1 → 图 v2 → 图 v3
6. 现有的"原地变绿"动画（追问场景）在 stage 打开 / 折叠状态下都不被破坏；切换 session 后 stage 状态独立保持
7. `/preview` 路由及 `features/preview/*` 删除完后，主窗体（`/` 路由）的 HERO/SPLIT、clip-path 裂开动画、消息流、artifact 时间线/画布、ChartArtifact 变色 全部正常

### 0.2 非目标

- 三圆点的"最小化"（黄）和"最大化"（绿）功能 —— MVP 仅红圆点真关闭，黄绿装饰位
- stage 状态持久化到 SQLite（沿用 manus spec §1.2"hero/split 是前端本地状态"原则）
- "电脑屏幕拟物"风格（外壳/底座/屏幕反光），仅 macOS 风格圆角 + titlebar
- stage 浮动 / Dock 化（关闭后并入右下角小标）
- stage 拖拽脱离右栏成独立 floating window
- 引入 framer-motion 或额外动画库

---

## 1. 命名 & 心智模型

| 概念 | 代码命名 | 说明 |
|---|---|---|
| 用户口中的"电脑窗体" | `Stage` / `StageWindow` | 沿用 manus spec §4.6 既有的 `StageColumn` 概念，外壳化后叫 `StageWindow` |
| 主输入框的"小电脑按钮" | `StageToggleButton` | 图标用 lucide `MonitorIcon` |
| 状态层 | `useStageStore` | 与 `timeline-store`、`ontology-store` 平级 |
| 智能自弹订阅 | `ensureStageAutoOpenSubscribed` | module 顶层闭包保证只 subscribe 一次 |

为何不叫 `Computer`：`Stage` 是 manus spec 已有词汇（StageColumn / ArtifactCanvas 都是 stage 的一部分），延续概念；`Computer` 在代码里容易和"宿主机/Tauri 进程"等概念混淆。"电脑"留在 UI 文案与 commit message 里描述意图。

---

## 2. 高层组件结构

### 2.1 新增 / 改造文件

```
client/src/
  features/
    stage/                                 ← 新增整个目录
      components/
        stage-window.tsx                   ← 圆角外壳 + macOS titlebar
        stage-toggle-button.tsx            ← 主输入框里的小电脑按钮
      use-stage-auto-open.ts               ← module 顶层 subscribe ontology-store
    session/
      split-view.tsx                       ← 改：右 Panel 用 collapsible + StageWindow
      prompt-composer.tsx                  ← 改：Auto 旁插 StageToggleButton
    workspace/
      home-page.tsx                        ← 改：useEffect 挂订阅
  stores/
    stage-store.ts                         ← 新增
  routes/
    preview.tsx                            ← 删
  features/preview/                        ← 删整个目录（7 文件）
```

### 2.2 `<StageWindow>` 形态

```
┌───────────────────────────────────────┐ ← rounded-xl border shadow-sm
│ ● ● ●   Stage · 表 v1                │ ← h-9 titlebar, border-b, bg-muted/40
├───────────────────────────────────────┤
│ [art1·表][art2·图]                    │ ← 沿用 ArtifactTimelineStrip
│ ┌─────────────────────────────────┐   │
│ │ ArtifactCanvas（沿用，不改）    │   │
│ │                                 │   │
│ └─────────────────────────────────┘   │
└───────────────────────────────────────┘
```

视觉规格：

- 外壳：`rounded-xl border bg-card shadow-sm overflow-hidden`
- titlebar：`h-9 px-3 border-b bg-muted/40 flex items-center gap-2 select-none`
- 三圆点：每个 `size-3 rounded-full`，依次 `bg-[#ff5f56]` / `bg-[#ffbd2e]` / `bg-[#27c93f]`，通过 hover 状态显示鼠标 pointer
- 红圆点 `onClick` 调 `useStageStore.getState().closeStage(sessionId)`，并阻止事件冒泡
- 黄/绿圆点 `cursor-default` + `aria-hidden`（占位、未来扩展）
- 标题文本：`text-xs text-muted-foreground` + 图标（active 是 table → `Table2Icon`、chart → `LineChartIcon`、erd → `NetworkIcon`），格式 `Stage · 表 v1`；无 active artifact 时仅 `Stage`
- StageWindow 主体：`flex-1 flex flex-col min-h-0`，内部直接 children slot

```tsx
type Props = {
  sessionId: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const close = useStageStore(s => s.closeStage)
  const title = useActiveArtifactTitle(sessionId)  // 见 §3.4
  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl border bg-card shadow-sm">
      <div className="flex h-9 shrink-0 items-center gap-2 border-b bg-muted/40 px-3 select-none">
        <button
          aria-label="关闭 Stage"
          onClick={(e) => { e.stopPropagation(); close(sessionId) }}
          className="size-3 rounded-full bg-[#ff5f56] hover:opacity-80"
        />
        <span aria-hidden className="size-3 rounded-full bg-[#ffbd2e] cursor-default" />
        <span aria-hidden className="size-3 rounded-full bg-[#27c93f] cursor-default" />
        <div className="flex-1" />
        <span className="text-xs text-muted-foreground inline-flex items-center gap-1">
          {title}
        </span>
      </div>
      <div className="flex-1 min-h-0 flex flex-col">{children}</div>
    </div>
  )
}
```

### 2.3 `<StageToggleButton>`

嵌在 `prompt-composer.tsx` 现有 `<Switch> Auto` 与 `<div className="flex-1" />` spacer 之间：

```tsx
import { MonitorIcon } from 'lucide-react'
import { useStageStore } from '@/stores/stage-store'
import { useSessionMode } from '@/features/session/use-session-mode'
import { useSessionStore } from '@/stores/session-store'

export function StageToggleButton() {
  const { mode } = useSessionMode()
  const sid = useSessionStore(s => s.activeSessionId)
  const open = useStageStore(s => sid ? !!s.openBySession.get(sid) : false)
  const toggle = useStageStore(s => sid ? () => s.toggleStage(sid) : null)

  const disabled = mode !== 'SPLIT' || !sid || !toggle
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          size="icon-xs"
          variant="ghost"
          aria-pressed={open}
          aria-label={open ? '关闭 Stage 面板' : '打开 Stage 面板'}
          disabled={disabled}
          onClick={() => toggle?.()}
          className={cn(
            'rounded-md text-muted-foreground hover:bg-accent/50',
            open && 'bg-accent/70 text-foreground',
          )}
        >
          <MonitorIcon className="size-3.5" />
        </Button>
      </TooltipTrigger>
      <TooltipContent side="top">
        {disabled ? 'AI 还没产出工件' : open ? '关闭 Stage 面板' : '打开 Stage 面板'}
      </TooltipContent>
    </Tooltip>
  )
}
```

### 2.4 `<SplitView>` 改造

```tsx
export function SplitView() {
  const sid = useSessionStore(s => s.activeSessionId)
  const open = useStageStore(s => sid ? !!s.openBySession.get(sid) : false)
  const stagePanelRef = useRef<ImperativePanelHandle>(null)

  // open 状态 → panel collapse/expand 命令
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
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50 data-[panel-collapsed]:hidden" />
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
            className="h-full w-full p-2 transition-opacity duration-180 data-[stage-open=false]:opacity-0 data-[stage-open=true]:opacity-100"
          >
            <StageWindow sessionId={sid}>
              <ArtifactTimelineStrip />
              <div className="flex-1 overflow-hidden"><ArtifactCanvas /></div>
            </StageWindow>
          </div>
        )}
      </Panel>
    </PanelGroup>
  )
}
```

注意：

- `p-2` 给电脑外壳留呼吸感（与 SidebarInset 的 inset margin 风格一致）
- `<PanelResizeHandle>` 在右 panel collapsed 时隐藏（避免出现"看似可拖但拖了没用"）
- 真正的状态源是 `useStageStore.openBySession`；`onCollapse/onExpand` 回调仅作"事实校准"（用户拖动 handle 跨过 minSize 触发的 collapse 也能反向同步到 store）

---

## 3. 状态层 `stage-store`

### 3.1 形态

```ts
// client/src/stores/stage-store.ts
import { create } from 'zustand'

type StageState = {
  openBySession: Map<string, boolean>      // 用户当前是否打开
  autoOpenedSessions: Set<string>          // 已经为该 session 自动弹过一次

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  notifyArtifactArrived: (sessionId: string) => void  // §4 订阅器调用
  syncCollapsed: (sessionId: string, collapsed: boolean) => void  // SplitView 反向同步
  clear: (sessionId: string) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),

  openStage: (sid) => set(s => {
    const m = new Map(s.openBySession); m.set(sid, true)
    return { openBySession: m }
  }),

  closeStage: (sid) => set(s => {
    const m = new Map(s.openBySession); m.set(sid, false)
    // 用户主动关，标记为"已自弹过"，禁止后续自弹
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  toggleStage: (sid) => {
    const cur = !!get().openBySession.get(sid)
    cur ? get().closeStage(sid) : get().openStage(sid)
  },

  notifyArtifactArrived: (sid) => set(s => {
    if (s.autoOpenedSessions.has(sid)) return s
    if (s.openBySession.get(sid)) return s
    const m = new Map(s.openBySession); m.set(sid, true)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  syncCollapsed: (sid, collapsed) => set(s => {
    const cur = s.openBySession.get(sid)
    const next = !collapsed
    if (cur === next) return s
    const m = new Map(s.openBySession); m.set(sid, next)
    // 拖动 handle 触发的 collapse 视同主动关，标 autoOpened，避免后续新 artifact 反复自弹
    const a = collapsed ? new Set(s.autoOpenedSessions).add(sid) : s.autoOpenedSessions
    return { openBySession: m, autoOpenedSessions: a }
  }),

  clear: (sid) => set(s => {
    const m = new Map(s.openBySession); m.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),
}))
```

### 3.2 不变量

- `closeStage` 必定隐含 `markAutoOpened`（用户已表态"我看过了"）
- `notifyArtifactArrived` 是幂等的（同 session 多次只第一次生效）
- `openStage`（用户手动）**不**清空 `autoOpenedSessions`（重新关后仍不自弹，符合"尊重用户决定"）
- 不持久化：刷新 / Tauri 重启后所有 session 回到"未自弹"状态

### 3.3 与现有 store 的关系

| store | 职责 | 与 stage-store 关系 |
|---|---|---|
| `session-store` | activeSessionId / mode | stage-store 不读，UI 层组合 |
| `ontology-store` | artifact 数据 | stage-store 通过订阅观察新 artifact |
| `timeline-store` | active artifact + 顺序 | 完全正交 |
| `chat-parts-store` | 消息流 | 无关 |

### 3.4 派生：active artifact 标题

```ts
// features/stage/use-active-artifact-title.ts
export function useActiveArtifactTitle(sessionId: string): ReactNode {
  const activeId = useTimelineStore(s => s.activeBySession.get(sessionId) ?? null)
  const artifact = useOntologyStore(s => {
    const m = s.artifactsBySession.get(sessionId)
    return activeId && m ? m.get(activeId) ?? null : null
  })
  if (!artifact) return <span>Stage</span>
  const Icon = artifact.kind === 'table' ? Table2Icon
            : artifact.kind === 'chart' ? LineChartIcon
            : NetworkIcon
  const label = artifact.kind === 'table' ? '表'
              : artifact.kind === 'chart' ? '图' : 'ER'
  return <><Icon className="size-3.5" /> Stage · {label} v{artifact.version}</>
}
```

---

## 4. 智能自弹：副作用接线

### 4.1 触发模式：module-level zustand subscribe

```ts
// client/src/features/stage/use-stage-auto-open.ts
import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'
import type { Artifact } from '@/services/channel/event-reducer'

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
```

挂载点：`HomePage` 顶部 `useEffect(() => ensureStageAutoOpenSubscribed(), [])`。module 顶层 `subscribed` 闭包保证全局只挂一次。

### 4.2 为何 module-level 而不是 React useEffect

- 订阅生命周期与 React 渲染解耦：StrictMode 下 useEffect 重入不会双订阅
- 跨路由切换不会重订阅 / 不会泄漏
- 测试时可通过模块重导入隔离

### 4.3 触发时序不变量

manus spec §6.3 规定：`ontology.updated` **必须在** 对应 `tool.completed` **之前**。

故"首个 artifact 到达 → stage 弹出"在 ToolPart 的 completed 状态出现**之前**完成动画启动 —— 用户视觉上看到的是工具完成的瞬间，右侧电脑同步现身、装东西，与"AI 干完活了，把东西摆出来给你看"的故事一致。

### 4.4 边界情况

| 场景 | 处理 |
|---|---|
| 同 session 多个 artifact 在第一帧批量到达 | `subscribe` 在 zustand 单次 setState 后只触发一次，`notifyArtifactArrived` 幂等 |
| 用户先 closeStage 再产新 artifact | `autoOpenedSessions` 已含 sid → 不自弹 |
| 切换 session（A 已开 stage，进入 B） | B 用自己的 openBySession（默认 false 关）→ 显示 chat 全宽，等 B 自己的首个 artifact |
| 历史 session 冷启动（GET /messages + /artifacts 一次性灌入大量 artifact） | `lastSizeBySession` 初始 0 → 灌入后 `size > 0` → 自弹一次 → `autoOpened` 标记 |
| Stage 已自弹后用户手动关再开 | open 是用户手动行为；`autoOpenedSessions` 已标，不会再自弹；用户关了再开是允许的 |
| 用户拖 PanelResizeHandle 把右栏缩到 < minSize 触发 onCollapse | `syncCollapsed` 反向更新 `openBySession=false`；不污染 `autoOpenedSessions`（拖动不算"主动 close"，所以未来再有新 artifact 还会再弹一次？）—— **决策**：拖动 collapse 视同主动关，仍会标 autoOpened（避免反复自弹的烦扰）。`syncCollapsed` 内置 `autoOpenedSessions.add(sid)`。

---

## 5. 动画 & 视觉过渡

### 5.1 三段动画

| 触发 | 动画 | 时长 |
|---|---|---|
| open: false → true（自弹或手动开） | Panel.expand() 库默认布局过渡 + StageWindow opacity 0 → 1 | 库默认 + 180ms（部分重叠） |
| open: true → false | Panel.collapse() 库默认布局过渡 + StageWindow opacity 1 → 0 | 库默认 + 180ms |
| 已 open 状态下切换 active artifact（chip 点击 / supersede） | 沿用 manus spec §4.6 "原地变色" 的 Recharts useMemo + 180ms opacity 0.6→1 | 不变 |

> "库默认"指 react-resizable-panels v2 的 collapsible 过渡（基于 CSS transition，约 200-300ms）。如果实测体感生硬，可在 plan 阶段决定是否覆盖。

### 5.2 不引入新依赖

- 完全靠 react-resizable-panels 自带的 collapsible + Tailwind 内置 `transition-opacity duration-180`
- 零 framer-motion
- HERO→SPLIT 的 clip-path 裂开动画（manus spec §4.3）保持原样不动

### 5.3 reduced-motion

```css
@media (prefers-reduced-motion: reduce) {
  [data-stage-open] { transition: none !important; }
}
```

PanelGroup 自身的过渡是布局变化，依然平滑（不在 motion 范畴）。

---

## 6. PromptComposer 改动

`features/session/prompt-composer.tsx` 的 InputGroupAddon `block-end` 内现有结构：

```
[Model Select] [Auto Switch] [Spacer] [Send Button]
```

改为：

```
[Model Select] [Auto Switch] [StageToggleButton] [Spacer] [Send Button]
```

仅需新增一处 `<StageToggleButton />` 引用，无其他逻辑变更。

按钮视觉规格（已在 §2.3 给出）：

- 状态 0（HERO/NOSESS）：`disabled` + ghost variant，灰图标
- 状态 1（SPLIT + closed）：ghost + 描边图标 + `text-muted-foreground`
- 状态 2（SPLIT + open）：`bg-accent/70 text-foreground` + 实心高亮
- 高度与 Send Button 一致（`size="icon-xs"` h=24）

---

## 7. /preview 删除清单

### 7.1 待删除文件（精确列表）

| 路径 | 是否删除 |
|---|---|
| `client/src/routes/preview.tsx` | 删 |
| `client/src/features/preview/preview-page.tsx` | 删 |
| `client/src/features/preview/preview-canvas.tsx` | 删 |
| `client/src/features/preview/preview-composer.tsx` | 删 |
| `client/src/features/preview/preview-error-boundary.tsx` | 删 |
| `client/src/features/preview/preview-mock-data.ts` | 删 |
| `client/src/features/preview/preview-script.ts` | 删 |
| `client/src/features/preview/preview-sidebar.tsx` | 删 |
| `client/src/routeTree.gen.ts` | 不手动改，TanStack Router CLI 重新生成自动剔除 PreviewRoute |

### 7.2 不动的共享代码

preview 文件依赖以下生产代码，删除时**不能**误伤：

- `@/services/channel/use-channel`（`buildEventSink`）
- `@/services/channel/types`（`StreamEvent`、`createTextPart`）
- `@/lib/uuid`（`generateUuid`）
- `@/stores/*` 全部
- `@/features/connection/store`
- `@/features/actions/registry`
- `@/features/chat/components/message-stream`
- `@/features/ontology/components/artifact-canvas` / `artifact-timeline-strip`

这些路径**主窗体也在用**，preview 是单向依赖（preview → 生产），删 preview 不会反向影响。

### 7.3 主窗体不受影响验证

- `Grep` 扫描 `features/preview` / `routes/preview` 在 `client/src/` 内的引用：除 `routes/preview.tsx` 自身和 `routeTree.gen.ts`（自动生成），无其他位置 → 删除是封闭的
- 主窗体 `SessionCanvas` 内已有的 `manus-clip-style` `<style>` 注入逻辑（行 14-26）与 preview-canvas.tsx 内的同名注入是**两份独立副本**，删 preview 这一份不影响主窗体
- 删除后执行 `cd client && npx tsc --noEmit` 应零报错；执行 `npm run dev` 后访问 `/preview` 应返回 TanStack Router 默认 404

---

## 8. 测试

### 8.1 单元测试

| 文件 | 测试点 |
|---|---|
| `stores/stage-store.test.ts` | openStage/closeStage/toggleStage 改 openBySession；closeStage 隐式 markAutoOpened；syncCollapsed 反向同步且去重；notifyArtifactArrived 幂等 |
| `features/stage/use-stage-auto-open.test.ts` | 模拟 `useOntologyStore.setState` 推首个 artifact → `useStageStore.getState().openBySession` 该 sid 变 true；用户先 closeStage 再 setState 推 artifact → 不再 open；多 session 互不影响 |
| `features/stage/components/stage-window.test.tsx` | titlebar 渲染 3 圆点；点红圆点触发 closeStage 且 stopPropagation；title 随 active artifact 变化 |
| `features/stage/components/stage-toggle-button.test.tsx` | HERO 时 disabled；SPLIT closed 时点击 → openStage；SPLIT open 时点击 → closeStage；aria-pressed 正确 |

### 8.2 集成测试

| 文件 | 测试点 |
|---|---|
| `features/session/split-view.test.tsx` | useStageStore.openBySession 切换 → stagePanelRef.current.expand/collapse 被调用一次（mock ref）；onCollapse 回调触发 syncCollapsed |

### 8.3 回归手动验收清单

- [ ] HERO 输入第一条消息 → 进 SPLIT → stage 关闭、左栏占满
- [ ] 等待 AI 产出第一个 artifact（execute_sql 完成）→ stage 自动展开（含动画）
- [ ] 点 titlebar 红圆点 → stage 折叠、左 chat 扩展到 100%
- [ ] 点主输入框小电脑按钮 → stage 重新展开
- [ ] AI 产出第二个 artifact（render_chart）→ stage 不再自动弹（已被用户手动关过）
- [ ] 已开 stage 时追问"换成绿色" → ChartArtifact 原地变色动画正常
- [ ] 已关 stage 时追问"换成绿色" → 仍不自弹，但点开后看到的是新版
- [ ] 切换 session（在 sidebar 选另一个 session）→ stage 状态独立保持
- [ ] HERO/NOSESS 时小电脑按钮 disabled + tooltip
- [ ] `prefers-reduced-motion: reduce` 下无 opacity 闪
- [ ] `/preview` 路径访问返回 404；`/` 主窗体所有功能正常

### 8.4 不写 E2E

沿用 manus spec §8 分层（vitest 在 L0/L5），不引入 Playwright 单独为本 spec 跑 E2E。手动验收覆盖回归。

---

## 9. 风险 & 兜底

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| react-resizable-panels 的 collapsible API 在 Tauri WebView 行为异常 | 低 | 中 | 已在 SplitView 用 PanelGroup 多周；不行就降级用 conditional render + CSS width 过渡 |
| zustand 跨 store subscribe 在 SSR 触发（Tauri 没有 SSR，但 Vite SSR 模式或测试） | 低 | 低 | `ensureStageAutoOpenSubscribed` 不做任何 DOM 操作；测试里通过 vi.resetModules + 重新 import 重置 |
| 智能自弹与用户手动关的竞态（artifact 到达瞬间用户也点了 close） | 低 | 低 | zustand setState 串行；`closeStage` 标 autoOpened 后 `notifyArtifactArrived` 不再触发 |
| 删除 preview 后 routeTree.gen.ts 未自动重新生成 | 低 | 低 | dev 模式 TanStack Router 启动时自动 regen；保险方式手动删除 routeTree.gen.ts 让其首次启动重建（在 plan 里明确给 `npm run dev` 重启步骤） |
| Tooltip 在小屏触屏环境难触发 | 低 | 低 | tooltip 仅做 hint，按钮 disabled 状态本身视觉可见 |
| HERO→SPLIT 跃迁瞬间 artifact 提前到达（理论无可能）触发 auto-open | 极低 | 低 | `ensureStageAutoOpenSubscribed` 在 HomePage mount 时已挂；首个 artifact 触发时 SPLIT 已就位 |

---

## 10. 实施顺序（给 plan 阶段参考）

不在本 spec 范围，写 plan 时建议顺序：

1. 新建 `stores/stage-store.ts` + 单测
2. 新建 `features/stage/use-active-artifact-title.ts`
3. 新建 `features/stage/use-stage-auto-open.ts` + 单测
4. 新建 `features/stage/components/stage-window.tsx` + 单测
5. 新建 `features/stage/components/stage-toggle-button.tsx` + 单测
6. 改 `features/session/split-view.tsx` 接入 collapsible + StageWindow
7. 改 `features/session/prompt-composer.tsx` 插入 StageToggleButton
8. 改 `features/workspace/home-page.tsx` 挂订阅
9. 删 `routes/preview.tsx` + `features/preview/` 整目录
10. 跑 `npx tsc --noEmit` + vitest + 手动验收清单
11. 更新 `docs/exec-plans/index.md`

---

## 11. 与既有 spec 的关系

- 本 spec 在 `2026-04-16-manus-split-view-design.md` §4.6 的"右栏：主画布 + 时间线胶囊"基础上做"外壳化 + 可关可开"增量改造，不修改：
  - HERO/SPLIT 状态机及合法跃迁表（§4.1）
  - HERO→SPLIT FLIP + clip-path 裂开动画（§4.3）
  - PartRenderer 分发（§4.5）
  - timeline-store 的 active 跟随规则（§4.4）
  - 协议、SessionBus、ActionRegistry、ToolCallBridge（全部 server 侧未改）
  - SQLite schema（§5.6 未新增表）

- 唯一与 manus spec §1.2 不变量"hero/split 是前端本地状态"对齐的扩展：stage open/close 也是前端本地状态，不持久化、不走 SSE、不走 ontology

- /preview 沙盒虽出自上个迭代，但其使命（演示 manus 动效）已完成；本次改造之后主窗体能直接看到等效效果，故下线。
