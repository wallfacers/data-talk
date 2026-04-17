# UI Demo 模式 & Stage 滑动动画 — 技术债记录

**日期**：2026-04-17
**背景**：为了在没有数据库连接/Session 的情况下直观调试"小电脑 Stage 面板"的布局与过渡效果，临时引入了一套"demo 预览模式"与自定义 SplitView 滑动动画。**这些改动绕过了真实业务流程，接通正式 session / connection 前需要逐项清理或改造**。

---

## 1. 功能变更一览

| 类别 | 改动 | 文件 |
|------|------|------|
| 布局 | 始终渲染 `SplitView`（不再在 HERO/SPLIT 间切换） | `features/session/session-canvas.tsx` |
| 布局 | 移除 `react-resizable-panels`，改为自写 `position:absolute` + width/translateX 过渡 | `features/session/split-view.tsx` |
| 按钮 | `StageToggleButton` 始终可点（`disabled={false}`）；无 session 时调 `toggleGlobal()` | `features/stage/components/stage-toggle-button.tsx` |
| 状态 | `stage-store` 新增 `globalOpen` / `maximized` / `demoMessages` / `toggleGlobal` / `toggleMaximized` / `addDemoMessage` | `stores/stage-store.ts` |
| 提交 | 无 session 时 `onSubmit` 不再触发 `ConnectionOverlay`，改为向 `demoMessages` 追加假消息 + 600ms 后追加 AI 占位回复 | `features/session/prompt-composer.tsx` |
| 消息 | `MessageStream` 为空时渲染 `DemoBubbles`（读取 `demoMessages`） | `features/chat/components/message-stream.tsx` |
| 头部 | 新增 `ChatHeader`（会话名 + `...` 下拉，含"重命名/删除"占位） | `features/session/chat-header.tsx` |
| Stage | `StageWindow` sessionId 变为可选；去掉 macOS 交通灯；右上角加 `XIcon`（关闭）+ `Maximize2Icon`（放大） | `features/stage/components/stage-window.tsx` |
| 样式 | `max-w-2xl` → `max-w-3xl`（672→768）；textarea `h-[100px]` → `h-[90px]` | `features/session/hero-view.tsx` / `split-view.tsx` / `prompt-composer.tsx` |
| Slot | `useComposerSlot` 每次渲染 `useLayoutEffect` 重新查询 DOM（应对 HeroView↔SplitView 切换） | `features/session/prompt-composer.tsx` |

---

## 2. 动画规格（用户确认版本，后续会手动调整）

**打开（都向左移动）**
- Chat 窗体：`width: 100% → 46%` —— 右边收窄让位，内容 `max-w-3xl mx-auto` 中心点随之左移
- Stage 窗体：`transform: translateX(100%) → translateX(0)` —— 从屏幕右外侧平滑滑入

**关闭（都向右移动）**
- Chat 窗体：`width: 46% → 100%` —— 右边扩张回满宽，内容右移回中
- Stage 窗体：`transform: translateX(0) → translateX(100%)` —— 向右平滑滑出屏幕

**时间与缓动**
- `DURATION = 400ms`
- `EASE = cubic-bezier(0.32, 0.72, 0.24, 1)`
- 两侧 `transition` 都包含 `width` 和 `transform`，用同一参数保证同步

**关键前提**（别破坏）：
- `SplitView` 必须**始终挂载**。CSS transition 只在"已挂载元素的 inline style 变化"时触发；若在 `SessionCanvas` 里条件渲染它，首次挂载瞬间不会 animate。

---

## 3. 待清理 / 待对接真实场景的项

### 3.1 P1 — 阻塞真实业务接入

- [x] **Demo 提交路径绕过了连接选择**
  `prompt-composer.tsx` 无 session 时 `setTimeout` 加入假 AI 回复，真实路径需恢复：检测 `!activeSessionId` → 弹出连接选择 → 建 session → `sendMessage`。被移除的 `ConnectionOverlay` 仍在文件 `features/session/connection-overlay.tsx`，但未被 import。
  ✅ 2026-04-17 清理：`prompt-composer.onSubmit` 改为 `setPendingPrompt + setPendingConnectionPrompt`；`session-canvas.tsx` 重新挂载 `<ConnectionOverlay />`。
- [x] **`StageToggleButton` 硬编码 `disabled={false}`**
  真实流程下应该是：未选中 session 时禁用，或者点击时引导用户先选 session/连接。
  ✅ 2026-04-17 清理：改为 `disabled={!sid}`；无 session 时不响应点击。测试新增 `无 activeSessionId → 按钮 disabled` case。
- [x] **`ChatHeader` 重命名/删除只是 toast 占位**
  `services/api/session.ts` 里没有 `renameSession` / `deleteSession`。对接后端 API 后要：新增 HTTP 方法、在 ChatHeader 里用 `useMutation` 调用、同步 `useSessions` 缓存。
  ✅ 2026-04-17 清理：后端 `SessionController` 加 `PATCH /{id}` + `DELETE /{id}`；前端 `session.ts` 加 `renameSession` / `deleteSession`；`chat-header.tsx` 用 `useMutation` 接通（`window.prompt` / `window.confirm`）。
- [x] **`stage-store.ts` 的 demo 字段需移除**
  `globalOpen` / `demoMessages` / `addDemoMessage` / `toggleGlobal` 都是临时预览态。正式 Stage 开关只应由 per-session 的 `openBySession` 控制。`maximized` 建议保留但改为 per-session。
  ✅ 2026-04-17 清理：删除四个 demo 字段；`maximized` 改为 `maximizedBySession: Map<string, boolean>`；`toggleMaximized` 改为 `(sid) => void`。
- [x] **`MessageStream` 的 `DemoBubbles` 分支需移除**
  真正的空消息态应显示"空会话"提示或直接留白，而不是占位气泡。
  ✅ 2026-04-17 清理：删除 `DemoBubbles` 组件；空态直接 `return null`（上层 `SplitView` else 分支渲染欢迎页）。

### 3.2 P2 — 功能回归 / UX 补全

- [ ] **用户无法手动调整左右面板宽度**
  原 `PanelResizeHandle` 已移除。若要保留滑动动画又支持拖拽，需用一个混合方案（例如 CSS transition + 拖拽时暂时禁用 transition）。
- [ ] **`session-canvas.tsx` 的 `manus-clip-unlock` 动画不再触发**
  因为 HeroView/SplitView 不再切换。若想保留"首次进入 SPLIT 的裂开动画"，需重新设计触发时机（比如首次 `hasEverSent` 变 true 时）。
- [ ] **`HeroView` 组件孤立**
  `features/session/hero-view.tsx` 仍存在但无引用。其内容（Logo + 标题 + 副标题）已被 `SplitView` 的 else 分支复刻。确认不再需要后可删除。
- [ ] **`ConnectionOverlay` 组件孤立**
  同上，`features/session/connection-overlay.tsx` 已无引用。恢复真实提交路径时要么重新 import 它，要么改版为更轻量的 Inline 选择器。
- [ ] **Stage demo 模式内容为空白**
  `{sid && <ArtifactTimelineStrip />}` / `{sid && <ArtifactCanvas />}` 这两个守卫导致 demo 下 Stage 内部完全空白。对接真实 session 后即可去守卫。

### 3.3 P2 — 实现细节隐患

- [ ] **`useComposerSlot` 每次渲染都跑 `useLayoutEffect`（无依赖数组）**
  这是为了解决 SplitView 内两种布局（has-messages vs empty）切换时 `composer-slot` DOM 节点被替换的问题。性能上可接受（一次 `getElementById`），但语义上不整洁。更正的方案：用 React ref callback 或 Context 传递 slot 元素，而不是通过 `id` 查询。
- [ ] **StageWindow 去掉了 macOS 交通灯**
  原 spec `2026-04-17-stage-as-computer-plan.md` 中 Stage 的设计是 macOS 风格 titlebar。当前改为 X/Maximize 按钮。若要回归原 spec，需恢复交通灯（红/黄/绿）。
- [ ] **`max-w-2xl → max-w-3xl` 和 `h-[90px]`** 是拍脑袋调的，后续 UX 定稿后要在 `prompt-composer.tsx` / `split-view.tsx` / `hero-view.tsx` 三处对齐。

---

## 4. 重新对接真实场景的建议步骤

1. **恢复 `StageToggleButton` 的 disabled 逻辑**，保留"有 session / HERO 模式"时的 `enterSplit + openStage` 路径。
2. **恢复 `ConnectionOverlay` 或设计替代方案**，然后在 `prompt-composer.tsx` 里把 demo 分支去掉。
3. **清理 `stage-store.ts`**：移除 `globalOpen` / `demoMessages` / `toggleGlobal` / `addDemoMessage`；视情况保留 `maximized`（改为 per-session Map）。
4. **清理 `MessageStream`**：删除 `DemoBubbles` 分支，改回纯真实数据渲染。
5. **接通 `ChatHeader` 重命名/删除 API**：新增后端端点、前端 `useMutation`、侧边栏和 header 共享相同 mutation。
6. **决定是否删除 `HeroView` / `ConnectionOverlay`**，或重新启用它们。
7. **重写 `SessionCanvas`**：若选择回到"HERO 与 SPLIT 条件渲染"，则需另外解决首次切换动画丢失问题（例如始终挂载 SplitView，用内部状态控制布局分支；或给 SplitView 包一层，在 mount 后用 `requestAnimationFrame` 触发 open 动画）。

---

## 5. 相关 commit / 文件改动范围（用于 revert 或 review）

本轮会话涉及的文件：

- `client/src/features/session/split-view.tsx`（重写）
- `client/src/features/session/session-canvas.tsx`
- `client/src/features/session/hero-view.tsx`（仅改 `max-w`）
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/session/chat-header.tsx`（**新文件**）
- `client/src/features/stage/components/stage-toggle-button.tsx`
- `client/src/features/stage/components/stage-window.tsx`
- `client/src/features/chat/components/message-stream.tsx`
- `client/src/stores/stage-store.ts`

---

> **清理此文档的触发条件**：上面所有 checkbox 都勾掉后，把本文档移到"已清除"并从 `tech-debt-tracker.md` 里删掉对应条目。
