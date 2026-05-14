---
id: BUG-0046
title: AI streaming 期间 Ctrl+R 让停止按钮误回"待发送"态（BUG-0037/0038 残留路径）
status: fixed
priority: P1
source: manual-report
modules: [session, chat, channel]
discovered: 2026-05-15
discoveredBy: human
testRunId: null
fixCommit: 2c3e3de9
fixPlanRef: openspec/changes/fix-composer-button-on-refresh-during-stream/
duplicateOf: null
regression: true
---

## Summary

继 BUG-0037（streamingBySession 同步落盘）与 BUG-0038（500ms 重放抑制 + cursor 同步落盘）修复之后，AI streaming 期间按 Ctrl+R 刷新仍然稳定复现"停止按钮翻回发送态"。第三条路径来自 `useChannel.sendMessage` 的 `finally { setStreaming(sessionId, false) }`：Ctrl+R 触发的 fetch abort 让 SSE 流 throw，await 落入 catch + finally，**同步**写 `streamingBySession=[]` 到 sessionStorage 后页面 unload。BUG-0037 引入的同步写让这条路径从偶发变成稳定可复现。

## Reproduction Steps

1. 任意 session 中输入一条需要 AI 长时间生成的提问，点击发送。
2. AI 进入流式输出，composer 按钮变为 Loader2Icon（转圈停止态）。
3. 在 streaming 过程中按 Ctrl+R 触发 webview reload。
4. 等待页面重渲染完成。

## Expected vs Actual

- **Expected**：按钮保持转圈停止态，因为后端 turn 仍在运行，长寿 GET /subscribe 流将在重连后接管事件，直到真实 `session.idle` 到达才清 streaming。
- **Actual**：按钮立即翻回 ArrowUpIcon（发送态），用户被误导可以再次发送；实际后端 turn 仍在跑，并发发送可能造成 turn 错位。

## Environment

- Backend commit: 02f7701a（当前 develop HEAD）
- Frontend commit: 02f7701a
- OS / Browser: Tauri v2 webview / WebKitGTK & WebView2
- Data source: N/A（纯前端状态机问题）

## Evidence

- `client/src/services/channel/use-channel.ts:507-535`（`sendMessage`）和同文件 `:537-563`（`retryPendingUser`）的 `finally { useChatPartsStore.getState().setStreaming(sessionId, false) }` 是问题代码。
- 既有修复链：
  - [BUG-0037](BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) `fixed` — store 层同步写
  - [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) `fixed` — SSE 重放路径
- 后端事实（`server/data-talk-infrastructure/.../ChannelController.java:177`）：Ctrl+R 触发 onDisconnect → `clientGone=true`，POST 线程**不会**补发 `SessionStatus("idle")`，所以这条 bug 不是后端重放问题。
- 探索期产物：[openspec/changes/fix-composer-button-on-refresh-during-stream/design.md](../../openspec/changes/fix-composer-button-on-refresh-during-stream/design.md) "真实根因" 章节给出完整时序图。

## Root Cause

`useChannel.sendMessage` 把"POST 请求生命周期"与"AI turn 生命周期"错误等同。Ctrl+R 抹掉 POST SSE 连接时，`consumeSseStream` 抛 AbortError → sendMessage 的 `await` reject → `finally { setStreaming(false) }` 同步写 `streamingBySession=[]` 进 sessionStorage。页面 unload 后 reload 时，Zustand persist hydrate 出 `streamingBySession=∅`，composer 渲染发送按钮。

BUG-0037 的同步写修复（`useChatPartsStore.setStreaming` 内部主动写 sessionStorage）反而让这条路径从异步队列里的偶发抢跑变成必然落盘。

`retryPendingUser` 拥有相同的 finally 结构、相同的 bug。

## Fix

详见 OpenSpec change [`fix-composer-button-on-refresh-during-stream`](../../openspec/changes/fix-composer-button-on-refresh-during-stream/)。核心改动 `client/src/services/channel/use-channel.ts`：

1. `sendMessage` 与 `retryPendingUser` 移除 `finally { setStreaming(sessionId, false) }`。
2. 用 `connected` SSE 帧作为"SSE 流已打开"的边界信号，在 sendMessage 的局部闭包内维护 `streamOpened` 标志。
3. 仅当 `!streamOpened`（pre-stream 失败：5xx、网络错、TLS）时在 catch 块内显式调用 `setStreaming(sessionId, false)`，覆盖网络层错误下按钮卡住停止态的边界。
4. SSE 流打开后的失败（包含 Ctrl+R navigation abort）一律不清 streaming——长寿 GET sink 会在后续 `session.idle` 时清理。

不修改后端 SSE / SessionBus / ChannelController 协议；不引入新依赖；不修改 UI 视觉 / token。

## Verification

- vitest 套件 `BUG-0046 stream-lifecycle vs request-lifecycle` 新增三个核心用例：
  - `sendMessage clears streaming when POST fails before connected frame`（pre-stream 失败路径）
  - `sendMessage preserves streaming when POST fails after connected frame`（Ctrl+R 模拟）
  - `sendMessage clears streaming via session.idle on natural completion`（正常 turn 完成）
- `retryPendingUser` 镜像两个核心用例。
- `cd client && npx tsc --noEmit` 0 错误；`cd client && npx vitest run src/services/channel src/stores` 全绿；`cd client && npx eslint src/services/channel/use-channel.ts` 0 错误。
- BUG-0037 / BUG-0038 既有 vitest 套件保持通过，无需修改。
- 手工 Tauri 验证：streaming 中 Ctrl+R 后按钮保持 Loader2Icon，直到 AI 真实完成 turn。截图存 `tmp/bug-0046-verify/`。

## Notes

- BUG-0037 / BUG-0038 / BUG-0046 三者关系：同症状不同根因，每次修复都消除一条独立失败路径。BUG-0037 修 store 持久化时机，BUG-0038 修 SSE 重放窗口，BUG-0046 修请求生命周期与 turn 生命周期的语义错位。
- 长寿 GET sink 在 Ctrl+R 后通过 `useSessionSubscribe` 重新挂载并以 `Last-Event-ID` 续接，正常情况下能拿到后续 idle 事件清 streaming。该路径**已存在**，本 BUG 修复依赖该兜底。
- 极端边界：`fetch` 在 `connected` 帧之前就被 Ctrl+R abort（极快刷新），catch 落入 `!streamOpened` 分支清 streaming——但此时后端订阅可能都没成功，turn 是否真启动不确定，按钮回发送态是合理输出。
