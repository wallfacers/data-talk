---
id: BUG-0046
title: CTRL+R 刷新 streaming 中 → composer 按钮回退到"待发送"（首次刷新场景）
status: fixed
priority: P1
source: manual-report
modules: [session, chat, channel]
discovered: 2026-05-15
discoveredBy: human
testRunId: null
fixCommit: 2bc199f1
fixPlanRef: openspec/changes/fix-composer-button-via-server-status
duplicateOf: null
regression: true
---

## Summary

BUG-0037 / BUG-0038 修复后，二次刷新场景已稳定（CTRL+R 后 `streamingBySession` 同步持久化 + 500ms 重放抑制窗口生效）。但用户复现仍 100% 出现一种残留：首次发起一个 turn 后立刻 CTRL+R 刷新（AI 还在 streaming），composer 按钮短暂显示停止态后即翻回"发送"（ArrowUp）箭头。

## Reproduction Steps

1. 打开一个 fresh session（或刚发完一轮 idle 的 session）。
2. 输入提示发送，AI 开始 streaming（按钮变 Loader2 停止态）。
3. 在前 1-2 秒内按 CTRL+R。
4. 页面重渲染。立即观察按钮形态。

## Expected vs Actual

- **Expected**：后端 OpenCode 进程仍在跑这轮 turn，按钮应保持停止态直到真正 `session.idle` 到达。
- **Actual**：按钮显示发送态。后端 AI 仍在生成，但前端 UI 状态机和服务端不一致。

## Evidence

- 前端 `useChannel.sendMessage` 的 `finally { setStreaming(false) }` 在 CTRL+R 触发 fetch abort 时同步执行，把内存 streaming 标志清成 false 并写 sessionStorage（BUG-0037 同步写）。重新加载后 rehydrate 出 false，按钮初渲染就是发送态。
- BUG-0038 的 500ms 抑制窗口只保护 GET /subscribe 重放 idle 帧，不保护 finally race。
- OpenCode 服务端有真实状态：`GET /session/status` 返回 `Record<sid, {type: "idle"|"busy"|"retry"}>`（参见 `/home/wallfacers/project/opencode/packages/opencode/src/session/status.ts`、`/home/wallfacers/project/opencode/packages/opencode/src/server/routes/session.ts:74-96`），DataTalk 后端目前未透传。

## Root Cause

混合根因：
1. 前端 `sendMessage` 的 `finally` 在 abort 时把 streaming 抹平，但「请求生命周期」≠「OpenCode turn 生命周期」。
2. 之前 BUG-0037 / BUG-0038 都用「前端持久化 + 重放抑制」的纯前端方案，缺乏权威源；任意一处写入错误都会污染持久化。
3. 之前尝试过 `streamOpened` 守卫纯前端 fix（commit 2c3e3de9）→ 副作用：streaming 标志因无服务端裁定而永久卡 true，`useSessionHistory.shouldSkipReplace` 阻塞历史加载（回归原因，commit e172edcb / 155706c8 已 revert 整体）。

## Fix

换路径：以 OpenCode 服务端 `SessionStatus` 为单一权威源。

- **后端**：`OpenCodeHttpClient.getSessionStatuses()` 调用 OpenCode `/session/status`；新增 `GET /api/sessions/{sessionId}/status` 经 `OpenCodeSessionMap.openCodeFor()` 转换为返回 `{type: "idle"|"busy"|"retry"}`（idle 兜底）。
- **前端**：移除 `streamingBySession` 的 Zustand persist 与 `setStreaming` 的同步 sessionStorage 写。改为 mount 期间 fetch history 完成后调用新增的 `fetchSessionStatus(sessionId)`，busy / retry 时 `setStreaming(sessionId, true)`，idle 时不写（默认就是 false）。
- 保留 `buildEventSink` 的 L1 event-id 去重和 BUG-0038 500ms 抑制窗口作为防御层，但不再依赖持久化作为状态源。

## Verification

- 后端：`OpenCodeHttpClientTest` 新增 4 个用例（busy 透传 / 空 map / 5xx fail-open / 连接断开 fail-open）；`SessionStatusControllerTest` 6 个用例（busy / retry / 未映射 idle / map 无条目 idle / 未知 type idle / OpenCode 异常 idle）。全部通过：`mvn -pl data-talk-infrastructure test` 23 测试 0 失败。
- 前端：`session-status.test.ts` 7 个用例（busy / retry / idle / 网络错误 / HTTP 500 / 未知 type / 空 sessionId）；`chat-parts-store.test.ts` 改写 3 个用例验证不再持久化 streamingBySession（写入 / reload 不复原）。配合 `use-channel.test.ts` 33 个 BUG-0037/0038 测试维持原状全绿。`npx vitest run src/services/channel src/stores src/features/session` — 189 测试 0 失败。
- 端到端语义：mount 时 `useSessionSubscribe` 先调 `GET /api/sessions/{id}/status`，busy/retry 时 `setStreaming(true)`，按钮维持停止态；idle 时不写 streamingBySession，按钮显示发送；OpenCode 不可达时 fail-open 返回 idle。`shouldSkipReplace` 在 mount 阶段读到空 streamingBySession，history replace 正常执行（覆盖之前 2c3e3de9 commit revert 的回归路径）。

## Notes

- 回归判定：commit 2c3e3de9（之前的尝试）虽然解决了 BUG-0046 症状，但破坏了 [[BUG-0044]] 之类的历史加载语义，被 revert。本次方案通过引入服务端权威源彻底解耦「streaming 真值」和「history 加载守卫」。
- 同时考虑过：在 ChannelController.subscribe 启动时立刻 publish 一个真实的 SessionStatus 事件，省掉 HTTP 调用 —— 设计上更紧凑但偏离了 OpenCode 协议清晰度，留作 follow-up。
