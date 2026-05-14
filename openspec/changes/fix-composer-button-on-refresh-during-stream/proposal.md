## Why

streaming 期间按 Ctrl+R 刷新页面后，prompt-composer 的发送/停止按钮规约要求**保持转圈停止态**直到真正的 turn 完成。当前实现违反该规约：刷新后按钮立即翻回"待发送"箭头，用户被误导为本轮已结束，可以再次发送 —— 但后端 turn 仍在跑，导致并发 turn 或操作错位。

该现象与已归档的 [BUG-0037](../../../docs/bugs/BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) / [BUG-0038](../../../docs/bugs/BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) 同症状但**根因不同**：前两个 BUG 解决的是 sessionStorage 持久化时机（L3/L4）和 SSE 重放窗口（L1/L2），本提案解决的是**请求生命周期与 turn 生命周期被错误等同**的逻辑根因 —— BUG-0037 的同步写修复反而把这一路径暴露为稳定可复现。

## What Changes

- 修改 `useChannel.sendMessage` 与 `useChannel.retryPendingUser`（`client/src/services/channel/use-channel.ts`）：
  - **移除 `finally { setStreaming(sessionId, false) }`** — 该清理把"请求结束"误判为"turn 结束"。
  - **将 streaming 清理收敛到两条合法路径**：
    1. SSE 事件 `session.idle` / `session.status=idle` / `session.error`（已存在）
    2. POST 请求**在 SSE 流尚未打开前**就失败时的 catch 分支（新增局部清理）
- 对 retryPendingUser 应用相同修复。
- 保留并强化既有 5 层防御（L1–L5）—— 本变更与其叠加生效，不替换任何一层。
- 新增 vitest 单元测试，覆盖：
  - Ctrl+R 模拟（AbortError 中断 SSE）后 `streamingBySession` 仍包含 sessionId
  - POST 请求 5xx / 网络错误（SSE 未打开）路径下 `streamingBySession` 被清除
  - 正常 turn 完成路径（session.idle 到达）下 `streamingBySession` 被清除
- 注册并最终关闭 `docs/bugs/BUG-0046-*.md`（`regression: true`，关联 BUG-0037 / BUG-0038）。

## Capabilities

### New Capabilities
- `composer-streaming-state`：声明 prompt-composer 发送/停止按钮所依赖的 `streamingBySession` 状态机的规约 —— 触发条件、清理边界、Ctrl+R / 网络中断下的不变量、与 SSE 事件流的关系。把当前散落在 5 层防御里的隐式契约提炼成可被测试的显式规约。

### Modified Capabilities
（无 — 当前 `openspec/specs/` 下没有覆盖此行为的规约，故以新建 capability 形式收录）

## Impact

- **代码**：仅 `client/src/services/channel/use-channel.ts`（`sendMessage` + `retryPendingUser` 两处闭包内的 try/catch/finally 重构）。
- **测试**：`client/src/services/channel/use-channel.test.ts` 新增 BUG-0046 用例套件；不修改既有 BUG-0037/0038 用例。
- **行为变更**：
  - Ctrl+R 后 `streamingBySession[sessionId]` 在长寿 GET 流接管前保持 `true`，按钮稳定显示停止态。
  - 网络层错误（pre-stream）路径下 streaming 被正确清除（与旧行为一致）。
  - 长寿 GET sink 仍是清理 streaming 的最终权威源。
- **不影响**：后端 SSE / SessionBus 协议、ChannelController、其他前端 store、其他 UI 模块。
- **依赖兼容**：本变更是纯前端 React/TypeScript 修复，不引入新依赖。
- **数据源兼容**：N/A（不触及任何数据库类型，无需读 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`）。

## Design Inputs

按 CLAUDE.md "Frontend Design Contract Gate" 与 "Frontend Plan Gate" 要求，已阅读 [`client/DESIGN.md`](../../../client/DESIGN.md)。本变更**不修改 UI 视觉与 token**，仅修复状态机；适用的设计约束如下：

- **"Motion as Confirmation"**（`client/DESIGN.md` 第 263 行）：按钮的转圈停止态是 motion-as-confirmation 的关键载体；它必须忠实表征"turn 仍在跑"的事实，禁止因请求生命周期短暂中断而误清。
- **Composer 组件契约**（`client/DESIGN.md` "Component Rules" 第 313 行）：`Composer uses bg.panel, border.default, and interaction.focusRing. It is a composed work control, not a plain textarea shell.` —— 状态机修复后视觉无变更，复用现有 token，未引入新视觉语言。
- **"State cannot be communicated by color alone"**（"Accessibility" 第 335 行）：转圈停止图标（`Loader2Icon`，spin 动画）已通过形状/动效与发送箭头（`ArrowUpIcon`）区分，无障碍语义不受影响。

## Risks

- **Known related BUGs**（已读 `docs/bugs/index.md`）：
  - [BUG-0037](../../../docs/bugs/BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) `fixed` —— 同症状的 store 持久化路径修复；本提案与之**互补**，不回退。
  - [BUG-0038](../../../docs/bugs/BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) `fixed` —— SSE 重放路径修复（500ms 窗口 + cursor 同步写）；本提案与之**互补**。
  - [BUG-0039](../../../docs/bugs/BUG-0039-composer-draft-sync-write-wrong-schema.md) `fixed` —— composerDraft 同步写 schema 错位；与本变更无重叠，但作为 Zustand persist 同步写模式的兄弟案例，本提案沿用其推荐的 `{state, version}` schema。
- **新 BUG**：注册为 `BUG-0046`，`regression: true`，标注关联 BUG-0037 / BUG-0038。
- **回归风险**：若清理路径不完整（pre-stream 失败未补 setStreaming(false)），用户在网络故障时会卡在 streaming 态，按钮永不复位。已用 vitest 用例覆盖该路径。
- **长寿 GET sink 不存在的边界**：useSessionSubscribe 在组件挂载时启动 GET /subscribe；如果挂载失败或被 `subscribedSessions` 重复订阅守卫挡掉，turn-complete 事件可能永远不到。该路径**已存在**，非本变更引入；记入 Open Questions。
