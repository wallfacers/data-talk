# Stage 气泡展开动画 + 圆角内背景色区分 设计

> 日期：2026-04-17
> 状态：待实施
> 前置 spec：`2026-04-17-stage-as-computer-design.md`（本 spec 在其 §5 "动画 & 视觉过渡" 与 §2.2 "`<StageWindow>` 形态" 的基础上做动画升级 + 配色微调）

---

## 0. 摘要

在"Stage 即电脑"方案上做两处增量：

1. **气泡式开/关动画**：点 prompt-composer 里的小电脑按钮（`StageToggleButton`）打开 Stage 时，Stage 从按钮位置以**圆形 clip-path 膨胀**的方式展开至全尺寸；关闭则以相同起点收缩回去。视觉隐喻 "电脑从按钮里被掏出来 / 收回按钮里"。
2. **Stage 圆角内部背景色区分**：当前 `StageWindow` 用 `bg-card`，light 模式下与外层 `--background` 同为纯白，仅靠 `border + shadow` 撑层次。改为 `bg-muted`（light 0.97 / dark 0.269），与外层形成一档清晰色差。

### 0.1 成功标准

1. 从 `HERO` 点小电脑按钮进入 `SPLIT`（或在 `SPLIT` 下 stage 已关闭时点按钮打开）：Stage 面板从按钮位置**圆形膨胀**展开到全尺寸，同时 chat 列宽从 100% → 46% 正常让位，400ms 内完成
2. 点 Stage titlebar 关闭按钮（`X`）或再次点小电脑按钮：Stage 以按钮位置为圆心**收缩**回零，同时 chat 列宽从 46% → 100%，400ms 内完成；动画结束后 Stage 滑出视口（`translateX(100%)`），避免遮挡事件
3. 首次自动弹出（`notifyArtifactArrived`，无按钮点击上下文）：退回既有的 `translateX` 滑入行为，不做气泡
4. Stage 圆角内部背景色可视地区分于外部 chat 背景（light：0.97 vs 1.0；dark：0.269 vs 0.145）
5. `prefers-reduced-motion: reduce` 下所有过渡降级为瞬切
6. 现有功能（`closeStage` / `toggleMaximized` / `notifyArtifactArrived` / 多 session 独立状态）全部不受影响

### 0.2 非目标

- 不引入 framer-motion 等新动画库（延续前置 spec §5.2 "零 framer-motion" 原则）
- 不改 stage-store 核心数据结构（仅追加 `revealOrigin` 一个字段）
- 不改 `ArtifactTimelineStrip` / `ArtifactCanvas` 内部
- 不改 HERO→SPLIT 的 clip-path 裂开动画（前置 manus spec §4.3）
- 不为"最大化"状态（`maximized`）单独设计气泡（maximized 沿用既有 width/transform 过渡）
- 不做跨 session 共享 `revealOrigin`（origin 是全局单值，谁最后点按钮归谁；多 session 切换不重置）

---

## 1. 高层改动一览

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `stores/stage-store.ts` | 新增字段 | `revealOrigin: { x: number; y: number } \| null` + `setRevealOrigin(o)` |
| `features/stage/components/stage-toggle-button.tsx` | 改 | click handler 中先 `getBoundingClientRect` 取按钮中心，`setRevealOrigin`，再走既有 open/close 分支 |
| `features/stage/components/stage-window.tsx` | 改 | `bg-card` → `bg-muted`；titlebar `bg-muted/40` 去底色（保留 `border-b`） |
| `features/session/split-view.tsx` | 改 | Stage 容器加 `clip-path` 过渡与 CSS 变量；调整 `translateX` 语义（仅关闭动画结束后生效） |
| `styles/globals.css` | 追加 | `@media (prefers-reduced-motion: reduce)` 规则 |

无新增文件，无新依赖，无层级调整。

---

## 2. 状态层：`stage-store` 增量

### 2.1 新增字段

```ts
type RevealOrigin = { x: number; y: number } // 按钮中心视口坐标

type StageState = {
  // ...既有字段...
  revealOrigin: RevealOrigin | null
  setRevealOrigin: (o: RevealOrigin | null) => void
}
```

### 2.2 行为契约

- `setRevealOrigin({x, y})`：覆盖写入。由 `StageToggleButton` 在 click 最前面调用
- `setRevealOrigin(null)`：暂不需要调用点（保留未来接口，例如"清空最近点击来源"）
- `closeStage` 与 `notifyArtifactArrived` **不触碰** `revealOrigin` —— 关闭时仍需要用同一圆心做收缩动画；自动弹出时若 `revealOrigin` 不为 null 也直接沿用（用户此前点过一次，之后的自弹沿用那个锚点也合理）
- 不持久化（与 `openBySession` / `autoOpenedSessions` 同级）

### 2.3 为什么是全局单值而不是 `originBySession`

- 按钮是全局的（`prompt-composer` 单实例），不同 session 切换时按钮位置几乎不变
- 简化 store 形状，后续迭代若真需要 per-session origin 再扩展

---

## 3. 气泡动画机制

### 3.1 坐标语义

`revealOrigin = { x, y }` 存的是**视口坐标**（来自 `getBoundingClientRect()` 的 `(left + width/2, top + height/2)`）。

在 stage 容器内渲染 clip-path 时，CSS `clip-path: circle(r at X Y)` 的 `X/Y` 是**相对当前元素自身左上角**的本地坐标。实现时在 `split-view.tsx` 用 `stageContainerRef` 拿到 `stageRect`，计算：

```ts
const localX = origin.x - stageRect.left
const localY = origin.y - stageRect.top
```

写入 CSS 变量 `--reveal-x / --reveal-y`（单位 `px`）。

### 3.2 半径计算

打开完成时 clip-path 圆必须覆盖整个 stage。半径取 "origin ↔ stage 对角"的最远距离：

```ts
const R = Math.max(
  Math.hypot(localX, localY),                        // 左上角
  Math.hypot(stageRect.width - localX, localY),      // 右上角
  Math.hypot(localX, stageRect.height - localY),     // 左下角
  Math.hypot(stageRect.width - localX, stageRect.height - localY), // 右下角
)
```

写入 `--reveal-r`。fallback：若 `revealOrigin === null` 或测量失败，`R = 2000px`（保底大于任何视口对角）。

### 3.3 过渡时序

| 阶段 | chat width | stage translateX | stage clip-path |
|---|---|---|---|
| 关闭稳态 | 100% | 100%（退出视口） | circle(0 at x y) |
| 打开 t=0 | 100% → 46%（开始） | 0%（即刻就位） | circle(0 at x y) |
| 打开 t∈(0, 400ms) | 动画中 | 0 | circle(0) → circle(R) |
| 打开稳态 | 46% | 0 | circle(R) |
| 关闭 t=0 | 46% → 100%（开始） | 0 | circle(R) |
| 关闭 t∈(0, 400ms) | 动画中 | 0 | circle(R) → circle(0) |
| 关闭 t=400ms 后 | 100% | 100%（退出视口，防点击穿透） | circle(0) |

关键：
- **打开瞬间** `translateX` 先切为 0（stage 进入布局）；**关闭动画结束后** 再切为 100%（stage 退出布局）
- 这个切换不能走 CSS transition（否则打开时会有一瞬间 stage 从右滑入再气泡）—— 实现上用 React `useEffect` + `setTimeout(400)` 或监听 `transitionend` 做二段切换

### 3.4 实现要点

- `clip-path` 是本方案的**核心**动画，参与 CSS transition
- `transform`（用于 stage 退出视口的 `translateX(100%)`）不走 transition，而是通过 React state `translateLatched` 双段驱动：
  - 打开瞬间：`translateLatched = false`（translateX 立即切到 0）
  - 关闭动画结束后：`translateLatched = true`（translateX 瞬切回 100%）
- 具体 JSX / style 见 §6.4

### 3.5 首次自动弹出退化路径

`notifyArtifactArrived` 触发时：
- 若 `revealOrigin` 有值（用户此前点过按钮）：沿用既有 origin 做气泡
- 若 `revealOrigin === null`：CSS 变量未设置 → `clipPath` fallback 到 `circle(2000px at 100% 100%)`，视觉上约等于"从右下角膨胀"。再叠加既有的 `translateX 100% → 0` 过渡，整体退化为"右侧滑入"行为，不会是一个尴尬的定点气泡。

### 3.6 reduced-motion

```css
/* globals.css 追加 */
@media (prefers-reduced-motion: reduce) {
  [data-stage-panel] {
    transition: none !important;
  }
}
```

此时 open 切换 → clip-path / width / transform 全部瞬切。

---

## 4. `StageToggleButton` 改造

```tsx
// features/stage/components/stage-toggle-button.tsx
export function StageToggleButton() {
  const btnRef = useRef<HTMLButtonElement>(null)
  const sid = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const { mode } = useSessionMode()
  const open = useStageStore((s) => (sid ? !!s.openBySession.get(sid) : false))
  const openStage = useStageStore((s) => s.openStage)
  const toggle = useStageStore((s) => s.toggleStage)
  const setRevealOrigin = useStageStore((s) => s.setRevealOrigin)

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

  // ...既有 JSX，只是 <Button ref={btnRef}>
}
```

关键：**先测量后派发**。测量必须在 `handleClick` 内同步完成（React batch 更新前），否则 stage 容器重新 render 时可能 origin 还没就位。

---

## 5. `StageWindow` 配色调整

### 5.1 外壳

```tsx
// 旧
<div className="flex h-full w-full flex-col overflow-hidden rounded-2xl border bg-card shadow-xl ring-1 ring-black/5 dark:ring-white/10">

// 新
<div className="flex h-full w-full flex-col overflow-hidden rounded-2xl border bg-muted shadow-xl ring-1 ring-black/5 dark:ring-white/10">
```

### 5.2 titlebar

当前 titlebar 没有显式底色（查了代码行 28-55，实际是 `<div className="flex h-10 shrink-0 items-center justify-between px-3 select-none">`，无 `bg-*` class）。**继续不加底色**，靠 titlebar 和主体同色 + 图标按钮本身的 hover 底色（`hover:bg-accent/50`）区分交互区即可。

> 前置 spec §2.2 描述 titlebar 有 `bg-muted/40` 底色，但实际实施偏差：线上代码 titlebar 就没有底色。本 spec 不恢复它。

### 5.3 色差对照

| 模式 | `--background`（外层） | `--muted`（stage 内） | 对比感 |
|---|---|---|---|
| light | oklch(1 0 0) | oklch(0.97 0 0) | 微妙 3% 亮度差，配合 border + shadow 可辨 |
| dark | oklch(0.145 0 0) | oklch(0.269 0 0) | stage 明显提亮一档，符合"活动窗口前置"直觉 |

---

## 6. `SplitView` 改造

### 6.1 新状态

```tsx
const stageContainerRef = useRef<HTMLDivElement>(null)
const [clipGeom, setClipGeom] = useState<{ r: number; x: number; y: number } | null>(null)
const [translateLatched, setTranslateLatched] = useState<boolean>(!open) // 关闭态时为 true，stage 退出视口
```

### 6.2 几何测量

```ts
useLayoutEffect(() => {
  if (!open) return
  const origin = useStageStore.getState().revealOrigin
  const rect = stageContainerRef.current?.getBoundingClientRect()
  if (!origin || !rect) return
  const localX = origin.x - rect.left
  const localY = origin.y - rect.top
  const r = Math.max(
    Math.hypot(localX, localY),
    Math.hypot(rect.width - localX, localY),
    Math.hypot(localX, rect.height - localY),
    Math.hypot(rect.width - localX, rect.height - localY),
  )
  setClipGeom({ r, x: localX, y: localY })
}, [open])
```

### 6.3 `translateLatched` 双段逻辑

```ts
useEffect(() => {
  if (open) {
    setTranslateLatched(false) // 立即就位
  } else {
    const t = setTimeout(() => setTranslateLatched(true), DURATION)
    return () => clearTimeout(t)
  }
}, [open])
```

稳态：
- `open && !translateLatched`：stage 可见、clip-path 覆盖全区域
- `!open && !translateLatched`：clip-path 正在收缩（前 400ms 内）
- `!open && translateLatched`：stage 退出视口（收缩结束后）

### 6.4 渲染

```tsx
<div
  ref={stageContainerRef}
  data-stage-panel
  style={{
    position: 'absolute',
    top: 0, right: 0, bottom: 0,
    width: stageWidth,
    transform: translateLatched ? 'translateX(100%)' : 'translateX(0)',
    clipPath: clipGeom
      ? `circle(${open ? clipGeom.r : 0}px at ${clipGeom.x}px ${clipGeom.y}px)`
      : `circle(${open ? 2000 : 0}px at 100% 100%)`,
    transition: `clip-path ${DURATION}ms ${EASE}, width ${DURATION}ms ${EASE}, transform 0ms linear`,
    willChange: 'clip-path, transform',
  }}
>
```

> 注：`width` 的过渡实际由 `stageWidth` 值变化驱动（maximized 态切换）；`translateX` 0ms linear 表示它是"瞬切"非"过渡"。

### 6.5 maximized 与气泡的交互

`maximized` 状态下 stage 宽 100%，此时若用户点"还原"退出 maximized：
- 退出 maximized 只改 `maximized=true → false`，不关闭 stage → Stage 保持 open，宽度从 100% → 54%（原有 width 过渡）
- clip-path 继续是 `circle(R)`（已全覆盖），不触发气泡
- 符合既有心智模型

若 maximized 时点关闭按钮（titlebar 的 X）：
- `closeStage` → `open=false` → 按 §3.3 时序关闭（clip-path 收缩）
- 此时 stage 宽度 100%，圆心仍是按钮坐标（远在左下角 composer 位置）→ 收缩效果仍从那个点往里收，**可能视觉略奇怪**（stage 太大）
- **取舍**：接受此边界情况（maximized + close 是罕见组合）。如果真有用户反馈，未来可在"退出 maximized 动画结束后再触发 close"里分两步处理

---

## 7. 测试

### 7.1 单测增量

| 文件 | 测试点 |
|---|---|
| `stores/stage-store.test.ts` | `setRevealOrigin({x,y})` 写入字段；`setRevealOrigin(null)` 清空；`closeStage` / `notifyArtifactArrived` 不触碰 `revealOrigin` |
| `features/stage/components/stage-toggle-button.test.tsx` | 点击时 `getBoundingClientRect` 被调用；`revealOrigin` 被更新为按钮中心视口坐标 |

### 7.2 集成测增量

| 文件 | 测试点 |
|---|---|
| `features/session/split-view.test.tsx` | open 切换时 stage 容器 style 含 `clip-path: circle(...)`；`revealOrigin` 为 null 时 clip-path fallback 到 `circle(..px at 100% 100%)`；关闭后 `DURATION` 毫秒内 `translateX` 从 0 变到 100%（用 `vi.useFakeTimers`） |

### 7.3 手动验收

- [ ] 从 HERO 点小电脑按钮 → Stage 从按钮位置圆形膨胀展开，chat 同步让位
- [ ] 在 SPLIT open 态再次点按钮 → Stage 从按钮位置收缩回零，chat 同步扩展
- [ ] 点 titlebar X 按钮关闭 → 从按钮位置（不是 X 位置！）收缩 —— 圆心由 `revealOrigin` 决定，X 按钮不更新 origin，故延用上次按钮位置
- [ ] 首次 artifact 到达自动弹出 → 沿用既有滑入（若从未点过按钮）或从上次按钮位置气泡（若点过）
- [ ] Maximized 状态下点还原 → 宽度平滑缩回 54%，clip-path 不变化（仍全覆盖）
- [ ] Stage 圆角内部 light 模式可见微灰（vs 纯白外部），dark 模式明显亮一档
- [ ] `prefers-reduced-motion: reduce`（系统设置打开或 Chrome DevTools Rendering panel）下切换即时无动画
- [ ] 切换 session，stage 状态独立保持；`revealOrigin` 是全局的不影响

---

## 8. 风险 & 兜底

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| `getBoundingClientRect` 在 composer 隐藏时返回 0/0 | 低 | 低 | 按钮只在 composer 渲染后才可见，不存在隐藏时点击 |
| `clip-path` 在 Tauri WebView 某版本不支持 circle 过渡 | 低 | 中 | Chromium WebView 稳定支持；若真遇到，降级把 `transition` 中的 `clip-path` 去掉 → 瞬切 |
| 动画期间用户再次点按钮（连点） | 中 | 低 | `clip-path` transition 自动接续；`translateLatched` 的 setTimeout 在 deps 变化时被 clearTimeout 覆盖，不会错位 |
| window.resize 时动画正在进行 | 极低 | 低 | 接受过渡中止重来；下一次点击重新测量即可 |
| `origin.x / y` 落在 stage 容器之外（负数或超过宽高） | 中 | 低 | 这是**预期行为** —— 按钮在 chat 列内，localX 自然是负数；`circle(r at -200px 800px)` 渲染正常，只是圆心在可见区外，膨胀时会有一瞬间的偏心感，正是"从外部飞进来"的视觉 |
| 测试环境 jsdom 不支持 `clip-path` | 低 | 低 | 单测只断言 style 属性字符串包含 `clip-path: circle(`，不依赖实际渲染 |

---

## 9. 实施顺序（供 plan 阶段参考）

1. `stage-store.ts` 加 `revealOrigin` + `setRevealOrigin` + 对应单测
2. `stage-toggle-button.tsx` 加 ref + 测量 + `setRevealOrigin` + 对应单测
3. `split-view.tsx` 接入 `clipGeom` + `translateLatched` + clip-path 渲染 + 集成测
4. `stage-window.tsx` `bg-card` → `bg-muted`
5. `globals.css` 追加 reduced-motion 规则
6. `npx tsc --noEmit` + vitest + 手动验收清单走一遍
7. 更新 `docs/exec-plans/index.md` 记录本次动画升级

---

## 10. 与既有 spec 的关系

- 本 spec 在 `2026-04-17-stage-as-computer-design.md` §5.1 "三段动画"基础上对"open: false ↔ true"那两行做升级，其余（active artifact 切换、reduced-motion）保持不变
- `2026-04-17-stage-as-computer-design.md` §3 的 `stage-store` 形态在本 spec 中被**增量**扩展（+ `revealOrigin`），非颠覆
- `2026-04-16-manus-split-view-design.md` §4.3 HERO→SPLIT clip-path 裂开动画与本 spec 的 stage clip-path 是**两个独立层级的 clip-path**：外层裂开动画走的是 HERO 整体，内层气泡动画走的是 SPLIT 内 stage 容器自身，互不干扰
- 不影响后端协议、Action 注册、SessionBus、SQLite schema
